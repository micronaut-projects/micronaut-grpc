# tag::imports[]
from jakarta.inject import Singleton

try:
    from io.grpc.health.v1 import HealthCheckResponse
    from io.grpc.protobuf.services import HealthStatusManager
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc.health.v1 import HealthCheckResponse
    from grpc.protobuf.services import HealthStatusManager
# end::imports[]


# tag::clazz[]
@Singleton
class HealthService:

    def __init__(self, health_status_manager: HealthStatusManager | None):
        self.health_status_manager = health_status_manager

    def set_status(self, service_name: str, status: HealthCheckResponse.ServingStatus) -> None:
        if self.health_status_manager is not None:
            self.health_status_manager.setStatus(service_name, status)
# end::clazz[]
