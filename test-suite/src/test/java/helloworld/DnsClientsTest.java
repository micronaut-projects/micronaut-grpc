package helloworld;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "DnsClientsTest")
@MicronautTest
class DnsClientsTest {

    @Inject
    GreeterGrpc.GreeterStub greeterStub;

    @Test
    void testDnsTarget() {
        assertEquals("greeter", greeterStub.getChannel().authority());
    }
}
