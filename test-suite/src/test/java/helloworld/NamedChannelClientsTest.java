package helloworld;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "NamedChannelClientsTest")
@Property(name = "grpc.server.port", value = "50152")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50152")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class NamedChannelClientsTest {

    @Inject
    GreeterGrpc.GreeterStub greeterStub;

    @Test
    void testNamedChannel() throws Exception {
        CompletableFuture<String> message = new CompletableFuture<>();
        greeterStub.sayHello(HelloRequest.newBuilder().setName("Fred").build(), new StreamObserver<>() {
            @Override
            public void onNext(HelloReply reply) {
                message.complete(reply.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                message.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
            }
        });
        assertEquals("Hello Fred", message.get(10, TimeUnit.SECONDS));
    }
}
