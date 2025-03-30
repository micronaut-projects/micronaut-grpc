package io.micronaut.protobuf.json

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.micronaut.protobuf.grpc.GreeterService
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.util.concurrent.TimeUnit

@MicronautTest(environments = ["test"])
@Stepwise
class GrpcGreeterServiceIntegrationSpec extends Specification {

    @Inject Server grpcServer
    @Inject GrpcServiceRegistry registry

    @Shared ManagedChannel channel

    def setup() {
        if (!channel || channel.isShutdown()) {
            channel = ManagedChannelBuilder.forAddress("localhost", grpcServer.port)
                    .usePlaintext()
                    .build()
        }
    }

    def cleanupSpec() {
        if (channel && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    def "Verify GrpcServer and GreeterService beans exist in application context"() {
        expect:
        grpcServer
        registry
        grpcServer.port > 0
    }

    def "Verify Greeter method is exposed via GrpcServiceRegistry"() {
        given:
        def serviceName = 'GreeterService'
        def methodName = "sayHello"

        when:
        def optionalMethod = registry.getExecutableMethod(serviceName, methodName)

        then:
        optionalMethod.isPresent()
    }

    def "Perform actual gRPC request to mocked GreeterService via Grpc stub"() {
        given: "a stub connected via dynamic port"
        def stub = GreeterGrpc.newBlockingStub(channel)

        and: "properly built HelloRequest explicitly mocked"
        def request = HelloRequest.newBuilder().setName("Micronaut Test").build()

        when: "making an explicit real gRPC request"
        def response = stub.sayHello(request)

        then: "correct explicitly mocked greeting is returned"
        response.getGreeting() == "TEST Hello, Micronaut Test"
    }

    @SuppressWarnings('unused')
    @Singleton
    private static Server grpcServer(GreeterService greeterService) {
        def server = ServerBuilder.forPort(0)
                .addService(greeterService)
                .build()
        server.start()
        return server
    }


}
