package helloworld;

// tag::imports[]
import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.annotation.GrpcChannel;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "ExternalizedClientsTest")
// tag::clazz[]
@Factory
class ExternalizedClients {

    @Singleton
    GreeterGrpc.GreeterStub greeterStub(
        @GrpcChannel("https://${my.server}:${my.port}")
        ManagedChannel channel) {
        return GreeterGrpc.newStub(
            channel
        );
    }
}
// end::clazz[]
