package io.micronaut.grpc.server.security.jwt

import io.grpc.Status
import io.micronaut.context.annotation.Property
import io.micronaut.core.order.Ordered
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
class GrpcServerSecurityJwtConfigurationSpec extends Specification {

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "GRPC server security JWT configuration defaults"() {
        expect:
        config.enabled
        config.metadataKeyName == "JWT"
        config.missingTokenStatus == Status.UNAUTHENTICATED.code
        config.failedValidationTokenStatus == Status.PERMISSION_DENIED.code
        config.interceptorOrder == Ordered.HIGHEST_PRECEDENCE
    }

}
