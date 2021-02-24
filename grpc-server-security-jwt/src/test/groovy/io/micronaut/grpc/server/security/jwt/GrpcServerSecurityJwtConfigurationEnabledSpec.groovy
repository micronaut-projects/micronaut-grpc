package io.micronaut.grpc.server.security.jwt


import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micronaut.context.annotation.Property
import io.micronaut.core.order.Ordered
import io.micronaut.grpc.server.security.jwt.interceptor.GrpcServerSecurityJwtInterceptor
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "micronaut.security.token.enabled", value = "true")
@Property(name = "grpc.server.security.token.jwt.enabled", value = "true")
class GrpcServerSecurityJwtConfigurationEnabledSpec extends Specification {

    @Inject
    List<ServerInterceptor> serverInterceptors

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "server interceptor bean present"() {
        expect:
        serverInterceptors.find { it instanceof GrpcServerSecurityJwtInterceptor }
    }

    def "GRPC server security JWT configuration defaults"() {
        expect:
        config.enabled
        config.metadataKeyName == "JWT"
        config.missingTokenStatus == Status.UNAUTHENTICATED.code
        config.failedValidationTokenStatus == Status.PERMISSION_DENIED.code
        !config.interceptMethodPatterns
        config.interceptorOrder == Ordered.HIGHEST_PRECEDENCE
    }

}
