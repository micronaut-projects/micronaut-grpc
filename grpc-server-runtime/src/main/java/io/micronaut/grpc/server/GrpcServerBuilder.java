/*
 * Copyright 2017-2022 original authors
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

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.protobuf.services.HealthStatusManager;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.grpc.server.health.HealthStatusManagerContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Constructs the {@link ServerBuilder} instance. Here to allow extensions via a {@link io.micronaut.context.event.BeanCreatedEventListener} for {@link ServerBuilder}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcServerBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerBuilder.class);
    @Nullable
    private final HealthStatusManagerContainer healthStatusManagerContainer;

    /**
     * Constructs the {@link ServerBuilder} instance.
     *
     * @param healthStatusManagerContainer if enabled, inject a GRPC health status manager.
     */
    @Inject
    public GrpcServerBuilder(@Nullable HealthStatusManagerContainer healthStatusManagerContainer) {
        this.healthStatusManagerContainer = healthStatusManagerContainer;
    }

    /**
     * The server builder instance.
     *
     * @param configuration The configuration
     * @param serviceList The bindable services
     * @param interceptors The server interceptors
     * @param serverTransportFilters The server transport filters
     * @param serverServiceDefinitions The server service definitions
     *
     * @return The builder
     */
    @Bean
    @Singleton
    protected ServerBuilder<?> serverBuilder(GrpcServerConfiguration configuration,
                                             @Nullable List<BindableService> serviceList,
                                             @Nullable List<ServerInterceptor> interceptors,
                                             @Nullable List<ServerTransportFilter> serverTransportFilters,
                                             @Nullable List<ServerServiceDefinition> serverServiceDefinitions) {
        final ServerBuilder<?> serverBuilder = configuration.getServerBuilder();
        if (healthStatusManagerContainer != null) {
            HealthStatusManager healthStatusManager = healthStatusManagerContainer.getHealthStatusManager();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding health status manager {}", healthStatusManager);
            }
            serverBuilder.addService(healthStatusManager.getHealthService());
        }
        if (CollectionUtils.isNotEmpty(serviceList)) {
            for (BindableService serviceBean : serviceList) {
                serverBuilder.addService(serviceBean);
            }
        }

        if (CollectionUtils.isNotEmpty(interceptors)) {
            OrderUtil.reverseSort(interceptors);
            for (ServerInterceptor i : interceptors) {
                serverBuilder.intercept(i);
            }
        }

        if (CollectionUtils.isNotEmpty(serverTransportFilters)) {
            for (ServerTransportFilter i : serverTransportFilters) {
                serverBuilder.addTransportFilter(i);
            }
        }
        if (CollectionUtils.isNotEmpty(serverServiceDefinitions)) {
            serverBuilder.addServices(serverServiceDefinitions);
        }

        return serverBuilder;
    }
}
