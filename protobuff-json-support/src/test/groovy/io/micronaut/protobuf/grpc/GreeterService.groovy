package io.micronaut.protobuf.grpc

import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import io.micronaut.grpc.annotation.GrpcService
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@GrpcService
@Singleton
@Requires(env = "test")
class GreeterService extends GreeterGrpc.GreeterImplBase {
    private static final Logger log = LoggerFactory.getLogger(GreeterService.class)

    @GrpcRestJsonExposed
    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        log.info("Request {} happening", request)
        HelloResponse reply = HelloResponse.newBuilder()
                .setGreeting("TEST Hello, ${request.name}")
                .build()

        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
