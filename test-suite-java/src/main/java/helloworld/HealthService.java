package helloworld;

// tag::imports[]
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import jakarta.inject.Singleton;
// end::imports[]

// tag::clazz[]
@Singleton
public class HealthService {

    private final HealthStatusManager healthStatusManager;

    public HealthService(@Nullable HealthStatusManager healthStatusManager) {
        this.healthStatusManager = healthStatusManager;
    }

    public void setStatus(@NonNull String serviceName, @NonNull HealthCheckResponse.ServingStatus status) {
        if (healthStatusManager != null) {
            healthStatusManager.setStatus(serviceName, status);
        }
    }
}
// end::clazz[]
