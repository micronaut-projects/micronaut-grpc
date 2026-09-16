from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Bean, Factory
from micronaut.grpc.annotation import GrpcChannel
from micronaut.grpc.server import GrpcServerChannel
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HealthService import HealthService

try:
    from io.grpc import ManagedChannel
    from io.grpc.health.v1 import HealthCheckRequest, HealthCheckResponse, HealthGrpc
    from io.grpc.protobuf.services import HealthStatusManager
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import ManagedChannel
    from grpc.health.v1 import HealthCheckRequest, HealthCheckResponse, HealthGrpc
    from grpc.protobuf.services import HealthStatusManager


@MicronautTest
class HealthCheckTest:

    health_stub: Annotated[HealthGrpc.HealthBlockingStub, Inject]
    health_service: Annotated[HealthService, Inject]

    @Test
    def test_health(self):
        assert self.health_stub.check(HealthCheckRequest.newBuilder().build()).getStatus() == HealthCheckResponse.ServingStatus.SERVING

        self.health_service.set_status(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, HealthCheckResponse.ServingStatus.NOT_SERVING)

        assert self.health_stub.check(HealthCheckRequest.newBuilder().build()).getStatus() == HealthCheckResponse.ServingStatus.NOT_SERVING


@Factory
class HealthClients:

    @Bean
    def health_stub(self, channel: Annotated[ManagedChannel, GrpcChannel(GrpcServerChannel.NAME)]) -> HealthGrpc.HealthBlockingStub:
        return HealthGrpc.newBlockingStub(channel)
