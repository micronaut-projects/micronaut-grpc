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
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for managing gRPC services and their associated metadata.
 *<br/>
 * This class provides functionality to register gRPC services along with their metadata
 * and retrieve metadata for registered services by name. The metadata contains details
 * about the service type, its bean instance, and its methods.
 */
@Singleton
@Experimental
public class GrpcServiceRegistry {
    private final Map<String, GrpcServiceMetadata> services = new ConcurrentHashMap<>();

    /**
     * Registers a gRPC service with its associated metadata.
     *
     * @param name The name of the gRPC service to be registered.
     * @param metadata The metadata associated with the gRPC service, including service type and methods.
     */
    public void registerService(String name, GrpcServiceMetadata metadata) {
        services.put(name, metadata);
    }

    /**
     * Retrieves the metadata for a registered gRPC service by its name.
     *
     * @param name The name of the gRPC service to retrieve metadata for.
     * @return An {@code Optional} containing the {@code GrpcServiceMetadata} if the service is found,
     *         or an empty {@code Optional} if no service is registered with the given name.
     */
    public Optional<GrpcServiceMetadata> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }
}
