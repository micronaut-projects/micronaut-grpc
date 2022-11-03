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
package helloworld;
// tag::imports[]
import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.*;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.server.GrpcServerChannel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
// end::imports[]

// tag::test[]
@MicronautTest // <1>
class GreetingEndpointTest {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub; // <2>

    @Test
    void testHelloWorld() {
        final HelloRequest request = HelloRequest.newBuilder() // <3>
                                                 .setName("Fred")
                                                 .build();
        assertEquals(
                "Hello Fred",
                blockingStub.sayHello(request)
                            .getMessage()
        );
    }

}
// end::test[]

// tag::clients[]
@Factory
class Clients {
    @Bean
    GreeterGrpc.GreeterBlockingStub blockingStub(
            @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) { // <1>
        return GreeterGrpc.newBlockingStub( // <2>
                channel
        );
    }
}
// end::clients[]