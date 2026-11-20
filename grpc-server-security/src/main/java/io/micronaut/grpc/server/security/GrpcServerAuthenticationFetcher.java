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
package io.micronaut.grpc.server.security;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.security.authentication.Authentication;

/**
 * Reads an {@link Authentication} from a gRPC server call.
 *
 * @since 5.0.0
 */
public interface GrpcServerAuthenticationFetcher extends Ordered {

    /**
     * Attempts to read an {@link Authentication} from the current server call.
     *
     * @param serverCall The current gRPC call
     * @param metadata The gRPC metadata
     * @param <T> The request type
     * @param <S> The response type
     * @return The authentication when one is available
     */
    <T, S> @Nullable Authentication fetchAuthentication(ServerCall<T, S> serverCall, Metadata metadata);
}
