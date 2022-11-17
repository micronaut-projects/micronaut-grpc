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

import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;

import jakarta.inject.Singleton;

/**
 * Factory that builds the Tracing interceptors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcServerTracingInterceptorFactory {

    /**
     * The server interceptor.
     *
     * @param configuration The configuration
     *
     * @return The server interceptor
     */
    @Requires(beans = GrpcServerTracingInterceptorConfiguration.class)
    @Singleton
    @Bean
    protected @NonNull ServerInterceptor serverTracingInterceptor(@NonNull GrpcServerTracingInterceptorConfiguration configuration) {
        return configuration.getBuilder().build();
    }
}
