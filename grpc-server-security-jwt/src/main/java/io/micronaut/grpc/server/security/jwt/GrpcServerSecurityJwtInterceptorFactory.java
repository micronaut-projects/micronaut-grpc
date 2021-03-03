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

import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import io.micronaut.security.token.jwt.validator.JwtValidator;

import javax.inject.Singleton;
import java.util.Collection;

/**
 * Factory for creating instances of gRPC server security JWT interceptors.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Factory
@Requires(beans = GrpcServerSecurityJwtConfiguration.class)
public class GrpcServerSecurityJwtInterceptorFactory {

    /**
     * Constructs an instance of {@link GrpcServerSecurityJwtInterceptor} based on configuration.
     *
     * @param grpcServerSecurityJwtConfiguration the gRPC server security JWT configuration
     * @param signatureConfigurations the signature configurations
     * @param encryptionConfigurations the encryption configurations
     * @param genericJwtClaimsValidators the generic JWT claims validators
     * @param securityConfiguration the security configuration
     * @param rolesFinder the roles finder for comparing roles with required roles
     * @return the server interceptor bean
     */
    @Bean
    @Singleton
    public ServerInterceptor serverInterceptor(final GrpcServerSecurityJwtConfiguration grpcServerSecurityJwtConfiguration,
                                               final Collection<SignatureConfiguration> signatureConfigurations,
                                               final Collection<EncryptionConfiguration> encryptionConfigurations,
                                               final Collection<GenericJwtClaimsValidator> genericJwtClaimsValidators,
                                               final SecurityConfiguration securityConfiguration,
                                               final RolesFinder rolesFinder) {
        final JwtValidator jwtValidator = JwtValidator.builder()
                .withSignatures(signatureConfigurations)
                .withEncryptions(encryptionConfigurations)
                .withClaimValidators(genericJwtClaimsValidators)
                .build();
        return new GrpcServerSecurityJwtInterceptor(grpcServerSecurityJwtConfiguration, jwtValidator, rolesFinder, securityConfiguration);
    }

}
