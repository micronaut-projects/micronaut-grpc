/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.order.Ordered;
import io.micronaut.security.authentication.Authentication;

import java.util.Optional;

/**
 * gRPC Authentication Fetcher.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
public interface GrpcServerAuthenticationFetcher extends Ordered {

    /**
     * Attempts to read an {@link Authentication} from a {@link ServerCall} being executed.
     *
     * @param serverCall {@link ServerCall} being executed.
     * @param <S> request type
     * @param <T> response type
     * @param metadata the metadata
     * @return {@link Authentication} if found
     */
    <T, S> Optional<Authentication> fetchAuthentication(ServerCall<T, S> serverCall, Metadata metadata);

}
