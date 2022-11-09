package io.micronaut.grpc.server.security.jwt.interceptor

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
import io.micronaut.security.authentication.ServerAuthentication
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll

class GrpcServerSecurityJwtInterceptorSpec extends Specification {

    private static final REQUIRED_ENV = "greeter-hello-world-jwt"
    private static final Map defaultConfigurations = [
            "grpc.server.security.token.jwt.enabled": true,
            "micronaut.security.token.jwt.signatures.secret.generator.secret": "SeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3t",
            "micronaut.security.token.jwt.signatures.secret.generator.base64": false,
            "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm": "HS512"
    ]

    def "test order configuration respected"() {
        given:
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("grpc.server.security.token.jwt.interceptor-order", 100)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def interceptor = context.getBean(GrpcServerSecurityJwtInterceptor)

        then:
        interceptor.order == 100

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    @Unroll
    def "test valid JWT works when required role = isAuthenticated() - pattern matches"(String pattern) {
        given:
        List interceptMethodPatterns = [
                [pattern: pattern, access: ["isAuthenticated()"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithJwt("Brian") == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()

        where:
        pattern << ["helloworld.Greeter/SayHello", "helloworld.Greeter/.*", "helloworld.*"]
    }

    def "test valid JWT works when required role = isAuthenticated() and custom metadata key name"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["isAuthenticated()"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("grpc.server.security.token.jwt.metadata-key-name", "AUTH")
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithCustomJwt("AUTH", "Brian") == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT works when ROLE_HELLO required"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["ROLE_HELLO"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"]) == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT works with no roles configured and reject-not-found = false"() {
        given:
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.reject-not-found", false)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"]) == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    @Unroll
    def "test valid JWT works with no roles found matching pattern and reject-not-found = false"(String pattern) {
        given:
        List interceptMethodPatterns = [
                [pattern: pattern, access: ["ROLE_HELLO"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        config.put("micronaut.security.reject-not-found", false)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"]) == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()

        where:
        pattern << ["helloworld.Greeter/SayGoodbye", "helloworld.Greeter/Talk.*", "helloearth.*"]
    }

    def "test valid JWT works with no matching roles configured and reject-not-found = false"() {
        given:
        List interceptMethodPatterns = [
                [pattern: "example.Foo/get.*", access: ["ROLE_HELLO"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        config.put("micronaut.security.reject-not-found", false)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithJwt("Brian") == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT denied when required roles = denyAll()"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["ROLE_HELLO", "denyAll()"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"])

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT denied when missing required role = ROLE_HELLO"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["ROLE_HELLO"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian", ["ROLE_OTHER"]) == "Hello Brian"

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT denied when required roles = denyAll() - custom status"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["ROLE_HELLO", "denyAll()"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("grpc.server.security.token.jwt.failed-validation-token-status", "ABORTED")
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"])

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.ABORTED

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT denied when no roles configured"() {
        given:
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwt("Brian", ["ROLE_HELLO"])

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test valid JWT denied when no subject / claims present"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["ROLE_HELLO", "isAuthenticated()"]]
        ]
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithJwtNoSubject("Brian")

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    def "test missing JWT works when role = isAnonymous()"() {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: ["isAnonymous()"]]
        ];
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()

        when:
        def testBean = context.getBean(TestBean)

        then:
        testBean.sayHelloWithoutJwt("Brian") == "Hello Brian"

        cleanup:
        embeddedServer.close()
        context.stop()
    }

    @Unroll
    def "test missing JWT denied when role = #role"(String role) {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: [role]]
        ];
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithoutJwt("Brian")

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.UNAUTHENTICATED.code

        cleanup:
        embeddedServer.close()
        context.stop()

        where:
        role << ["isAuthenticated()", "ROLE_HELLO"]
    }

    @Unroll
    def "test missing JWT denied when role = #role - custom status"(String role) {
        given:
        List interceptMethodPatterns = [
                [pattern: ".*", access: [role]]
        ];
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        config.put("grpc.server.security.token.jwt.missing-token-status", "NOT_FOUND")
        config.put("micronaut.security.intercept-url-map", interceptMethodPatterns)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithoutJwt("Brian")

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.NOT_FOUND.code

        cleanup:
        embeddedServer.close()
        context.stop()

        where:
        role << ["isAuthenticated()", "ROLE_HELLO"]
    }

    def "test invalid JWT denied"() {
        given:
        Map<String, Object> config = new HashMap<>(defaultConfigurations)
        def context = ApplicationContext.run(config, REQUIRED_ENV, Environment.TEST)
        def embeddedServer = context.getBean(GrpcEmbeddedServer)
        embeddedServer.start()
        def testBean = context.getBean(TestBean)

        when:
        testBean.sayHelloWithNonParseableJwt("Brian")

        then:
        StatusRuntimeException statusRuntimeException = thrown(StatusRuntimeException)
        statusRuntimeException.status.code == Status.Code.PERMISSION_DENIED

        cleanup:
        embeddedServer.close()
        context.stop()
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

        @Inject
        JwtTokenGenerator jwtTokenGenerator

        @Inject
        GreeterGrpc.GreeterBlockingStub blockingStub

        String sayHelloWithJwt(String message, final List<String> roles = []) {
            ServerAuthentication userDetails = new ServerAuthentication("micronaut", roles, [:])
            String jwt = jwtTokenGenerator.generateToken(userDetails, 60).get()
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of("JWT", Metadata.ASCII_STRING_MARSHALLER), jwt)
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            MetadataUtils.attachHeaders(blockingStub, metadata).sayHello(helloRequest).message
        }

        String sayHelloWithCustomJwt(String metadataKeyName, String message, final List<String> roles = []) {
            ServerAuthentication userDetails = new ServerAuthentication("micronaut", roles, [:])
            String jwt = jwtTokenGenerator.generateToken(userDetails, 60).get()
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of(metadataKeyName, Metadata.ASCII_STRING_MARSHALLER), jwt)
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            MetadataUtils.attachHeaders(blockingStub, metadata).sayHello(helloRequest).message
        }

        String sayHelloWithJwtNoSubject(String message) {
            String jwt = jwtTokenGenerator.generateToken([:]).get()
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of("JWT", Metadata.ASCII_STRING_MARSHALLER), jwt)
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            MetadataUtils.attachHeaders(blockingStub, metadata).sayHello(helloRequest).message
        }

        String sayHelloWithNonParseableJwt(String message) {
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of("JWT", Metadata.ASCII_STRING_MARSHALLER), "invalid-jwt-which-cannot-be-parsed")
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            MetadataUtils.attachHeaders(blockingStub, metadata).sayHello(helloRequest).message
        }

        String sayHelloWithoutJwt(String message) {
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            blockingStub.sayHello(helloRequest).message
        }

    }

    @Singleton
    @Requires(env = REQUIRED_ENV)
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

    }

}
