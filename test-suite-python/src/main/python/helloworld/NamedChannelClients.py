# tag::imports[]
import java
from typing import Annotated

from io.grpc import ManagedChannel
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from micronaut.grpc.annotation import GrpcChannel

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="NamedChannelClientsTest")
# tag::clazz[]
@Factory
class NamedChannelClients:

    @Singleton
    def greeter_stub(
        self,
        channel: Annotated[ManagedChannel, GrpcChannel("greeter")]
    ) -> GreeterGrpc.GreeterStub:
        return GreeterGrpc.newStub(
            channel
        )
# end::clazz[]
