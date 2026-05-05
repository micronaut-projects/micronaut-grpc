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
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRuleResult;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts gRPC calls and applies Micronaut security rules.
 *
 * @since 5.0.0
 */
@Singleton
@Requires(beans = {
    GrpcServerSecurityConfiguration.class,
    GrpcServerAuthenticationFetcher.class,
    GrpcServerSecurityRule.class
})
public final class GrpcServerSecurityInterceptor implements ServerInterceptor, Ordered {

    private final SecurityConfiguration securityConfiguration;
    private final GrpcServerSecurityConfiguration grpcServerSecurityConfiguration;
    private final List<GrpcServerAuthenticationFetcher> grpcServerAuthenticationFetchers;
    private final List<GrpcServerSecurityRule> grpcServerSecurityRules;

    /**
     * @param securityConfiguration The Micronaut security configuration
     * @param grpcServerSecurityConfiguration The gRPC security configuration
     * @param grpcServerAuthenticationFetchers The gRPC authentication fetchers
     * @param grpcServerSecurityRules The gRPC security rules
     */
    public GrpcServerSecurityInterceptor(SecurityConfiguration securityConfiguration,
                                         GrpcServerSecurityConfiguration grpcServerSecurityConfiguration,
                                         Collection<GrpcServerAuthenticationFetcher> grpcServerAuthenticationFetchers,
                                         Collection<GrpcServerSecurityRule> grpcServerSecurityRules) {
        this.securityConfiguration = securityConfiguration;
        this.grpcServerSecurityConfiguration = grpcServerSecurityConfiguration;
        this.grpcServerAuthenticationFetchers = new ArrayList<>(grpcServerAuthenticationFetchers);
        this.grpcServerSecurityRules = new ArrayList<>(grpcServerSecurityRules);
        OrderUtil.sort(this.grpcServerAuthenticationFetchers);
        OrderUtil.sort(this.grpcServerSecurityRules);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(ServerCall<T, S> serverCall,
                                                       Metadata metadata,
                                                       ServerCallHandler<T, S> next) {
        Authentication authentication = fetchAuthentication(serverCall, metadata);
        return checkRules(serverCall, metadata, next, authentication);
    }

    private <T, S> @Nullable Authentication fetchAuthentication(ServerCall<T, S> serverCall, Metadata metadata) {
        return Flux.fromIterable(grpcServerAuthenticationFetchers)
            .concatMap(authenticationFetcher -> authenticationFetcher.fetchAuthentication(serverCall, metadata))
            .next()
            .blockOptional()
            .orElse(null);
    }

    private <T, S> ServerCall.Listener<T> checkRules(ServerCall<T, S> serverCall,
                                                     Metadata metadata,
                                                     ServerCallHandler<T, S> next,
                                                     @Nullable Authentication authentication) {
        boolean authenticated = authentication != null;
        Optional<SecurityRuleResult> result = Flux.fromIterable(grpcServerSecurityRules)
            .concatMap(rule -> Mono.from(rule.check(serverCall, metadata, authentication))
                .defaultIfEmpty(SecurityRuleResult.UNKNOWN)
                .filter(securityRuleResult -> securityRuleResult != SecurityRuleResult.UNKNOWN))
            .next()
            .blockOptional();

        if (result.isPresent()) {
            if (result.get() == SecurityRuleResult.ALLOWED) {
                return next.startCall(serverCall, metadata);
            }
            throw status(authenticated
                ? grpcServerSecurityConfiguration.getFailedAuthorizationStatus()
                : grpcServerSecurityConfiguration.getMissingAuthenticationStatus());
        }

        if (!securityConfiguration.isRejectNotFound()) {
            return next.startCall(serverCall, metadata);
        }

        throw status(authenticated
            ? grpcServerSecurityConfiguration.getFailedAuthorizationStatus()
            : grpcServerSecurityConfiguration.getMissingAuthenticationStatus());
    }

    private static RuntimeException status(Status.Code code) {
        return Status.fromCode(code).asRuntimeException();
    }
}
