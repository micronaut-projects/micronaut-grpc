import java

from java.util.concurrent import CompletableFuture, TimeUnit
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloRequest = java.type("helloworld.HelloRequest")


class ReplyObserver:
    """A `StreamObserver` completing a future with the message of the reply."""

    def __init__(self, message: CompletableFuture):
        self.message = message

    def onNext(self, reply) -> None:
        self.message.complete(reply.getMessage())

    def onError(self, t) -> None:
        self.message.completeExceptionally(t)

    def onCompleted(self) -> None:
        pass


@Property(name="spec.name", value="NamedChannelClientsTest")
@Property(name="grpc.server.port", value="50182")
@Property(name="grpc.channels.greeter.address", value="localhost:50182")
@Property(name="grpc.channels.greeter.plaintext", value="true")
@MicronautTest
class NamedChannelClientsTest:

    greeter_stub: Annotated[GreeterGrpc.GreeterStub, Inject]

    @Test
    def test_named_channel(self):
        message = CompletableFuture()
        self.greeter_stub.sayHello(HelloRequest.newBuilder().setName("Fred").build(), ReplyObserver(message))
        assert message.get(10, TimeUnit.SECONDS) == "Hello Fred"
