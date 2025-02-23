package io.micronaut.protobuf.json;

import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class GrpcServiceRegistry {

    private final Map<String, ServiceDefinition> services = new ConcurrentHashMap<>();

    public void registerService(String name, Object serviceBean, Map<String, Method> methods) {
        services.put(name.toLowerCase(), new ServiceDefinition(serviceBean, methods));
    }

    public Optional<ServiceDefinition> getService(String name) {
        return Optional.ofNullable(services.get(name.toLowerCase()));
    }

    public static class ServiceDefinition {
        final Object serviceBean;
        final Map<String, Method> methods;

        public ServiceDefinition(Object serviceBean, Map<String, Method> methods) {
            this.serviceBean = serviceBean;
            this.methods = methods;
        }
    }
}



