package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "DnsClientsTest")
@MicronautTest
class DnsClientsTest extends Specification {

    @Inject
    GreeterGrpc.GreeterStub greeterStub

    void "test dns target"() {
        expect:
        greeterStub.channel.authority() == "greeter"
    }
}
