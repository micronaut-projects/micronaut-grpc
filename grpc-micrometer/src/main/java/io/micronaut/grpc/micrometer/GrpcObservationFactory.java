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
package io.micronaut.grpc.micrometer;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.binder.grpc.GrpcClientObservationConvention;
import io.micrometer.core.instrument.binder.grpc.GrpcServerObservationConvention;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Registers Micrometer's gRPC observation interceptors as beans, so that micronaut-grpc installs them on the server and
 * on every channel. Each call becomes a {@code grpc.server} or {@code grpc.client} observation, which the observation
 * handlers turn into timers and, with Micrometer Tracing, spans.
 *
 * @since 5.3.0
 */
@Factory
@Internal
@Requires(classes = ObservationGrpcServerInterceptor.class)
@Requires(beans = ObservationRegistry.class)
public class GrpcObservationFactory {

    /**
     * Enables observations of gRPC server calls. Defaults to {@code true}.
     */
    public static final String SERVER_ENABLED = "micrometer.observation.grpc.server.enabled";

    /**
     * Enables observations of gRPC client calls. Defaults to {@code true}.
     */
    public static final String CLIENT_ENABLED = "micrometer.observation.grpc.client.enabled";

    /**
     * The order of the observation interceptors: ahead of interceptors with the default order, so that calls they
     * reject or fail are observed too.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;

    /**
     * Creates the server observation interceptor.
     *
     * @param registry The observation registry
     * @param convention A custom observation convention, if one is declared as a bean
     * @return The interceptor
     */
    @Singleton
    @Named("grpc-server-observation")
    @Requires(property = SERVER_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    protected ServerInterceptor grpcServerObservationInterceptor(ObservationRegistry registry,
                                                                 @Nullable GrpcServerObservationConvention convention) {
        ObservationGrpcServerInterceptor interceptor = new ObservationGrpcServerInterceptor(registry);
        if (convention != null) {
            interceptor.setCustomConvention(convention);
        }
        return new OrderedObservationServerInterceptor(interceptor);
    }

    /**
     * Creates the client observation interceptor.
     *
     * @param registry The observation registry
     * @param convention A custom observation convention, if one is declared as a bean
     * @return The interceptor
     */
    @Singleton
    @Named("grpc-client-observation")
    @Requires(property = CLIENT_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    protected ClientInterceptor grpcClientObservationInterceptor(ObservationRegistry registry,
                                                                 @Nullable GrpcClientObservationConvention convention) {
        ObservationGrpcClientInterceptor interceptor = new ObservationGrpcClientInterceptor(registry);
        if (convention != null) {
            interceptor.setCustomConvention(convention);
        }
        return new OrderedObservationClientInterceptor(interceptor);
    }
}
