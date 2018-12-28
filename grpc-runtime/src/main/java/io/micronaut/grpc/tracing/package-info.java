@Configuration
@Requires(classes = {Tracer.class, ClientTracingInterceptor.class, io.opentracing.contrib.grpc.ServerTracingInterceptor.class})
@Requires(beans = Tracer.class)
package io.micronaut.grpc.tracing;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import io.opentracing.Tracer;
import io.opentracing.contrib.grpc.ClientTracingInterceptor;