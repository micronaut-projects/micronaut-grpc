package helloworld;

// tag::imports[]
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import jakarta.inject.Singleton;
// end::imports[]

// tag::clazz[]
@Singleton
public class HealthService {

    private final HealthStatusManager healthStatusManager;

    public HealthService(@Nullable HealthStatusManager healthStatusManager) {
        this.healthStatusManager = healthStatusManager;
    }

    public void setStatus(@NonNull String serviceName, HealthCheckResponse.@NonNull ServingStatus status) {
        if (healthStatusManager != null) {
            healthStatusManager.setStatus(serviceName, status);
        }
    }
}
// end::clazz[]
