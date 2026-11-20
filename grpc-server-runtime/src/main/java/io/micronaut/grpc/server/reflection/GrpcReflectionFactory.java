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
package io.micronaut.grpc.server.reflection;

import io.grpc.BindableService;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Registers the gRPC server reflection service ({@code grpc.reflection.v1.ServerReflection}) so that
 * tools such as {@code grpcurl}, Postman or {@code grpcui} can discover the services exposed by the server.
 *
 * <p>Applications enable it through configuration and don't use this class directly.</p>
 *
 * <p>Reflection is disabled by default because it exposes the full API surface. Enable it with
 * {@code grpc.server.reflection.enabled=true}. The {@code io.grpc:grpc-services} dependency must be on the
 * classpath.</p>
 *
 * @since 5.3.0
 */
@Factory
@Internal
@Requires(classes = ProtoReflectionServiceV1.class)
public class GrpcReflectionFactory {

    /**
     * The configuration property that enables the reflection service.
     */
    public static final String REFLECTION_ENABLED = "grpc.server.reflection.enabled";

    /**
     * The name of the reflection service bean.
     */
    public static final String REFLECTION_SERVICE_NAME = "grpc-reflection";

    /**
     * Creates the reflection service. It is registered with the gRPC server like any other {@link BindableService} bean.
     *
     * @return The reflection service
     */
    @Singleton
    @Named(REFLECTION_SERVICE_NAME)
    @Requires(property = REFLECTION_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
    protected BindableService grpcReflectionService() {
        return ProtoReflectionServiceV1.newInstance();
    }
}
