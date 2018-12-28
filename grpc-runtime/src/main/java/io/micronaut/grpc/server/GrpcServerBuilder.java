package io.micronaut.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerTransportFilter;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.List;

/**
 * Constructs the {@link ServerBuilder} instance.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcServerBuilder {

    /**
     * The server builder instance.
     *
     * @param configuration The configuration
     * @param serviceList The bindable services
     * @return The builder
     */
    @Bean
    @Singleton
    protected ServerBuilder<?> serverBuilder(GrpcServerConfiguration configuration,
                                             @Nullable List<BindableService> serviceList,
                                             @Nullable List<ServerInterceptor> interceptors,
                                             @Nullable List<ServerTransportFilter> serverTransportFilters) {
        final ServerBuilder<?> serverBuilder = configuration.getServerBuilder();
        if (CollectionUtils.isNotEmpty(serviceList)) {
            for (BindableService serviceBean : serviceList) {
                serverBuilder.addService(serviceBean);
            }
        }

        if (CollectionUtils.isNotEmpty(interceptors)) {
            for (ServerInterceptor i : interceptors) {
                serverBuilder.intercept(i);
            }
        }

        if (CollectionUtils.isNotEmpty(serverTransportFilters)) {
            for (ServerTransportFilter i : serverTransportFilters) {
                serverBuilder.addTransportFilter(i);
            }
        }

        return serverBuilder;
    }
}
