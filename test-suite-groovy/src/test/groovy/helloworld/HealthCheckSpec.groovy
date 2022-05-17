package helloworld

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.protobuf.services.HealthStatusManager
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class HealthCheckSpec extends Specification {

    @Inject
    HealthGrpc.HealthBlockingStub healthStub

    @Inject
    HealthService healthService

    void "test health check"() {
        when:
        def result = healthStub.check(HealthCheckRequest.newBuilder().build())

        then:
        result.status == HealthCheckResponse.ServingStatus.SERVING

        when:
        healthService.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, HealthCheckResponse.ServingStatus.NOT_SERVING)
        result = healthStub.check(HealthCheckRequest.newBuilder().build())

        then:
        result.status == HealthCheckResponse.ServingStatus.NOT_SERVING
    }

    @Factory
    static class Clients {

        @Singleton
        HealthGrpc.HealthBlockingStub healthStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            HealthGrpc.newBlockingStub(channel)
        }
    }
}
