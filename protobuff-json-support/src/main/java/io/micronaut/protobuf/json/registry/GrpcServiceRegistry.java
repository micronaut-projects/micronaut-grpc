package io.micronaut.protobuf.json.registry;

import io.micronaut.core.annotation.Experimental;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Experimental
public class GrpcServiceRegistry {
    private final Map<String, GrpcServiceMetadata> services = new ConcurrentHashMap<>();

    public void registerService(String name, GrpcServiceMetadata metadata) {
        services.put(name, metadata);
    }

    public Optional<GrpcServiceMetadata> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }
}
