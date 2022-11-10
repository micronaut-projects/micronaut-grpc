/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.grpc


import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class HelloWordGrpcSpec extends Specification {

    @Inject
    TestBean testBean

    @Inject
    MyInterceptor myInterceptor

    @Inject
    FirstInterceptor firstInterceptor

    @Inject
    MiddleInterceptor middleInterceptor

    @Inject
    LastInterceptor lastInterceptor

    @Inject
    UnorderedInterceptor unorderedInterceptor

    void "test hello world grpc"() {
        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        myInterceptor.intercepted
        firstInterceptor.intercepted < unorderedInterceptor.intercepted
        unorderedInterceptor.intercepted < middleInterceptor.intercepted
        middleInterceptor.intercepted < lastInterceptor.intercepted
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
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            intercepted = true
            return next.startCall(call, headers)
        }
    }


    static class BaseClientInterceptor implements ClientInterceptor {
        long intercepted

        @Override
        <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next
        ) {
            sleep 10
            intercepted = System.currentTimeMillis()
            return next.newCall(method, callOptions)
        }
    }

    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class FirstInterceptor extends BaseClientInterceptor {
    }

    @Singleton
    @Order(Ordered.LOWEST_PRECEDENCE)
    static class LastInterceptor extends BaseClientInterceptor {
    }

    @Singleton
    static class MiddleInterceptor extends BaseClientInterceptor implements Ordered {
        @Override
        int getOrder() {
            return 1000
        }
    }

    @Singleton
    static class UnorderedInterceptor extends BaseClientInterceptor {}


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
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
