package io.micronaut.grpc.tracing;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.opentracing.contrib.grpc.ClientTracingInterceptor;
import io.opentracing.contrib.grpc.ServerTracingInterceptor;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Factory
public class GrpcTracingInterceptorFactory {

    @Requires(beans = GrpcServerTracingInterceptorConfiguration.class)
    @Singleton
    @Bean
    protected @Nonnull ServerInterceptor serverTracingInterceptor(@Nonnull GrpcServerTracingInterceptorConfiguration configuration) {
        return configuration.getBuilder().build();
    }

    @Requires(beans = GrpcClientTracingInterceptorConfiguration.class)
    @Singleton
    @Bean
    protected @Nonnull ClientInterceptor clientTracingInterceptor(@Nonnull GrpcClientTracingInterceptorConfiguration configuration) {
        return configuration.getBuilder().build();
    }
}
