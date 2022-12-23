package helloworld

import io.grpc.Channel
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.protobuf.services.HealthStatusManager
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class HealthCheckTest {

    @Inject
    lateinit var healthStub: HealthGrpc.HealthBlockingStub

    @Inject
    lateinit var healthService: HealthService

    @Test
    fun testHealthCheck() = runBlocking {
        Assertions.assertEquals(
                HealthCheckResponse.ServingStatus.SERVING,
                healthStub.check(HealthCheckRequest.newBuilder().build()).status
        )

        healthService.setStatus(
                HealthStatusManager.SERVICE_NAME_ALL_SERVICES,
                HealthCheckResponse.ServingStatus.NOT_SERVING
        )

        Assertions.assertEquals(
                HealthCheckResponse.ServingStatus.NOT_SERVING,
                healthStub.check(HealthCheckRequest.newBuilder().build()).status
        )
    }
}

@Factory
class HealthClient {

    @Singleton
    fun healthClient(@GrpcChannel(GrpcServerChannel.NAME) channel: Channel): HealthGrpc.HealthBlockingStub =
            HealthGrpc.newBlockingStub(channel)
}
