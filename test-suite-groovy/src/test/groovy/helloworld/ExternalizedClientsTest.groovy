package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "ExternalizedClientsTest")
@Property(name = "my.server", value = "localhost")
@Property(name = "my.port", value = "8443")
@MicronautTest
class ExternalizedClientsTest extends Specification {

    @Inject
    GreeterGrpc.GreeterStub greeterStub

    void "test channel target is resolved from configuration"() {
        expect:
        greeterStub.channel.authority() == "localhost:8443"
    }
}
