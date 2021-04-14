package io.micronaut.grpc.server.security.jwt

import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micronaut.context.annotation.Property
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.token.jwt.enabled", value = "true")
@Property(name = "grpc.server.security.token.jwt.metadata-key-name", value = "AUTH")
@Property(name = "grpc.server.security.token.jwt.missing-token-status", value = "NOT_FOUND")
@Property(name = "grpc.server.security.token.jwt.failed-validation-token-status", value = "ABORTED")
@Property(name = "grpc.server.security.token.jwt.interceptor-order", value = "100")
class GrpcServerSecurityJwtConfigurationOverrideSpec extends Specification {

    @Inject
    List<ServerInterceptor> serverInterceptors

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "server interceptor bean present"() {
        expect:
        serverInterceptors.find { it instanceof GrpcServerSecurityJwtInterceptor }
    }

    def "GRPC server security JWT configuration defaults override"() {
        expect:
        config.enabled
        config.metadataKeyName == "AUTH"
        config.missingTokenStatus == Status.NOT_FOUND.code
        config.failedValidationTokenStatus == Status.ABORTED.code
        config.interceptorOrder == 100
    }

}
