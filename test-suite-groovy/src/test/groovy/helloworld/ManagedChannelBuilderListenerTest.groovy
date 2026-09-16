package helloworld

import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@Property(name = "spec.name", value = "ManagedChannelBuilderListenerTest")
@Property(name = "grpc.server.port", value = "50161")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50161")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class ManagedChannelBuilderListenerTest extends Specification {

    @Inject
    @Named("greeter")
    GreeterGrpc.GreeterBlockingStub greeterStub

    void "test max inbound message size"() {
        given: "a request whose reply is larger than the 1024 bytes the channel accepts"
        HelloRequest request = HelloRequest.newBuilder()
            .setName("x" * 2000)
            .build()

        when:
        greeterStub.sayHello(request)

        then:
        StatusRuntimeException ex = thrown()
        ex.status.code == Status.Code.RESOURCE_EXHAUSTED
    }

    // the "grpc-server" test channel bypasses the channel builders, so the test uses the named "greeter" channel
    @Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
    @Factory
    static class GreeterChannelClients {

        @Bean
        @Named("greeter")
        GreeterGrpc.GreeterBlockingStub greeterStub(@GrpcChannel("greeter") ManagedChannel channel) {
            GreeterGrpc.newBlockingStub(channel)
        }
    }
}
