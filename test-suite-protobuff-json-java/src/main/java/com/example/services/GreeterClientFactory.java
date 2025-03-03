package com.example.services;

import io.grpc.ManagedChannel;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.grpc.server.GrpcServerChannel;
import jakarta.inject.Singleton;
import org.example.grpc.GreeterGrpc;

@Factory
public class GreeterClientFactory {

    @Bean
    @GrpcRestJsonExposed
    @Singleton
    GreeterGrpc.GreeterBlockingStub createBlockingStub(@GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
        return GreeterGrpc.newBlockingStub(channel);
    }
}
