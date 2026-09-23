package helloworld

// tag::imports[]
import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
// end::imports[]

// tag::test[]
@MicronautTest // <1>
class GreetingEndpointTest extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub // <2>

    void "test greeting endpoint"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().with { // <3>
            name = "Fred"
            build()
        }

        expect:
        blockingStub.sayHello(
                request
        ).message == 'Hello Fred'
    }
}
// end::test[]

// tag::clients[]
@Factory
class Clients {

    @Bean
    GreeterGrpc.GreeterBlockingStub blockingStub(
            @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) { // <1>
        GreeterGrpc.newBlockingStub( // <2>
                channel
        )
    }
}
// end::clients[]
