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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * gRPC Server Security Interceptor responsible for checking {@link GrpcServerSecurityRule}
 * results prior to allowing access to gRPC server methods.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Singleton
@Requires(beans = GrpcServerSecurityConfiguration.class)
public class GrpcServerSecurityInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerSecurityInterceptor.class);
    private final SecurityConfiguration securityConfiguration;
    private final GrpcServerSecurityConfiguration grpcServerSecurityConfiguration;
    private final Collection<GrpcServerAuthenticationFetcher> grpcServerAuthenticationFetchers;
    private final Collection<GrpcServerSecurityRule> grpcServerSecurityRules;

    /**
     * Constructs an instance of the security interceptor with the provided configurations and rules.
     *  @param securityConfiguration the security configuration
     * @param grpcServerSecurityConfiguration the gRPC security configuration
     * @param grpcServerAuthenticationFetchers the gRPC server authentication fetchers
     * @param grpcServerSecurityRules the method security rules
     */
    @Inject
    public GrpcServerSecurityInterceptor(final SecurityConfiguration securityConfiguration,
                                         final GrpcServerSecurityConfiguration grpcServerSecurityConfiguration,
                                         final Collection<GrpcServerAuthenticationFetcher> grpcServerAuthenticationFetchers,
                                         final Collection<GrpcServerSecurityRule> grpcServerSecurityRules) {
        this.securityConfiguration = securityConfiguration;
        this.grpcServerSecurityConfiguration = grpcServerSecurityConfiguration;
        this.grpcServerAuthenticationFetchers = grpcServerAuthenticationFetchers;
        this.grpcServerSecurityRules = grpcServerSecurityRules;
    }

    /**
     * Intercept the gRPC server call and check security rules.  If no {@link Authentication} is found, the server call
     * is forwarded onto the next server call handler.
     *
     * @param serverCall the server call
     * @param metadata the metadata
     * @param next the next server call handler
     * @param <T> the type of the request
     * @param <S> the type of the response
     * @return the server call listener
     */
    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(final ServerCall<T, S> serverCall, final Metadata metadata, final ServerCallHandler<T, S> next) {
        final String fullMethodName = serverCall.getMethodDescriptor().getFullMethodName();
        final Optional<Optional<Authentication>> authentication = grpcServerAuthenticationFetchers.stream()
                .map(grpcServerAuthenticationFetcher -> grpcServerAuthenticationFetcher.fetchAuthentication(serverCall, metadata))
                .findFirst();
        if (authentication.isPresent()) {
            return checkRules(serverCall, metadata, next, fullMethodName, authentication.get().orElse(null));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No Authentication fetched for server call. {}.", fullMethodName);
        }
        return forward(serverCall, metadata, next);
    }

    /**
     * Check the rules to see if the server call should be allowed or rejected.
     *
     * @param serverCall the server call
     * @param metadata the metadata
     * @param next the next server call handler
     * @param fullMethodName the full method name
     * @param authentication the authentication (may be null)
     * @param <T> the type of the server call request
     * @param <S> the type of the server call response
     * @return the server call listener
     */
    private <T, S> ServerCall.Listener<T> checkRules(final ServerCall<T, S> serverCall, final Metadata metadata, final ServerCallHandler<T, S> next,
                                                     final String fullMethodName, @Nullable final Authentication authentication) {
        final boolean forbidden = authentication != null;
        final Optional<ServerCall.Listener<T>> listenerOptional = grpcServerSecurityRules.stream()
                .map(grpcServerSecurityRule -> checkRule(serverCall, metadata, next, authentication, fullMethodName, forbidden, grpcServerSecurityRule))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (listenerOptional.isPresent()) {
            return listenerOptional.get();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Authorized server call to {}. No rule provider authorized or rejected the call.", fullMethodName);
        }
        if (securityConfiguration.isRejectNotFound() && forbidden) {
            throw Status.fromCode(grpcServerSecurityConfiguration.getFailedAuthorizationStatus()).asRuntimeException();
        } else if (securityConfiguration.isRejectNotFound()) {
            throw Status.fromCode(grpcServerSecurityConfiguration.getMissingAuthenticationStatus()).asRuntimeException();
        }
        return forward(serverCall, metadata, next);
    }

    /**
     * Check the rules to see if the server call should be allowed or rejected.
     *
     * @param serverCall the server call
     * @param metadata the metadata
     * @param next the next server call handler
     * @param authentication the authentication
     * @param fullMethodName the full method name
     * @param forbidden true if there is no authentication
     * @param grpcServerSecurityRule the method security rule to check
     * @param <T> the type of the server call request
     * @param <S> the type of the server call response
     * @return the server call listener
     */
    private <T, S> Optional<ServerCall.Listener<T>> checkRule(final ServerCall<T, S> serverCall, final Metadata metadata, final ServerCallHandler<T, S> next,
                                                              @Nullable final Authentication authentication, final String fullMethodName,
                                                              final boolean forbidden, final GrpcServerSecurityRule grpcServerSecurityRule) {
        final Map<String, Object> claims = Optional.ofNullable(authentication)
                .map(Authentication::getAttributes)
                .orElse(null);
        final SecurityRuleResult result = grpcServerSecurityRule.check(serverCall, metadata, claims);
        if (result == SecurityRuleResult.REJECTED) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unauthorized server call to {}. The rule provider {} rejected the call.", fullMethodName, grpcServerSecurityRule.getClass().getName());
            }
            if (forbidden) {
                throw Status.fromCode(grpcServerSecurityConfiguration.getFailedAuthorizationStatus()).asRuntimeException();
            }
            throw Status.fromCode(grpcServerSecurityConfiguration.getMissingAuthenticationStatus()).asRuntimeException();
        }
        if (result == SecurityRuleResult.ALLOWED) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Authorized server call to {}. The rule provider {} authorized the request.", fullMethodName, grpcServerSecurityRule.getClass().getName());
            }
            return Optional.of(forward(serverCall, metadata, next));
        }
        return Optional.empty();
    }

    /**
     * Forward the call to next handler.
     *
     * @param call the server call
     * @param metadata the metadata
     * @param next the next handler
     * @param <T> the type of the request
     * @param <S> the type of the response
     * @return the server call listener
     */
    private static <T, S> ServerCall.Listener<T> forward(final ServerCall<T, S> call, final Metadata metadata, final ServerCallHandler<T, S> next) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<T>(next.startCall(call, metadata)) { };
    }

}
