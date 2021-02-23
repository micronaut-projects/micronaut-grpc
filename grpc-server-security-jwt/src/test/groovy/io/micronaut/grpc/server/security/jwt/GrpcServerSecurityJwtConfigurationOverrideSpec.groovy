package io.micronaut.grpc.server.security.jwt

import io.grpc.Status
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
@Property(name = "grpc.server.security.jwt.metadata-key-name", value = "AUTH")
@Property(name = "grpc.server.security.jwt.missing-token-status", value = "NOT_FOUND")
@Property(name = "grpc.server.security.jwt.failed-validation-token-status", value = "ABORTED")
@Property(name = "grpc.server.security.jwt.interceptor-order", value = "100")
class GrpcServerSecurityJwtConfigurationOverrideSpec extends Specification {

    @Inject
    GrpcServerSecurityJwtConfiguration config

    def "GRPC server security JWT configuration defaults override"() {
        expect:
        config.enabled
        config.metadataKeyName == "AUTH"
        config.missingTokenStatus == Status.NOT_FOUND.code
        config.failedValidationTokenStatus == Status.ABORTED.code
        config.interceptorOrder == 100
    }

}
