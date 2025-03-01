/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.protobuf.json;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * The GrpcServiceRegistrar initializes and registers gRPC service beans with the
 * {@link GrpcServiceRegistry}. It scans the provided application {@link BeanContext}
 * for beans annotated with {@link GrpcRestJsonExposed} and registers them in the
 * gRPC service registry. This enables the gRPC services to be exposed via JSON.
 *<br/>
 * This class identifies and processes gRPC services without directly instantiating them
 * unless necessary, ensuring lazy initialization where applicable. The process involves:
 * - Scanning bean definitions in the context for the {@link GrpcRestJsonExposed} annotation.
 * - Validating that the detected bean contains gRPC-compatible methods.
 * - Registering the service and its methods in a structured mapping within the {@link GrpcServiceRegistry}.
 *<br/>
 * Main Features:
 * - Scans and processes services annotated with {@link GrpcRestJsonExposed}.
 * - Identifies and validates gRPC methods using method signature checks.
 * - Logs detailed information about the registration process and errors encountered.
 *<br/>
 * Responsibilities:
 * - Efficiently map and register supported gRPC services with minimal resource usage.
 * - Log warnings for services with no identifiable gRPC methods.
 * - Gracefully handle failures in bean resolution or registration.
 */
@Singleton
@Experimental
public class GrpcServiceRegistrar {
    private static final Logger LOG = getLogger(GrpcServiceRegistrar.class);
    private final GrpcServiceRegistry registry;
    private final BeanContext context;

    /**
     * Constructs a new GrpcServiceRegistrar that scans the provided {@link BeanContext}
     * for gRPC service beans annotated with {@link GrpcRestJsonExposed} and registers
     * them with the provided {@link GrpcServiceRegistry}.
     *
     * @param context  The {@link BeanContext} used to discover and manage bean definitions. Must not be null.
     * @param registry The {@link GrpcServiceRegistry} used to register discovered gRPC services. Must not be null.
     */
    @SuppressWarnings("MnInjectionPoints")
    public GrpcServiceRegistrar(BeanContext context, GrpcServiceRegistry registry) {
        this.context = checkNotNull(context);
        this.registry = checkNotNull(registry);
    }

    /**
     * Scans the application context for gRPC service beans annotated with
     * {@link GrpcRestJsonExposed} and registers them with the provided gRPC
     * service registry. This method identifies gRPC-related beans based on
     * specific annotations and ensures that each identified service is properly
     * registered.
     *<br/>
     * If a bean has relevant gRPC-related annotations, it is processed and
     * registered as a JSON-compatible gRPC service. The method logs the
     * initialization process, iterates over all available bean definitions in
     * the context, and uses helper methods to validate and register gRPC
     * services.
     *<br/>
     * Any exceptions during the registration process are logged appropriately.
     */
    public void registerGrpcServices() {
        LOG.info("GrpcServiceRegistrar initializing.  Registering gRPC service beans tagged with " +
            "{}", GrpcRestJsonExposed.class.getSimpleName());
        for (BeanDefinition<?> beanDefinition : context.getBeanDefinitions(Object.class)) {
            // Check if the bean has @GrpcService or @GrpcRestJsonExposed annotations
            if (isGrpcRelatedService(beanDefinition)) {
                registerGrpcServiceAsJson(context, registry, beanDefinition);
            }
        }
    }

    /**
     * Registers a gRPC service as a JSON-compatible service with the provided gRPC service registry.
     * This method attempts to find a bean instance of the specified type, discovers gRPC methods
     * implemented by the bean, and registers the service if any gRPC methods are found. If no methods
     * are discovered, a warning is logged. Any exceptions encountered during the process are logged
     * as errors.
     *
     * @param context        The {@link BeanContext} used to resolve the bean instance. Must not be null.
     * @param registry       The {@link GrpcServiceRegistry} where the gRPC service will be registered. Must not be null.
     * @param beanDefinition The {@link BeanDefinition} representing the gRPC service bean type. Must not be null.
     */
    private void registerGrpcServiceAsJson(BeanContext context, GrpcServiceRegistry registry, BeanDefinition<?> beanDefinition) {
        // Attempt to resolve the bean only if necessary
        try {
            Object bean = context.findBean(beanDefinition.getBeanType()).orElse(null);
            if (bean != null) {
                String serviceName = bean.getClass().getSimpleName();
                Map<String, Method> methodMap = discoverGrpcMethods(bean);
                if (methodMap.isEmpty()) {
                    LOG.warn("No gRPC methods found for service: [{}]", serviceName);
                } else {
                    LOG.info("Registering gRPC service: [{}] with method map: [{}]",
                        serviceName, methodMapString(methodMap));
                    registry.registerService(serviceName, bean, methodMap);
                }
            }
        } catch (Exception e) {
            // Log an error or handle exceptions gracefully
            LOG.error("Failed to register gRPC service: [{}]", beanDefinition.getBeanType(), e);
        }
    }

    private boolean isGrpcRelatedService(BeanDefinition<?> beanDefinition) {
        // Check for @GrpcRestJsonExposed annotation without needing to instantiate
        return beanDefinition.getAnnotation(GrpcRestJsonExposed.class) != null;
    }

    private Map<String, Method> discoverGrpcMethods(Object serviceBean) {
        Map<String, Method> methods = new HashMap<>();
        for (Method method : serviceBean.getClass().getMethods()) {
            if (isGrpcMethod(method)) {
                methods.put(method.getName().toLowerCase(), method);
            }
        }
        return methods;
    }

    private boolean isGrpcMethod(Method method) {
        // Check if the method matches gRPC signature
        return method.getParameterCount() == 2 &&
                StreamObserver.class.isAssignableFrom(method.getParameterTypes()[1]);
    }

    private static String methodMapString(Map<String, Method> methodMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue().getName());
        }
        return sb.toString();
    }
}
