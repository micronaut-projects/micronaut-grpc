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
package io.micronaut.grpc.server.security.jwt.interceptor;

import com.nimbusds.jwt.JWT;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.grpc.server.security.jwt.GrpcServerSecurityJwtConfiguration;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.security.token.jwt.generator.claims.JwtClaimsSetAdapter;
import io.micronaut.security.token.jwt.validator.JwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * gRPC Server Security JWT Interceptor.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
public class GrpcServerSecurityJwtInterceptor implements ServerInterceptor, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerSecurityJwtInterceptor.class);

    private final GrpcServerSecurityJwtConfiguration config;
    private final Metadata.Key<String> jwtMetadataKey;
    private final JwtValidator jwtValidator;
    private final List<InterceptUrlMapPattern> interceptMethodPatterns; // httpMethod is not used in this context
    private final boolean rejectRolesNotFound;
    private final RolesFinder rolesFinder;

    /**
     * Create the interceptor based on the configuration.
     *
     * @param config the gRPC Security JWT configuration
     * @param jwtValidator the JWT validator
     * @param rolesFinder the roles finder
     * @param securityConfiguration the security configuration
     */
    public GrpcServerSecurityJwtInterceptor(final GrpcServerSecurityJwtConfiguration config,
                                            final JwtValidator jwtValidator,
                                            final RolesFinder rolesFinder,
                                            final SecurityConfiguration securityConfiguration) {
        this.config = config;
        this.jwtMetadataKey = Metadata.Key.of(config.getMetadataKeyName(), Metadata.ASCII_STRING_MARSHALLER);
        this.jwtValidator = jwtValidator;
        this.interceptMethodPatterns = securityConfiguration.getInterceptUrlMap();
        this.rejectRolesNotFound = securityConfiguration.isRejectNotFound();
        this.rolesFinder = rolesFinder;
    }

    /**
     * Intercept the call to validate the JSON web token.  If the token is not present in the metadata, or
     * if the token is not valid, this method will deny the request with a {@link io.grpc.StatusRuntimeException}.
     *
     * @param call the server call
     * @param metadata the metadata
     * @param next the next processor in the interceptor chain
     * @param <T> the type of the server request
     * @param <S> the type of the server response
     * @throws io.grpc.StatusRuntimeException if token not present or invalid
     */
    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(final ServerCall<T, S> call, final Metadata metadata, final ServerCallHandler<T, S> next) {
        final List<String> requiredAccesses = getRequiredAccesses(call);
        if (CollectionUtils.isEmpty(interceptMethodPatterns) && !rejectRolesNotFound) {
            LOG.debug("JWT validation is skipped due to 'intercept-method-patterns' configuration being empty");
            return forward(call, metadata, next);
        } else if (requiredAccesses.isEmpty() && rejectRolesNotFound) {
            throw statusRuntimeException(config.getFailedValidationTokenStatus(), "JWT validation failed since no roles were found and 'reject-not-found' = true");
        } else if (requiredAccesses.isEmpty()) { // We don't need tp validate JWT
            LOG.debug("JWT validation is skipped due to no matching 'intercept-method-patterns'");
            return forward(call, metadata, next);
        } else if (requiredAccesses.contains(SecurityRule.DENY_ALL)) {
            throw statusRuntimeException(config.getFailedValidationTokenStatus(), "JWT validation failed since denyAll() requirement met");
        } else if (requiredAccesses.contains(SecurityRule.IS_ANONYMOUS)) { // We don't need tp validate JWT
            LOG.debug("JWT validation is skipped since isAnonymous() requirement met");
            return forward(call, metadata, next);
        }
        if (!metadata.containsKey(jwtMetadataKey)) {
            throw statusRuntimeException(config.getMissingTokenStatus(), "JWT validation failed since no JWT was found in metadata");
        }
        final String token = metadata.get(jwtMetadataKey);
        LOG.debug("JWT: {}", token);
        final Optional<JWT> jwtOptional = jwtValidator.validate(token, null); // We don't have an HttpRequest to send in here (hence null)
        if (jwtOptional.isPresent()) {
            if (requiredAccesses.contains(SecurityRule.IS_AUTHENTICATED)) { // Valid JWT is enough here
                LOG.debug("JWT validation succeeded since isAuthenticated() requirement met");
                return forward(call, metadata, next);
            }
            return validateRoles(requiredAccesses, jwtOptional.get(), call, metadata, next);
        }
        throw statusRuntimeException(config.getFailedValidationTokenStatus(), "JWT validation failed since no JWT was returned by validator");
    }

    /**
     * Validate the JWT claims.
     *
     * @param requiredAccesses the list of required accesses
     * @param jwt the JWT
     * @param call the server call
     * @param metadata the metadata
     * @param next the next handler
     * @param <T> the type of the request
     * @param <S> the type of the response
     * @return the server call listener
     */
    private <T, S> ServerCall.Listener<T> validateRoles(final List<String> requiredAccesses, final JWT jwt,
                                                        final ServerCall<T, S> call, final Metadata metadata, final ServerCallHandler<T, S> next) {
        final List<String> roles;
        try {
            roles = rolesFinder.findInClaims(new JwtClaimsSetAdapter(jwt.getJWTClaimsSet()));
        } catch (final ParseException e) {
            throw statusRuntimeException(config.getFailedValidationTokenStatus(), "JWT validation failed due to parsing exception");
        }
        if (rolesFinder.hasAnyRequiredRoles(requiredAccesses, roles)) {
            LOG.debug("JWT validation succeeded with matching roles");
            return forward(call, metadata, next);
        } else {
            throw statusRuntimeException(config.getFailedValidationTokenStatus(), "JWT does not contain required roles");
        }
    }

    /**
     * Create a {@link StatusRuntimeException} for the given status code and message and log an error.
     *
     * @param statusCode the status code
     * @param message the message to log an error with
     * @return the status runtime exception
     */
    private static StatusRuntimeException statusRuntimeException(final Status.Code statusCode, final String message) {
        LOG.error(message);
        return Status.fromCode(statusCode).withDescription("JWT validation failed").asRuntimeException();
    }

    /**
     * Get the required access for the server call.
     *
     * @param serverCall the server call
     * @param <T> the request type
     * @param <S> the response type
     * @return the required access
     */
    private <T, S> List<String> getRequiredAccesses(final ServerCall<T, S> serverCall) {
        if (CollectionUtils.isEmpty(interceptMethodPatterns)) {
            return Collections.emptyList();
        }
        return interceptMethodPatterns.stream()
                .filter(interceptMethodPattern -> serverCall.getMethodDescriptor().getFullMethodName().matches(interceptMethodPattern.getPattern()))
                .map(InterceptUrlMapPattern::getAccess)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
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

    /**
     * Get the order for this interceptor within the interceptor chain.
     *
     * @return the order
     */
    @Override
    public int getOrder() {
        return config.getInterceptorOrder();
    }

}
