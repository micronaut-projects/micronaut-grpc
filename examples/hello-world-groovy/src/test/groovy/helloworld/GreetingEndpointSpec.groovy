package helloworld

import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Singleton

@MicronautTest
class GreetingEndpointSpec extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub

    void "test greeting endpoint"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().with {
            name = "Fred"
            build()
        }

        expect:
        blockingStub.sayHello(
               request
        ).message == 'Hello Fred'
    }

    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(
                @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
            GreeterGrpc.newBlockingStub(
                    channel
            )
        }
    }
}

