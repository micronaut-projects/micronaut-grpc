package helloworld;

import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.protobuf.services.HealthStatusManager;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.server.GrpcServerChannel;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class HealthCheckTest {

    @Inject
    HealthGrpc.HealthBlockingStub healthStub;

    @Inject
    HealthService healthService;

    @Test
    void testHealth() {
        assertEquals(
            HealthCheckResponse.ServingStatus.SERVING,
            healthStub.check(HealthCheckRequest.newBuilder().build()).getStatus()
        );

        healthService.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, HealthCheckResponse.ServingStatus.NOT_SERVING);

        assertEquals(
            HealthCheckResponse.ServingStatus.NOT_SERVING,
            healthStub.check(HealthCheckRequest.newBuilder().build()).getStatus()
        );
    }

    @Factory
    static class HealthClients {

        @Bean
        HealthGrpc.HealthBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
            return HealthGrpc.newBlockingStub(channel);
        }
    }
}
