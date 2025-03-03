/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.protobuf.json.registry;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Metadata for a registered gRPC service.
 */
@Experimental
public final class GrpcServiceMetadata {
    private final Object serviceBean;
    private final GrpcServiceType type;
    private final Map<String, Method> methods;

    /**
     * Constructs a new GrpcServiceMetadata instance.
     *
     * @param serviceBean The service bean instance representing the gRPC service.
     * @param type The type of the gRPC service, which determines whether it is blocking, async, or unknown.
     * @param methods A map containing the service methods, keyed by method name.
     */
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
