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
package io.micronaut.grpc.server.tracing

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentracing.Tracer
import io.opentracing.mock.MockTracer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@MicronautTest
@Property(name = 'mock.tracer', value = 'true')
class GrpcTracingSpec extends Specification {

    @Inject
    TestBean testBean

    @Inject
    TracingInterceptor myInterceptor

    @Inject
    Tracer tracer

    MockTracer mockTracer = new MockTracer()

    void "test hello world grpc with tracing enabled"() {
        given:
        MockTracer tracer = mockTracer
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)
        testBean.sayHello("Fred") == "Hello Fred"

        expect:
        conditions.eventually {
            myInterceptor.intercepted
            tracer.finishedSpans().size() == 2
            tracer.finishedSpans()[0].operationName() == 'helloworld.Greeter/SayHello'
            tracer.finishedSpans()[1].operationName() == 'helloworld.Greeter/SayHello'
            tracer.finishedSpans().find { it.tags().get('span.kind') == 'client' }
            tracer.finishedSpans().find { it.tags().get('span.kind') == 'server' }
        }
    }

    @Singleton
    static class TracingInterceptor implements ServerInterceptor {

        boolean intercepted = false

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            intercepted = true
            return next.startCall(call, headers)
        }
    }

    @MockBean
    @Requires(property = "mock.tracer", value = "true")
    Tracer tracer() {
        return mockTracer
    }

    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            GreeterGrpc.newBlockingStub(channel)
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
    static class GreeterService extends GreeterGrpc.GreeterImplBase {
        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
