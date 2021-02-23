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
import io.micronaut.core.order.Ordered;
import io.micronaut.grpc.server.security.jwt.GrpcServerSecurityJwtConfiguration;
import io.micronaut.security.token.jwt.validator.JwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


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

    /**
     * Create the interceptor based on the configuration.
     *
     * @param config the gRPC Security JWT configuration
     * @param validator the JWT validator
     */
    public GrpcServerSecurityJwtInterceptor(final GrpcServerSecurityJwtConfiguration config, final JwtValidator validator) {
        this.config = config;
        jwtMetadataKey = Metadata.Key.of(config.getMetadataKeyName(), Metadata.ASCII_STRING_MARSHALLER);
        jwtValidator = validator;
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
        if (!metadata.containsKey(jwtMetadataKey)) {
            final String message = String.format("%s key missing in gRPC metadata", jwtMetadataKey.name());
            LOG.error(message);
            throw Status.fromCode(config.getMissingTokenStatus()).withDescription(message).asRuntimeException();
        }
        final ServerCall.Listener<T> listener = next.startCall(call, metadata);
        final String jwt = metadata.get(jwtMetadataKey);
        if (LOG.isDebugEnabled()) {
            LOG.debug("JWT: {}", jwt);
        }
        final Optional<JWT> jwtOptional = jwtValidator.validate(jwt, null); // We don't have an HttpRequest to send in here (hence null)
        if (!jwtOptional.isPresent()) {
            final String message = "JWT validation failed";
            LOG.error(message);
            throw Status.fromCode(config.getFailedValidationTokenStatus()).withDescription(message).asRuntimeException();
        }
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<T>(listener) { };
    }

    /**
     * Get the metadata key.
     *
     * @return the metadata key
     */
    Metadata.Key<String> getMetadataKey() {
        return jwtMetadataKey;
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
