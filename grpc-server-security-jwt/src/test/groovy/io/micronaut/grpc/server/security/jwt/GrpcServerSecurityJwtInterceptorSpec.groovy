package io.micronaut.grpc.server.security.jwt

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import jakarta.inject.Singleton
import spock.lang.Specification

class GrpcServerSecurityJwtInterceptorSpec extends Specification {

    private static final String REQUIRED_ENV = "grpc-server-security-jwt-spec"
    private static final String SECRET = "SeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3t"
    private static final Map<String, Object> DEFAULT_CONFIGURATION = [
        "micronaut.security.enabled"                                   : true,
        "micronaut.security.token.jwt.signatures.secret.generator.secret": SECRET,
        "micronaut.security.token.jwt.signatures.secret.generator.base64": false,
        "grpc.server.security.enabled"                                 : true,
        "grpc.server.security.token.jwt.enabled"                       : true
    ]

    void "accepts valid jwt for authenticated method"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: ".*", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        expect:
        testBean.sayHelloWithJwt("Brian") == "Hello Brian"

        cleanup:
        stopContext(context)
    }

    void "accepts bearer jwt with surrounding whitespace and lowercase scheme"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: ".*", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        expect:
        testBean.sayHelloWithBearerJwt("Brian") == "Hello Brian"

        cleanup:
        stopContext(context)
    }

    void "rejects missing jwt for authenticated method"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: ".*", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithoutJwt("Brian")

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.UNAUTHENTICATED

        cleanup:
        stopContext(context)
    }

    void "rejects invalid jwt"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: ".*", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithInvalidJwt("Brian")

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        stopContext(context)
    }

    void "rejects valid jwt without required role"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: ".*", access: ["ROLE_HELLO"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian", ["ROLE_OTHER"])

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        stopContext(context)
    }

    void "matches the fully qualified gRPC method name in intercept-url-map"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: "helloworld\\.Greeter/SayHello", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        expect:
        testBean.sayHelloWithJwt("Brian") == "Hello Brian"

        cleanup:
        stopContext(context)
    }

    void "rejects when intercept-url-map does not match the gRPC method name"() {
        given:
        ApplicationContext context = startContext([
            "micronaut.security.intercept-url-map": [[pattern: "helloworld\\.Greeter/OtherMethod", access: ["isAuthenticated()"]]]
        ])
        TestBean testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian")

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        stopContext(context)
    }

    private static ApplicationContext startContext(Map<String, Object> config) {
        ApplicationContext context = ApplicationContext.run(new LinkedHashMap<>(DEFAULT_CONFIGURATION + config), REQUIRED_ENV, Environment.TEST)
        context.getBean(GrpcEmbeddedServer).start()
        context
    }

    private static void stopContext(ApplicationContext context) {
        if (context != null) {
            context.getBean(GrpcEmbeddedServer).close()
            context.stop()
        }
    }

    @Factory
    @Requires(env = REQUIRED_ENV)
    static class Clients {

        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            GreeterGrpc.newBlockingStub(channel)
        }
    }

    @Singleton
    @Requires(env = REQUIRED_ENV)
    static class TestBean {

        private final JwtTokenGenerator jwtTokenGenerator
        private final GreeterGrpc.GreeterBlockingStub blockingStub

        TestBean(JwtTokenGenerator jwtTokenGenerator, GreeterGrpc.GreeterBlockingStub blockingStub) {
            this.jwtTokenGenerator = jwtTokenGenerator
            this.blockingStub = blockingStub
        }

        String sayHelloWithJwt(String message, List<String> roles = []) {
            Authentication authentication = Authentication.build("micronaut", roles)
            String jwt = jwtTokenGenerator.generateToken(authentication, 60).orElseThrow()
            return sayHelloWithMetadata(message, jwt)
        }

        String sayHelloWithBearerJwt(String message, List<String> roles = []) {
            Authentication authentication = Authentication.build("micronaut", roles)
            String jwt = jwtTokenGenerator.generateToken(authentication, 60).orElseThrow()
            return sayHelloWithMetadata(message, "  bearer ${jwt}  ")
        }

        String sayHelloWithInvalidJwt(String message) {
            sayHelloWithMetadata(message, "invalid.jwt")
        }

        String sayHelloWithoutJwt(String message) {
            HelloRequest request = HelloRequest.newBuilder().setName(message).build()
            blockingStub.sayHello(request).message
        }

        private String sayHelloWithMetadata(String message, String token) {
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of("JWT", Metadata.ASCII_STRING_MARSHALLER), token)
            HelloRequest request = HelloRequest.newBuilder().setName(message).build()
            blockingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)).sayHello(request).message
        }
    }

    @Singleton
    @Requires(env = REQUIRED_ENV)
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello ${request.name}").build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
