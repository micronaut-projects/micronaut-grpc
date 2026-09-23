package helloworld;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "DiscoveryClientsTest")
@Property(name = "grpc.client.discovery.enabled", value = "true")
@MicronautTest
class DiscoveryClientsTest {

    @Inject
    GreeterGrpc.GreeterStub greeterStub;

    @Test
    void testServiceIdTarget() {
        assertEquals("//greeter", greeterStub.getChannel().authority());
    }
}
