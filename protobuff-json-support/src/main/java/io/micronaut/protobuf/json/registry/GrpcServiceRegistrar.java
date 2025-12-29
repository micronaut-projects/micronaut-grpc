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

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The {@code GrpcServiceRegistrar} is responsible for processing methods annotated with
 * {@code @GrpcRestJsonExposed} during application startup and registering them with the
 * {@link GrpcServiceRegistry}. This enables gRPC methods to be invoked via JSON over REST.
 * <br/>
 * This class implements the {@link ExecutableMethodProcessor} interface for the
 * {@code @GrpcRestJsonExposed} annotation and ensures that the annotated methods are
 * identified and registered.
 * <br/>
 * The registrar operates at startup to facilitate the integration of gRPC services
 * with REST-based clients by leveraging JSON payloads.
 * <br/>
 * It uses the {@link GrpcServiceRegistry} to maintain a mapping of registered methods.
 * <br/>
 * Annotations:
 * - {@code @Singleton}: Indicates that this class is a singleton within the application context.
 * - {@code @Experimental}: Denotes that the class features experimental functionality
 *   and may be prone to modifications in future versions.
 * <br/>
 * Constructor:
 * - Requires a {@code GrpcServiceRegistry} instance for method registration.
 * <br/>
 * Logging:
 * - Maintains an internal logger to track the registration process of gRPC methods.
 */
@Singleton
@Experimental
public class GrpcServiceRegistrar implements ExecutableMethodProcessor<GrpcRestJsonExposed> {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServiceRegistrar.class);
    private final GrpcServiceRegistry registry;

    public GrpcServiceRegistrar(GrpcServiceRegistry registry) {
        this.registry = checkNotNull(registry, "GrpcServiceRegistry cannot be null");
    }

    /**
     * Processes a bean definition and its executable method annotated with {@code @GrpcRestJsonExposed}.
     * Registers the annotated gRPC method with the {@link GrpcServiceRegistry}, enabling the method
     * to be invoked via JSON over REST.
     *
     * @param beanDefinition the bean definition containing the gRPC service class where the method is defined
     * @param method the executable method annotated with {@code @GrpcRestJsonExposed} to be registered
     * @param <B> type parameter annotation
     */
    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        LOG.info("Registering gRPC JSON-exposed method '{}' from bean '{}'",
            method.getMethodName(), beanDefinition.getBeanType().getSimpleName());
        registry.register(beanDefinition.getBeanType(), method.getMethodName(), method);
    }
}
