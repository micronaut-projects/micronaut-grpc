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
package helloworld

import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Singleton

@MicronautTest
class GreetingEndpointSpec extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub

    void "test greeting endpoint"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().with {
            name = "Fred"
            build()
        }

        expect:
        blockingStub.sayHello(
               request
        ).message == 'Hello Fred'
    }

    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(
                @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
            GreeterGrpc.newBlockingStub(
                    channel
            )
        }
    }
}

