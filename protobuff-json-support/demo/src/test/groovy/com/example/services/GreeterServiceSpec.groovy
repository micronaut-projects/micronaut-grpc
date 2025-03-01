package com.example.services

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import spock.lang.Specification

@MicronautTest
@Property(name = "grpc.server.port", value = "{random.port}") // Random port
class GreeterServiceSpec extends Specification {

    @Inject
    ManagedChannel channel

    void "test sayHello returns correct greeting"() {
        given: "a gRPC stub"
        def stub = GreeterGrpc.newBlockingStub(channel)

        and: "a request with a name"
        def request = HelloRequest.newBuilder()
                .setName("John")
                .build()

        when: "we call the service"
        def response = stub.sayHello(request)

        then: "we get the expected greeting"
        response.greeting == "Hello, John"
    }

    void cleanup() {
        channel?.shutdown()
    }
}
