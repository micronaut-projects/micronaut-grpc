package com.example.services

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.micronaut.context.annotation.Property
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@Property(name = "grpc.server.port", value = "{random.port}") // Random port
class GreeterServiceSpec extends Specification {

    @GrpcChannel(GrpcServerChannel.NAME)
    @Inject
    ManagedChannel channel

    @Shared
    GreeterGrpc.GreeterBlockingStub blockingStub

    void setup() {
        blockingStub = GreeterGrpc.newBlockingStub(channel)
    }

    void "test sayHello returns correct greeting"() {
        when:
        def response = blockingStub.sayHello(
                HelloRequest.newBuilder().setName("John").build()
        )

        then:
        response.greeting == "Hello, John"
    }


    void cleanup() {
        channel?.shutdown()
    }
}
