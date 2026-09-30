package helloworld;

// tag::imports[]
import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.annotation.GrpcChannel;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "DiscoveryClientsTest")
// tag::clazz[]
@Factory
class DiscoveryClients {

    @Singleton
    @Bean
    GreeterGrpc.GreeterStub greeterStub(
        @GrpcChannel("greeter")
        ManagedChannel channel) {
        return GreeterGrpc.newStub(
            channel
        );
    }
}
// end::clazz[]
