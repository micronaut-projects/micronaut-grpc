# tag::imports[]
import java
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Bean, Factory
from micronaut.grpc.annotation import GrpcChannel
from micronaut.grpc.server import GrpcServerChannel
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

try:
    from io.grpc import ManagedChannel
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import ManagedChannel

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloRequest = java.type("helloworld.HelloRequest")
# end::imports[]


# tag::test[]
@MicronautTest  # <1>
class GreetingEndpointTest:

    blocking_stub: Annotated[GreeterGrpc.GreeterBlockingStub, Inject]  # <2>

    @Test
    def test_hello_world(self):
        request = HelloRequest.newBuilder().setName("Fred").build()  # <3>
        assert self.blocking_stub.sayHello(request).getMessage() == "Hello Fred"
# end::test[]


# tag::clients[]
@Factory
class Clients:

    @Bean
    def blocking_stub(self, channel: Annotated[ManagedChannel, GrpcChannel(GrpcServerChannel.NAME)]) -> GreeterGrpc.GreeterBlockingStub:  # <1>
        return GreeterGrpc.newBlockingStub(channel)  # <2>
# end::clients[]
