package com.example.services;

import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import jakarta.inject.Singleton;
import org.example.grpc.GreeterGrpc;
import org.example.grpc.HelloRequest;
import org.example.grpc.HelloResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GreeterService extends GreeterGrpc.GreeterImplBase {
    private static final Logger log = LoggerFactory.getLogger(GreeterService.class);

    @GrpcRestJsonExposed
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        log.debug("Received request: {}", request);
        String name = request.getName();
        String greeting = "Hello, " + name;

        HelloResponse reply = HelloResponse.newBuilder()
                .setGreeting(greeting)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
