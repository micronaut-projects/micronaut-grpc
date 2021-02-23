package io.micronaut.grpc.server.security.jwt.interceptor

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.StatusRuntimeException
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

@MicronautTest(environments = ["greeter-hello-world", Environment.TEST])
@Property(name = "grpc.server.security.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.enabled", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret", value = "SeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3tSeCr3t")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.base64", value = "true")
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm", value = "HS512")
class GrpcServerSecurityJwtInterceptorIntegrationSpec extends Specification {

    @Inject
    private TestBean testBean

    def "test valid JWT works"() {
        expect:
        testBean.sayHelloWithJwt("Brian") == "Hello Brian"
    }

    def "test missing JWT does not work"() {
        when:
        testBean.sayHelloWithoutJwt("Brian")

        then:
        thrown(StatusRuntimeException)
    }

    @Factory
    @Requires(env = "greeter-hello-world")
    static class Clients {

        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            GreeterGrpc.newBlockingStub(channel)
        }

    }

    @Singleton
    static class TestBean {

        @Inject
        JwtTokenGenerator jwtTokenGenerator

        @Inject
        GreeterGrpc.GreeterBlockingStub blockingStub

        String sayHelloWithJwt(String message) {
            UserDetails userDetails = new UserDetails("micronaut", [])
            String jwt = jwtTokenGenerator.generateToken(userDetails, 60).get()
            Metadata metadata = new Metadata()
            metadata.put(Metadata.Key.of("JWT", Metadata.ASCII_STRING_MARSHALLER), jwt)
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            MetadataUtils.attachHeaders(blockingStub, metadata).sayHello(helloRequest).message
        }

        String sayHelloWithoutJwt(String message) {
            HelloRequest helloRequest = HelloRequest.newBuilder().setName(message).build()
            blockingStub.sayHello(helloRequest).message
        }

    }

    @Singleton
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

    }

}