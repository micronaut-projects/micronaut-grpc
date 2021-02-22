package io.micronaut.grpc.server.security.jwt;

import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import io.micronaut.security.token.jwt.validator.JwtValidator;

import javax.inject.Singleton;
import java.util.Collection;

/**
 * Factory for creating instances of gRPC server security JWT interceptors
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Factory
@Requires(beans = GrpcServerSecurityJwtConfiguration.class)
public class GrpcServerSecurityJwtInterceptorFactory {

    /**
     * Constructs an instance of {@link GrpcServerSecurityJwtInterceptor} based on configuration
     *
     * @param grpcServerSecurityJwtConfiguration the gRPC server security JWT configuration
     * @param signatureConfigurations the signature configurations
     * @param encryptionConfigurations the encryption configurations
     * @param genericJwtClaimsValidators the generic JWT claims validators
     * @return the server interceptor bean
     */
    @Bean
    @Singleton
    public ServerInterceptor serverInterceptor(final GrpcServerSecurityJwtConfiguration grpcServerSecurityJwtConfiguration,
                                               final Collection<SignatureConfiguration> signatureConfigurations,
                                               final Collection<EncryptionConfiguration> encryptionConfigurations,
                                               final Collection<GenericJwtClaimsValidator> genericJwtClaimsValidators) {
        final JwtValidator jwtValidator = JwtValidator.builder()
                .withSignatures(signatureConfigurations)
                .withEncryptions(encryptionConfigurations)
                .withClaimValidators(genericJwtClaimsValidators)
                .build();
        return new GrpcServerSecurityJwtInterceptor(grpcServerSecurityJwtConfiguration, jwtValidator);
    }

}
