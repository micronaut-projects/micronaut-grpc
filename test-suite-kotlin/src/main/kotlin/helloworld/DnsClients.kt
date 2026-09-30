package helloworld

// tag::imports[]
import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "DnsClientsTest")
// tag::clazz[]
@Factory
class DnsClients {

    @Singleton
    fun greeterStub(
        @GrpcChannel("dns:///greeter")
        channel: ManagedChannel,
    ): GreeterGrpc.GreeterStub {
        return GreeterGrpc.newStub(
            channel
        )
    }
}
// end::clazz[]
