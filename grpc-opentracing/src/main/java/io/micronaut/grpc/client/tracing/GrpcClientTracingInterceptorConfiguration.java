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
package io.micronaut.grpc.client.tracing;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ClientCloseDecorator;
import io.opentracing.contrib.grpc.ClientSpanDecorator;
import io.opentracing.contrib.grpc.TracingClientInterceptor;
import jakarta.inject.Inject;

/**
 * Adds a {@link TracingClientInterceptor} when OpenTracing for GRPC is on the classpath
 * and allows integration with Zipkin and Jaeger.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GrpcClientTracingInterceptorConfiguration.PREFIX)
public class GrpcClientTracingInterceptorConfiguration {

    public static final String PREFIX = "grpc.client.tracing";

    @ConfigurationBuilder(prefixes = "with", allowZeroArgs = true)
    protected final TracingClientInterceptor.Builder builder;

    /**
     * Default constructor.
     * @param tracer The tracer
     */
    protected GrpcClientTracingInterceptorConfiguration(Tracer tracer) {
        this.builder = TracingClientInterceptor.newBuilder().withTracer(tracer);
    }

    /**
     * @return The {@link TracingClientInterceptor.Builder}
     */
    public @NonNull TracingClientInterceptor.Builder getBuilder() {
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
