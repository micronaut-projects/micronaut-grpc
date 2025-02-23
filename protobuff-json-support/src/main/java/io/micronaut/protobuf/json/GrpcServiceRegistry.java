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
package io.micronaut.protobuf.json;

import jakarta.inject.Singleton;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for managing gRPC services and their associated method definitions within
 * a Micronaut application. This class allows dynamic registration and retrieval of
 * gRPC service definitions, enabling services to be invoked dynamically at runtime.
 *<br/>
 * This registry associates a service name with a {@code ServiceDefinition}, which
 * encapsulates the service bean and a map of the available methods for that service.
 *<br/>
 * Annotations:
 * - {@code @Singleton}: Ensures a single instance of this registry is used within the application.
 */
@Singleton
public class GrpcServiceRegistry {

    private final Map<String, ServiceDefinition> services = new ConcurrentHashMap<>();

    /**
     * Registers a gRPC service by associating it with a service name and its corresponding
     * service bean and methods.
     *
     * @param name The name of the gRPC service to register. This value is case-insensitive
     *             and will be converted to lowercase for storage.
     * @param serviceBean The service bean instance that implements the gRPC service being
     *                    registered. This object is used to invoke the associated methods.
     * @param methods A map of method names to their corresponding {@code Method} objects.
     *                This defines the available methods for the service and allows dynamic
     *                invocation based on method name.
     */
    public void registerService(String name, Object serviceBean, Map<String, Method> methods) {
        services.put(name.toLowerCase(), new ServiceDefinition(serviceBean, methods));
    }

    /**
     * Retrieves the {@code ServiceDefinition} associated with the given service name, if it exists.
     * This method performs a case-insensitive lookup for the service name in the registered services.
     *
     * @param name The name of the gRPC service to retrieve. This value is case-insensitive.
     *             It will be converted to lowercase for the lookup.
     * @return An {@code Optional} containing the {@code ServiceDefinition} if the service is found,
     *         or an empty {@code Optional} if the service is not registered.
     */
    public Optional<ServiceDefinition> getService(String name) {
        return Optional.ofNullable(services.get(name.toLowerCase()));
    }

    /**
     * Represents a gRPC service definition containing the service bean instance and its
     * associated methods. This class provides the necessary encapsulation for managing
     * gRPC services, enabling dynamic invocation of service methods.
     *<br/>
     * A `ServiceDefinition` object is created with a reference to the service bean that
     * implements the gRPC service logic, as well as a mapping of method names to their
     * corresponding {@code Method} objects. This structure allows for flexible and dynamic
     * handling of gRPC service methods at runtime.
     *<br/>
     * Constructors:
     * - {@link #ServiceDefinition(Object, Map)}: Initializes the service definition with
     *   the service bean and method map.
     *<br/>
     * Fields:
     * - `serviceBean`: The instance of the service bean associated with this service
     *   definition. This object is the implementation of the gRPC service.
     * - `methods`: A map of method names to corresponding {@code Method} objects. Each
     *   entry in this map represents an accessible service method for the associated gRPC
     *   service.
     */
    public static class ServiceDefinition {
        final Object serviceBean;
        final Map<String, Method> methods;

        /**
         * Constructs a new ServiceDefinition instance with the specified service bean and method mapping.
         *
         * @param serviceBean The service bean instance representing the gRPC service logic. Must not be null.
         * @param methods A map of method names (as keys) to their corresponding {@code Method} objects (as values),
         *                representing the accessible methods for the service. Must not be null.
         */
        public ServiceDefinition(Object serviceBean, Map<String, Method> methods) {
            this.serviceBean = serviceBean;
            this.methods = methods;
        }
    }
}



