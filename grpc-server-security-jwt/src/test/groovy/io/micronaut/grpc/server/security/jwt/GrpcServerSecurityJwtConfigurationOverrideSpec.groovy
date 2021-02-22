package io.micronaut.grpc.server.security.jwt

import io.micronaut.context.annotation.Property
import io.micronaut.core.order.Ordered
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
class GrpcServerSecurityJwtConfigurationOverrideSpec extends Specification {

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "GRPC server security JWT configuration defaults override"() {
        expect:
        config.enabled
        config.metadataKeyName == "JWT"
        config.order == Ordered.HIGHEST_PRECEDENCE
    }

}
