package io.micronaut.grpc.server.security.jwt

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
@Property(name = "grpc.server.security.jwt.metadata-key-name", value = "AUTH")
@Property(name = "grpc.server.security.jwt.order", value = "100")
class GrpcServerSecurityJwtConfigurationSpec extends Specification {

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "GRPC server security JWT configuration defaults"() {
        expect:
        config.enabled
        config.metadataKeyName == "AUTH"
        config.order == 100
    }

}
