package helloworld

// tag::imports[]
import io.grpc.health.v1.HealthCheckResponse.ServingStatus
import io.grpc.protobuf.services.HealthStatusManager
import jakarta.inject.Singleton

// end::imports[]

// tag::clazz[]
@Singleton
class HealthService(private val healthStatusManager: HealthStatusManager?) {

    fun setStatus(serviceName: String, status: ServingStatus) {
        healthStatusManager?.setStatus(serviceName, status)
    }
}
// end::clazz[]
