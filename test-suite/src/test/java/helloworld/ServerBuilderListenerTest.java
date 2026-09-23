package helloworld;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "spec.name", value = "ServerBuilderListenerTest")
@MicronautTest
class ServerBuilderListenerTest {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub;

    @Test
    void testMaxInboundMessageSize() {
        HelloRequest request = HelloRequest.newBuilder()
            .setName("x".repeat(2000))
            .build();
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> blockingStub.sayHello(request));
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.getStatus().getCode());
    }
}
