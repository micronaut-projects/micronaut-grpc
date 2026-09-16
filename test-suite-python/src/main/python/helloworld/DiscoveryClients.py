# tag::imports[]
import java
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Factory
from micronaut.grpc.annotation import GrpcChannel

try:
    from io.grpc import ManagedChannel
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import ManagedChannel

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="DiscoveryClientsTest")
# tag::clazz[]
@Factory
class DiscoveryClients:

    @Singleton
    @Bean
    def greeter_stub(
        self,
        channel: Annotated[ManagedChannel, GrpcChannel("greeter")]
    ) -> GreeterGrpc.GreeterStub:
        return GreeterGrpc.newStub(
            channel
        )
# end::clazz[]
