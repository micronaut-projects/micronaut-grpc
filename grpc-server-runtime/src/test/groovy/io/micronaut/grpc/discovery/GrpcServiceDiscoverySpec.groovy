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
package io.micronaut.grpc.discovery

import io.grpc.ManagedChannel
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class GrpcServiceDiscoverySpec extends Specification {

    void "test GRPC named service discovery"() {
        when:"A service is run"
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name':'greet',
                'grpc.server.port': port
        ])

        and:'then a client is run that declares the service'
        ApplicationContext client = ApplicationContext.run([
                (GrpcNameResolverProvider.ENABLED): true,
                'grpc.channels.greet.plaintext':true,
                'micronaut.http.services.greet.url': server.URL.toString()
        ])

        GreeterGrpc.GreeterFutureStub stub = client.getBean(GreeterGrpc.GreeterFutureStub)

        then:
        stub.sayHello(HelloRequest.newBuilder().setName("test").build()).get().message == 'Hello test'


        cleanup:
        client.stop()
        server.stop()
    }

    void "test GRPC channel explicit URI"() {
        when:"A service is run"
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name':'greet',
                'grpc.server.port': port
        ])

        and:'then a client is run that declares the service'
        ApplicationContext client = ApplicationContext.run([
                (GrpcNameResolverProvider.ENABLED): true,
                'grpc.client.plaintext':true,
                'my.port':server.getPort()
        ])

        GreeterGrpc.GreeterStub stub = client.getBean(GreeterGrpc.GreeterStub)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)
        HelloReply reply
        stub.sayHello(HelloRequest.newBuilder().setName("test").build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                reply = value
            }

            @Override
            void onError(Throwable t) {

            }

            @Override
            void onCompleted() {

            }
        })
        then:
        conditions.eventually {
            reply != null
        }


        cleanup:
        client.stop()
        server.stop()
    }



    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterFutureStub futureStub(@GrpcChannel("greet") ManagedChannel channel) {
            GreeterGrpc.newFutureStub(
                    channel
            )
        }

        @Singleton
        GreeterGrpc.GreeterStub reactiveStub(@GrpcChannel('http://localhost:${my.port}') ManagedChannel channel) {
            GreeterGrpc.newStub(
                    channel
            )
        }
    }
}
