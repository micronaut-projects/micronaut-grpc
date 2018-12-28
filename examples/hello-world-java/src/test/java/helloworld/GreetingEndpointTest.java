package helloworld;

import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.server.GrpcServerChannel;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@MicronautTest
public class GreetingEndpointTest {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub;

    @Test
    void testHelloWorld() {
        Assertions.assertEquals(
                "Hello Fred",
                blockingStub.sayHello(HelloRequest.newBuilder().setName("Fred").build())
                            .getMessage()
        );
    }

}

@Factory
class Clients {
    @Bean
    GreeterGrpc.GreeterBlockingStub blockingStub(
            @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
        return GreeterGrpc.newBlockingStub(
                channel
        );
    }
}