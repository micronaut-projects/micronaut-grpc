package io.micronaut.protobuf.json

import io.grpc.stub.StreamObserver
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse

@Singleton
@GrpcRestJsonExposed
class GreeterService extends GreeterGrpc.GreeterImplBase {

    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        def name = request.getName()
        def greeting = "Hello, $name"

        def reply = HelloResponse.newBuilder()
                .setGreeting(greeting)
                .build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
