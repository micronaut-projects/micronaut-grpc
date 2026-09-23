import java
from typing import Annotated

from io.grpc import Status, StatusRuntimeException
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloRequest = java.type("helloworld.HelloRequest")


@Property(name="spec.name", value="ServerBuilderListenerTest")
@MicronautTest
class ServerBuilderListenerTest:

    blocking_stub: Annotated[GreeterGrpc.GreeterBlockingStub, Inject]

    @Test
    def test_max_inbound_message_size(self):
        request = HelloRequest.newBuilder().setName("x" * 2000).build()
        try:
            self.blocking_stub.sayHello(request)
        except StatusRuntimeException as ex:
            assert ex.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED
        else:
            assert False, "the request should have been rejected"
