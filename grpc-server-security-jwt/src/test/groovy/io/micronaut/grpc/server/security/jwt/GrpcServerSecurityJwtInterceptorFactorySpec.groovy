package io.micronaut.grpc.server.security.jwt

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.token.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret", value = "SeCr3t")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.base64", value = "false")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm", value = "HS512")
class GrpcServerSecurityJwtInterceptorFactorySpec extends Specification {

    @Inject
    ApplicationContext applicationContext

    def "serverInterceptor bean present"() {
        expect:
        applicationContext.getBean(GrpcServerSecurityJwtInterceptor)
    }

}
