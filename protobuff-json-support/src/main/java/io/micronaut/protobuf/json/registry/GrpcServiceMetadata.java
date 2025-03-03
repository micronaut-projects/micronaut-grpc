package io.micronaut.protobuf.json.registry;

import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Metadata for a registered gRPC service.
 */
public class GrpcServiceMetadata {
    private final Object serviceBean;
    private final GrpcServiceType type;
    private final Map<String, Method> methods;

    public GrpcServiceMetadata(
            @NonNull Object serviceBean,
            @NonNull GrpcServiceType type,
            @NonNull Map<String, Method> methods) {
        this.serviceBean = serviceBean;
        this.type = type;
        this.methods = methods;
    }

    @NonNull
    public Object getServiceBean() {
        return serviceBean;
    }

    @NonNull
    public GrpcServiceType getType() {
        return type;
    }

    @NonNull
    public Method getMethod(String name) {
        return methods.get(name);
    }
}
