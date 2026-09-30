package helloworld;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "ServerInterceptorTest")
@MicronautTest
class ServerInterceptorTest {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub;

    @Test
    void testInterceptorsAreRegistered() {
        HelloRequest request = HelloRequest.newBuilder().setName("Fred").build();
        assertEquals("Hello Fred", blockingStub.sayHello(request).getMessage());
        // the ordered bean and the interceptor wrapped by the factory both intercept the call
        assertEquals(
            List.of("helloworld.Greeter/SayHello", "helloworld.Greeter/SayHello"),
            List.copyOf(CustomInterceptor.INTERCEPTED)
        );
    }
}
