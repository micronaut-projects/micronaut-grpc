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
package io.micronaut.grpc.server.security.jwt;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micronaut.grpc.server.security.GrpcServerAuthenticationFetcher;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.jwt.validator.JwtAuthenticationFactory;
import io.micronaut.security.token.jwt.validator.JwtValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Fetches {@link Authentication} from JWT present in the gRPC {@link Metadata}.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Singleton
public class JwtGrpcServerAuthenticationFetcher implements GrpcServerAuthenticationFetcher {

    private final Metadata.Key<String> jwtMetadataKey;
    private final JwtValidator jwtValidator;
    private final JwtAuthenticationFactory jwtAuthenticationFactory;

    /**
     * Constructs the authentication fetcher with the provided configuration and JWT validator.
     *
     * @param config the configuration
     * @param jwtValidator the JWT validator
     * @param jwtAuthenticationFactory the JWT authentication factory
     */
    @Inject
    public JwtGrpcServerAuthenticationFetcher(final GrpcServerSecurityJwtConfiguration config,
                                              final JwtValidator jwtValidator,
                                              final JwtAuthenticationFactory jwtAuthenticationFactory) {
        this.jwtMetadataKey = Metadata.Key.of(config.getMetadataKeyName(), Metadata.ASCII_STRING_MARSHALLER);
        this.jwtValidator = jwtValidator;
        this.jwtAuthenticationFactory = jwtAuthenticationFactory;
    }

    /**
     * Fetch the {@link Authentication} from JWT in metadata.
     *
     * @param serverCall {@link ServerCall} being executed.
     * @param metadata the metadata to retrieve JWT from
     * @param <T> the type of the server call request
     * @param <S> the type of the server call response
     * @return the authentication if found, otherwise {@link Optional#empty()}
     */
    @Override
    public <T, S> Optional<Authentication> fetchAuthentication(final ServerCall<T, S> serverCall, final Metadata metadata) {
        return Optional.of(jwtMetadataKey)
                .map(metadata::get)
                .flatMap(jwt -> jwtValidator.validate(jwt, null))
                .flatMap(jwtAuthenticationFactory::createAuthentication);
    }

}
