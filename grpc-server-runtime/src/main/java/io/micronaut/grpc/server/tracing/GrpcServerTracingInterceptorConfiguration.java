/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.grpc.server.tracing;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.grpc.server.GrpcServerConfiguration;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ServerCloseDecorator;
import io.opentracing.contrib.grpc.ServerSpanDecorator;
import io.opentracing.contrib.grpc.TracingServerInterceptor;

import javax.inject.Inject;

/**
 * Adds a {@link TracingServerInterceptor} when OpenTracing for GRPC is on the classpath
 * and allows integration with Zipkin and Jaeger.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GrpcServerTracingInterceptorConfiguration.PREFIX)
public class GrpcServerTracingInterceptorConfiguration {

    public static final String PREFIX = GrpcServerConfiguration.PREFIX + ".tracing";

    @ConfigurationBuilder(prefixes = "with", allowZeroArgs = true)
    protected final TracingServerInterceptor.Builder builder;

    /**
     * Default constructor.
     * @param tracer The tracer
     */
    protected GrpcServerTracingInterceptorConfiguration(Tracer tracer) {
        this.builder = TracingServerInterceptor.newBuilder().withTracer(tracer);
    }

    /**
     * @return The {@link TracingServerInterceptor.Builder}
     */
    public @NonNull TracingServerInterceptor.Builder getBuilder() {
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
