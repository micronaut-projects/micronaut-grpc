package io.micronaut.grpc.server.security.jwt

import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micronaut.context.annotation.Property
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret", value = "SeCr3t")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.base64", value = "false")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm", value = "HS512")
class GrpcServerSecurityJwtInterceptorFactorySpec extends Specification {

    @Inject
    GrpcServerSecurityJwtInterceptor serverInterceptor

    def "serverInterceptor bean present"() {
        expect:
        serverInterceptor
    }

    def "serverInterceptor"() {
        given:
        GrpcServerSecurityJwtConfiguration config = new GrpcServerSecurityJwtConfiguration() {
            @Override
            boolean isEnabled() {
                return true
            }
            @Override
            int getInterceptorOrder() {
                return 0
            }
            @Override
            Collection<String> getInterceptMethodPatterns() {
                return null
            }
            @Override
            Status.Code getMissingTokenStatus() {
                return Status.UNAUTHENTICATED.code
            }
            @Override
            Status.Code getFailedValidationTokenStatus() {
                return Status.PERMISSION_DENIED.code
            }
            @Override
            String getMetadataKeyName() {
                return "JWT"
            }
        }
        GrpcServerSecurityJwtInterceptorFactory factory = new GrpcServerSecurityJwtInterceptorFactory()

        when:
        ServerInterceptor serverInterceptor = factory.serverInterceptor(config, [], [], [])

        then:
        serverInterceptor
        serverInterceptor instanceof GrpcServerSecurityJwtInterceptor
        ((GrpcServerSecurityJwtInterceptor) serverInterceptor).order == config.interceptorOrder
    }

}
