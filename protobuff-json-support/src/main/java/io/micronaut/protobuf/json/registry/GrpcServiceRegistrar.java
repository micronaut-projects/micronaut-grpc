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
package io.micronaut.protobuf.json.registry;

import io.grpc.stub.AbstractStub;
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
 * The GrpcServiceRegistrar is responsible for scanning a {@link BeanContext} to identify
 * gRPC service beans annotated with {@link GrpcRestJsonExposed} and registering them with
 * the provided {@link GrpcServiceRegistry}. This class provides mechanisms to discover and
 * manage gRPC services in a modular application context.
 *<br/>
 * Annotations such as {@link GrpcRestJsonExposed} are used to identify beans suitable for
 * JSON compatibility and exposure over gRPC. The registrar manages the service lifecycle
 * by ensuring that discovered services are registered properly in the context of the
 * application's gRPC infrastructure.
 *<br/>
 * This class must be instantiated with a valid {@link BeanContext} and {@link GrpcServiceRegistry}.
 * The core functionality of this class includes identifying gRPC-related beans, determining
 * candidate methods, and safely registering services with appropriate logging for success
 * and failure scenarios.
 *<br/>
 * Note: This class is experimental and subject to potential changes in future versions.
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
        LOG.info("GrpcServiceRegistrar initializing. Registering gRPC service beans tagged with {}",
                GrpcRestJsonExposed.class.getSimpleName());
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
                // For client stubs, adjust the service name by trimming "Stub" (if present)
                // and appending a client identifier if available.
                if (bean instanceof AbstractStub<?>) {
                    if (serviceName.endsWith("Stub")) {
                        serviceName = serviceName.substring(0, serviceName.length() - 4);
                    }
                    try {
                        Method getClientIdMethod = bean.getClass().getMethod("getClientId");
                        if (String.class.equals(getClientIdMethod.getReturnType())) {
                            String clientId = (String) getClientIdMethod.invoke(bean);
                            if (clientId != null && !clientId.isBlank()) {
                                serviceName = serviceName + "-" + clientId;
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        // No getClientId method; continue using the base service name.
                    } catch (Exception e) {
                        LOG.error("Error retrieving clientId from bean: [{}]", bean.getClass().getName(), e);
                    }
                }
                Map<String, Method> methodMap = discoverGrpcMethods(bean);
                if (methodMap.isEmpty()) {
                    LOG.warn("No gRPC methods found for service: [{}]", serviceName);
                } else {
                    LOG.info("Registering gRPC service: [{}] with method map: [{}]", serviceName, methodMapString(methodMap));
                    registry.registerService(serviceName, bean, methodMap);
                }
            }
        } catch (Exception e) {
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
                methods.put(method.getName(), method);
            }
        }
        return methods;
    }

    private boolean isGrpcMethod(Method method) {
        // A valid gRPC method should have two parameters with the second being a StreamObserver.
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
