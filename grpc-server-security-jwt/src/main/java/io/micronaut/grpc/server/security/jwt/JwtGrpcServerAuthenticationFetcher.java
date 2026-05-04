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
package io.micronaut.grpc.server.security.jwt;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.grpc.server.security.GrpcServerAuthenticationFetcher;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.validator.TokenValidator;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reads a JWT from gRPC metadata and authenticates it with Micronaut Security token validators.
 *
 * @since 5.0.0
 */
@Singleton
@Requires(classes = TokenValidator.class)
@Requires(beans = GrpcServerSecurityJwtConfiguration.class)
public final class JwtGrpcServerAuthenticationFetcher implements GrpcServerAuthenticationFetcher {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Metadata.Key<String> jwtMetadataKey;
    private final List<TokenValidator<?>> tokenValidators;

    /**
     * @param configuration The JWT gRPC server security configuration
     * @param tokenValidators The Micronaut token validators
     */
    public JwtGrpcServerAuthenticationFetcher(GrpcServerSecurityJwtConfiguration configuration,
                                              Collection<TokenValidator<?>> tokenValidators) {
        this.jwtMetadataKey = Metadata.Key.of(configuration.getMetadataKeyName(), Metadata.ASCII_STRING_MARSHALLER);
        this.tokenValidators = new ArrayList<>(tokenValidators);
        OrderUtil.sort(this.tokenValidators);
    }

    @Override
    public <T, S> org.reactivestreams.Publisher<Authentication> fetchAuthentication(ServerCall<T, S> serverCall, Metadata metadata) {
        String token = metadata.get(jwtMetadataKey);
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        String normalizedToken = token.startsWith(BEARER_PREFIX) ? token.substring(BEARER_PREFIX.length()) : token;
        return Flux.fromIterable(tokenValidators)
            .concatMap(tokenValidator -> Mono.from(tokenValidator.validateToken(normalizedToken, null)))
            .next()
            .switchIfEmpty(Mono.error(Status.PERMISSION_DENIED.withDescription("JWT validation failed").asRuntimeException()))
            .onErrorMap(throwable -> throwable instanceof RuntimeException runtimeException
                ? runtimeException
                : Status.PERMISSION_DENIED.withDescription("JWT validation failed").withCause(throwable).asRuntimeException());
    }
}
