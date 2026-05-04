/*
 * Copyright 2017-2026 original authors
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

import io.grpc.Channel
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.grpc.server.GrpcServerChannel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

class InProcessGrpcServerChannelSpec extends Specification {

    void "test grpc-server channel starts in-process server in application context"() {
        given:
        def context = ApplicationContext.run([
            'grpc.server.in-process-name': 'lazy-in-process'
        ])

        when:
        def bean = context.getBean(TestBean)

        then:
        bean.sayHello("Fred") == "Hello Fred"
        context.getBean(GrpcEmbeddedServer).isRunning()

        cleanup:
        context.close()
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
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
