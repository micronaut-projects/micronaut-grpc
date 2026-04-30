/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.grpc.server;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.inprocess.InProcessServerBuilder;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.TaskExecutors;
import org.jspecify.annotations.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import io.micronaut.grpc.server.health.HealthStatusManagerContainer;

/**
 * Builds an in-process gRPC server when explicitly enabled.
 *
 * @since 5.0.0
 */
@Factory
@Requires(property = GrpcServerConfiguration.IN_PROCESS_NAME)
@Internal
public class InProcessGrpcServerBuilder extends GrpcServerBuilder {

    private final ExecutorService executorService;
    private final GrpcInProcessServerConfiguration inProcessConfiguration;

    /**
     * @param healthStatusManagerContainer if enabled, inject a gRPC health status manager
     * @param executorService The IO executor service
     * @param inProcessConfiguration The in-process server configuration
     */
    @Inject
    public InProcessGrpcServerBuilder(@Nullable HealthStatusManagerContainer healthStatusManagerContainer,
                                      @Named(TaskExecutors.IO) ExecutorService executorService,
                                      GrpcInProcessServerConfiguration inProcessConfiguration) {
        super(healthStatusManagerContainer);
        this.executorService = executorService;
        this.inProcessConfiguration = inProcessConfiguration;
    }

    @Bean
    @Singleton
    @Replaces(value = ServerBuilder.class, factory = GrpcServerBuilder.class)
    @Override
    protected ServerBuilder<?> serverBuilder(GrpcServerConfiguration configuration,
                                             @Nullable List<BindableService> serviceList,
                                             @Nullable List<ServerInterceptor> interceptors,
                                             @Nullable List<ServerTransportFilter> serverTransportFilters,
                                             @Nullable List<ServerServiceDefinition> serverServiceDefinitions) {
        if (configuration.isSecure()) {
            throw new ConfigurationException("SSL is not supported for in-process gRPC servers");
        }
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName(
            inProcessConfiguration.getInProcessName().orElseThrow(() ->
                new ConfigurationException("grpc.server.in-process-name must not be empty"))
        ).executor(executorService);
        if (configuration.getMaxInboundMessageSize() != null) {
            serverBuilder.maxInboundMessageSize(configuration.getMaxInboundMessageSize());
        }
        if (configuration.getMaxInboundMetadataSize() != null) {
            serverBuilder.maxInboundMetadataSize(configuration.getMaxInboundMetadataSize());
        }
        return configureServerBuilder(
            serverBuilder,
            serviceList,
            interceptors,
            serverTransportFilters,
            serverServiceDefinitions
        );
    }
}
