package helloworld;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "ExternalizedClientsTest")
@Property(name = "my.server", value = "localhost")
@Property(name = "my.port", value = "8443")
@MicronautTest
class ExternalizedClientsTest {

    @Inject
    GreeterGrpc.GreeterStub greeterStub;

    @Test
    void testChannelTargetIsResolvedFromConfiguration() {
        assertEquals("localhost:8443", greeterStub.getChannel().authority());
    }
}
