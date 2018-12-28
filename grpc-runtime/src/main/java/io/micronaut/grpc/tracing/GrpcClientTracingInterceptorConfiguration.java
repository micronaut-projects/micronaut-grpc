package io.micronaut.grpc.tracing;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.grpc.channels.GrpcDefaultManagedChannelConfiguration;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

@ConfigurationProperties(GrpcClientTracingInterceptorConfiguration.PREFIX)
public class GrpcClientTracingInterceptorConfiguration {

    public static final String PREFIX = GrpcDefaultManagedChannelConfiguration.PREFIX + ".tracing";

    @ConfigurationBuilder(prefixes = "with", allowZeroArgs = true)
    protected final ClientTracingInterceptor.Builder builder;

    protected GrpcClientTracingInterceptorConfiguration(Tracer tracer) {
        this.builder = new ClientTracingInterceptor.Builder(tracer);
    }

    /**
     * @return The {@link ServerTracingInterceptor.Builder}
     */
    public @Nonnull ClientTracingInterceptor.Builder getBuilder() {
        return builder;
    }

    /**
     * Decorates the server span with custom data.
     *
     * @param clientSpanDecorator used to decorate the server span
     */
    @Inject
    public void setClientSpanDecorator(@Nullable ClientSpanDecorator clientSpanDecorator) {
        if (clientSpanDecorator != null) {
            builder.withClientSpanDecorator(clientSpanDecorator);
        }
    }

    /**
     * Decorates the server span with custom data when the gRPC call is closed.
     *
     * @param clientCloseDecorator used to decorate the server span
     */
    @Inject
    public void setClientCloseDecorator(@Nullable ClientCloseDecorator clientCloseDecorator) {
        if (clientCloseDecorator != null) {
            builder.withClientCloseDecorator(clientCloseDecorator);
        }
    }
}
