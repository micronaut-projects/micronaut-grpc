import java
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")


@Property(name="spec.name", value="DiscoveryClientsTest")
@Property(name="grpc.client.discovery.enabled", value="true")
@MicronautTest
class DiscoveryClientsTest:

    greeter_stub: Annotated[GreeterGrpc.GreeterStub, Inject]

    @Test
    def test_service_id_target(self):
        assert self.greeter_stub.getChannel().authority() == "//greeter"
