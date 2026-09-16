import java
from typing import Annotated

from jakarta.inject import Inject
from java.time import Duration
from micronaut.context.annotation import Bean, Factory
from micronaut.grpc.annotation import GrpcChannel
from micronaut.grpc.server import GrpcServerChannel
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from reactor.core.publisher import Mono

try:
    from io.grpc import ManagedChannel
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import ManagedChannel

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
HelloRequest = java.type("helloworld.HelloRequest")
ReactorReactiveGreeterGrpc = java.type("helloworld.ReactorReactiveGreeterGrpc")


# tag::reactive-test[]
@MicronautTest
class ReactiveGreetingEndpointTest:

    reactive_stub: Annotated[ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub, Inject]

    @Test
    def test_reactive_hello_world(self):
        message = self.reactive_stub.sayHello(Mono.just(HelloRequest.newBuilder().setName("Fred").build())) \
            .map(lambda reply: reply.getMessage()) \
            .block(Duration.ofSeconds(5))

        assert message == "Hello Fred"
# end::reactive-test[]


@Factory
class ReactiveClients:

    # tag::reactive-client[]
    @Bean
    def reactive_stub(self, channel: Annotated[ManagedChannel, GrpcChannel(GrpcServerChannel.NAME)]) -> ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub:
        return ReactorReactiveGreeterGrpc.newReactorStub(channel)
    # end::reactive-client[]
