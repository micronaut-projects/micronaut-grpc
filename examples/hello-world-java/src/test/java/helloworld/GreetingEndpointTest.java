package helloworld;
// tag::imports[]
import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.*;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.server.GrpcServerChannel;
import io.micronaut.test.annotation.MicronautTest;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
// end::imports[]

// tag::test[]
@MicronautTest // <1>
public class GreetingEndpointTest {

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