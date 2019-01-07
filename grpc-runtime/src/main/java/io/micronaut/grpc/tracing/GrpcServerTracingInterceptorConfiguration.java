package io.micronaut.grpc.tracing;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.grpc.server.GrpcServerConfiguration;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ClientTracingInterceptor;
import io.opentracing.contrib.grpc.ServerCloseDecorator;
import io.opentracing.contrib.grpc.ServerSpanDecorator;
import io.opentracing.contrib.grpc.ServerTracingInterceptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Adds a {@link ServerTracingInterceptor} when OpenTracing for GRPC is on the classpath
 * and allows integration with Zipkin and Jaeger.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GrpcServerTracingInterceptorConfiguration.PREFIX)
public class GrpcServerTracingInterceptorConfiguration {

    public static final String PREFIX = GrpcServerConfiguration.PREFIX + ".tracing";

    @ConfigurationBuilder(prefixes = "with", allowZeroArgs = true)
    protected final ServerTracingInterceptor.Builder builder;

    protected GrpcServerTracingInterceptorConfiguration(Tracer tracer) {
        this.builder = new ServerTracingInterceptor.Builder(tracer);
    }

    /**
     * @return The {@link ServerTracingInterceptor.Builder}
     */
    public @Nonnull ServerTracingInterceptor.Builder getBuilder() {
        return builder;
    }

    /**
     * Decorates the server span with custom data.
     *
     * @param serverSpanDecorator used to decorate the server span
     */
    @Inject
    public void setServerSpanDecorator(@Nullable ServerSpanDecorator serverSpanDecorator) {
        if (serverSpanDecorator != null) {
            builder.withServerSpanDecorator(serverSpanDecorator);
        }
    }

    /**
     * Decorates the server span with custom data when the gRPC call is closed.
     *
     * @param serverCloseDecorator used to decorate the server span
     */
    @Inject
    public void setServerCloseDecorator(@Nullable ServerCloseDecorator serverCloseDecorator) {
        if (serverCloseDecorator != null) {
            builder.withServerCloseDecorator(serverCloseDecorator);
        }
    }
}
