package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "DiscoveryClientsTest")
@Property(name = "grpc.client.discovery.enabled", value = "true")
@MicronautTest
class DiscoveryClientsTest extends Specification {

    @Inject
    GreeterGrpc.GreeterStub greeterStub

    void "test service id target"() {
        expect:
        greeterStub.channel.authority() == "//greeter"
    }
}
