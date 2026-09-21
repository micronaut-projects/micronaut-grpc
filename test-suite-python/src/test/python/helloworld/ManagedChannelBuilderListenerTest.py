import java
from typing import Annotated

from io.grpc import ManagedChannel, Status, StatusRuntimeException
from jakarta.inject import Inject, Named
from micronaut.context.annotation import Bean, Factory, Property, Requires
from micronaut.grpc.annotation import GrpcChannel
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloRequest = java.type("helloworld.HelloRequest")


@Property(name="spec.name", value="ManagedChannelBuilderListenerTest")
@Property(name="grpc.server.port", value="50181")
@Property(name="grpc.channels.greeter.address", value="localhost:50181")
@Property(name="grpc.channels.greeter.plaintext", value="true")
@MicronautTest
class ManagedChannelBuilderListenerTest:

    greeter_stub: Annotated[GreeterGrpc.GreeterBlockingStub, Inject, Named("greeter")]

    @Test
    def test_max_inbound_message_size(self):
        # the reply is larger than the 1024 bytes the channel accepts
        request = HelloRequest.newBuilder().setName("x" * 2000).build()
        try:
            self.greeter_stub.sayHello(request)
        except StatusRuntimeException as ex:
            assert ex.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED
        else:
            assert False, "the reply should have been rejected"


# the "grpc-server" test channel bypasses the channel builders, so the test uses the named "greeter" channel
@Requires(property="spec.name", value="ManagedChannelBuilderListenerTest")
@Factory
class GreeterChannelClients:

    @Bean
    @Named("greeter")
    def greeter_stub(self, channel: Annotated[ManagedChannel, GrpcChannel("greeter")]) -> GreeterGrpc.GreeterBlockingStub:
        return GreeterGrpc.newBlockingStub(channel)
