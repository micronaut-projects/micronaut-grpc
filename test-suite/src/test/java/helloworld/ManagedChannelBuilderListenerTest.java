package helloworld;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "spec.name", value = "ManagedChannelBuilderListenerTest")
@Property(name = "grpc.server.port", value = "50151")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50151")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class ManagedChannelBuilderListenerTest {

    @Inject
    @Named("greeter")
    GreeterGrpc.GreeterBlockingStub greeterStub;

    @Test
    void testMaxInboundMessageSize() {
        // the reply is larger than the 1024 bytes the channel accepts
        HelloRequest request = HelloRequest.newBuilder()
            .setName("x".repeat(2000))
            .build();
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> greeterStub.sayHello(request));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.getStatus().getCode());
    }

    // the "grpc-server" test channel bypasses the channel builders, so the test uses the named "greeter" channel
    @Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
    @Factory
    static class GreeterChannelClients {

        @Bean
        @Named("greeter")
        GreeterGrpc.GreeterBlockingStub greeterStub(@GrpcChannel("greeter") ManagedChannel channel) {
            return GreeterGrpc.newBlockingStub(channel);
        }
    }
}
