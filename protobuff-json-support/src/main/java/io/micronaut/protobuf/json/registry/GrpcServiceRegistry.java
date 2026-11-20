/*
 * Copyright 2017-2026 original authors
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

import io.grpc.MethodDescriptor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class responsible for maintaining a registry of gRPC service methods, enabling them
 * to be exposed and invoked via JSON over REST. The registry manages mappings between
 * service names, method names, and their corresponding {@link ExecutableMethod} instances.
 * <br/>
 * The {@code GrpcServiceRegistry} is primarily used to support the integration of gRPC
 * methods with REST-based clients by providing a centralized point for method registration
 * and lookup.
 * <br/>
 * Annotations:
 * - {@code @Singleton}: Indicates that this class is a singleton within the application context.
 * - {@code @Experimental}: Marks the class as experimental functionality, which is subject to
 *   change in future releases.
 * <br/>
 * Thread Safety:
 * - This class uses a thread-safe {@link ConcurrentHashMap} to store service and method mappings,
 *   ensuring safe registration and retrieval in concurrent environments.
 */
@Singleton
@Experimental
public class GrpcServiceRegistry {

    private final Map<String, Map<String, RegisteredMethod>> methods = new ConcurrentHashMap<>();

    /**
     * Registers a gRPC service method with the internal registry, allowing it to be exposed
     * for invocation via JSON over REST. This method maintains a mapping between the service
     * name, method name, and its corresponding {@code ExecutableMethod}.
     *
     * @param serviceBeanType The class type of the gRPC service bean being registered. Must not be null.
     * @param methodName The name of the method in the gRPC service being registered. Must not be null or empty.
     * @param method The {@code ExecutableMethod} representing the method's metadata and logic. Must not be null.
     * @param methodDescriptor The gRPC {@link MethodDescriptor} for the method. Must not be null.
     */
    public void register(Class<?> serviceBeanType,
                         String methodName,
                         ExecutableMethod<?, ?> method,
                         MethodDescriptor<?, ?> methodDescriptor) {
        methods.computeIfAbsent(serviceBeanType.getSimpleName(), key -> new ConcurrentHashMap<>())
            .put(methodName, new RegisteredMethod(method, methodDescriptor));
    }

    /**
     * Retrieves an {@code ExecutableMethod} instance based on the specified service name and method name.
     * This method is used to locate a registered gRPC method within the given service context.
     *
     * @param serviceName The name of the gRPC service containing the method. Must not be null or empty.
     * @param methodName The name of the method within the service to retrieve. Must not be null or empty.
     * @return An {@code Optional} containing the {@code ExecutableMethod} if found, or an empty {@code Optional}
     *         if no matching service or method is registered.
     */
    public Optional<ExecutableMethod<?, ?>> getExecutableMethod(String serviceName, String methodName) {
        return getRegisteredMethod(serviceName, methodName).map(RegisteredMethod::executableMethod);
    }

    /**
     * Retrieves a registered gRPC method entry based on the specified service and method name.
     *
     * @param serviceName The gRPC service containing the method.
     * @param methodName The gRPC method name.
     * @return The registered method entry if present.
     */
    public Optional<RegisteredMethod> getRegisteredMethod(String serviceName, String methodName) {
        return Optional.ofNullable(methods.getOrDefault(serviceName, Map.of()).get(methodName));
    }

    /**
     * Registered gRPC method metadata.
     *
     * @param executableMethod The Micronaut executable method.
     * @param methodDescriptor The gRPC method descriptor.
     */
    public record RegisteredMethod(ExecutableMethod<?, ?> executableMethod,
                                   MethodDescriptor<?, ?> methodDescriptor) {
    }
}
