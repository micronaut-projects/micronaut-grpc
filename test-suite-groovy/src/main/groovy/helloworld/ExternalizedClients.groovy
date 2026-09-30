package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "ExternalizedClientsTest")
// tag::clazz[]
@CompileStatic
@Factory
class ExternalizedClients {

    @Singleton
    GreeterGrpc.GreeterStub greeterStub(
            @GrpcChannel('https://${my.server}:${my.port}')
            ManagedChannel channel) {
        GreeterGrpc.newStub(
                channel
        )
    }
}
// end::clazz[]
