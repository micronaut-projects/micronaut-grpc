package org.example.grpc

import io.grpc.stub.StreamObserver
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import jakarta.inject.Singleton

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
