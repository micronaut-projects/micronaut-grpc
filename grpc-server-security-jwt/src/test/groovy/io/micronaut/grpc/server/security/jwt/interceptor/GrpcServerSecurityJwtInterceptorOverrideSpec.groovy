package io.micronaut.grpc.server.security.jwt.interceptor


import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.micronaut.context.annotation.Property
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
@Property(name = "grpc.server.security.jwt.metadata-key-name", value = METADATA_KEY_NAME)
@Property(name = "grpc.server.security.jwt.missing-token-status", value = "NOT_FOUND")
@Property(name = "grpc.server.security.jwt.failed-validation-token-status", value = "ABORTED")
@Property(name = "grpc.server.security.jwt.interceptor-order", value = ORDER)
@Property(name = "micronaut.security.token.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret", value = "SeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3t")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.base64", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm", value = "HS512")
class GrpcServerSecurityJwtInterceptorOverrideSpec extends Specification {

    static final String METADATA_KEY_NAME = "AUTH"
    static final String ORDER = "10"

    @Inject
    private JwtTokenGenerator jwtTokenGenerator

    @Inject
    private GrpcServerSecurityJwtInterceptor interceptor

    def "test interceptor configured correctly"() {
        expect:
        interceptor.order == Integer.parseInt(ORDER)
        interceptor.metadataKey == Metadata.Key.of(METADATA_KEY_NAME, Metadata.ASCII_STRING_MARSHALLER)
    }

    def "test interceptCall - missing JWT metadata key"() {
        given:
        ServerCall<?, ?> mockServerCall = Mock()
        Metadata metadata = new Metadata()
        ServerCallHandler<?, ?> mockServerCallHandler = Mock()

        when:
        interceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        0 * _

        and:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.NOT_FOUND.code
        statusRuntimeException.status.description == "${METADATA_KEY_NAME.toLowerCase()} key missing in gRPC metadata"
    }

    def "test interceptCall - invalid JWT"() {
        given:
        ServerCall<?, ?> mockServerCall = Mock()
        Metadata metadata = new Metadata()
        String jwt = "invalid-token"
        metadata.put(Metadata.Key.of(METADATA_KEY_NAME, Metadata.ASCII_STRING_MARSHALLER), jwt)
        ServerCallHandler<?, ?> mockServerCallHandler = Mock()
        ServerCall.Listener<?> mockServerCallListener = Mock()

        when:
        interceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        1 * mockServerCallHandler.startCall(mockServerCall, metadata) >> mockServerCallListener
        0 * _

        and:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.ABORTED.code
        statusRuntimeException.status.description == "JWT validation failed"
    }

    def "test interceptCall - invalid claims JWT"() {
        given:
        ServerCall<?, ?> mockServerCall = Mock()
        Metadata metadata = new Metadata()
        String jwt = jwtTokenGenerator.generateToken([:]).get()
        metadata.put(Metadata.Key.of(METADATA_KEY_NAME, Metadata.ASCII_STRING_MARSHALLER), jwt)
        ServerCallHandler<?, ?> mockServerCallHandler = Mock()
        ServerCall.Listener<?> mockServerCallListener = Mock()

        when:
        interceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        1 * mockServerCallHandler.startCall(mockServerCall, metadata) >> mockServerCallListener
        0 * _

        and:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.ABORTED.code
        statusRuntimeException.status.description == "JWT validation failed"
    }

    def "test interceptCall - expired JWT"() {
        given:
        ServerCall<?, ?> mockServerCall = Mock()
        Metadata metadata = new Metadata()
        UserDetails userDetails = new UserDetails("micronaut", [])
        int expiration = 1
        String jwt = jwtTokenGenerator.generateToken(userDetails, 1).get()
        metadata.put(Metadata.Key.of(METADATA_KEY_NAME, Metadata.ASCII_STRING_MARSHALLER), jwt)
        ServerCallHandler<?, ?> mockServerCallHandler = Mock()
        ServerCall.Listener<?> mockServerCallListener = Mock()

        when:
        sleep((expiration * 1000) + 500) // Allow for token to expire
        interceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        1 * mockServerCallHandler.startCall(mockServerCall, metadata) >> mockServerCallListener
        0 * _

        and:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.ABORTED.code
        statusRuntimeException.status.description == "JWT validation failed"
    }

    def "test interceptCall - valid JWT"() {
        given:
        ServerCall<?, ?> mockServerCall = Mock()
        Metadata metadata = new Metadata()
        UserDetails userDetails = new UserDetails("micronaut", ["admin"])
        String jwt = jwtTokenGenerator.generateToken(userDetails, 60).get()
        metadata.put(Metadata.Key.of(METADATA_KEY_NAME, Metadata.ASCII_STRING_MARSHALLER), jwt)
        ServerCallHandler<?, ?> mockServerCallHandler = Mock()
        ServerCall.Listener<?> mockServerCallListener = Mock()

        when:
        ServerCall.Listener<?> serverCallListener = interceptor.interceptCall(mockServerCall, metadata, mockServerCallHandler)

        then:
        1 * mockServerCallHandler.startCall(mockServerCall, metadata) >> mockServerCallListener
        0 * _

        and:
        serverCallListener
        serverCallListener instanceof ForwardingServerCallListener.SimpleForwardingServerCallListener
    }

}
