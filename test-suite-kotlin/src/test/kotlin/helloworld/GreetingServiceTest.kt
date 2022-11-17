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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class GreetingServiceTest {

    @Inject
    lateinit var greetingClient: GreeterGrpcKt.GreeterCoroutineStub

    @Test
    fun testGreetingService() = runBlocking {
        Assertions.assertEquals(
                "Hello John",
                greetingClient.sayHello(
                        HelloRequest.newBuilder().setName("John").build()
                ).message
        )
    }
}

@Factory
class Clients {
    @Singleton
    fun greetingClient(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel): GreeterGrpcKt.GreeterCoroutineStub {
        return GreeterGrpcKt.GreeterCoroutineStub(
                channel
        )
    }
}
