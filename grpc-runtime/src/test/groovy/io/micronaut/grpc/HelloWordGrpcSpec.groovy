package io.micronaut.grpc

import io.grpc.*
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

@MicronautTest
class HelloWordGrpcSpec extends Specification {


    @Inject
    TestBean testBean

    @Inject
    MyInterceptor myInterceptor

    void "test hello world grpc"() {
        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        myInterceptor.intercepted
    }


    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            GreeterGrpc.newBlockingStub(channel)
        }
    }

    @Singleton
    static class MyInterceptor implements ServerInterceptor {

        boolean intercepted = false
        @Override
        def <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            intercepted = true
            return next.startCall(call, headers)
        }
    }

    @Singleton
    static class TestBean {
        @Inject
        GreeterGrpc.GreeterBlockingStub blockingStub

        String sayHello(String message) {
            blockingStub.sayHello(
                    HelloRequest.newBuilder().setName(message).build()
            ).message
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
