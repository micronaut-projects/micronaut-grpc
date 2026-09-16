package helloworld

// tag::imports[]
import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "NamedChannelClientsTest")
// tag::clazz[]
@Factory
class NamedChannelClients {

    @Singleton
    fun greeterStub(
        @GrpcChannel("greeter")
        channel: ManagedChannel,
    ): GreeterGrpc.GreeterStub {
        return GreeterGrpc.newStub(
            channel
        )
    }
}
// end::clazz[]
