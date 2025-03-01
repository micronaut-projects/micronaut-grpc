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

import io.micronaut.core.annotation.Experimental;

/**
 * Exception thrown when a gRPC service is not found.
 */
@Experimental
public class ServiceNotFoundException extends RuntimeException {
    /**
     * Constructs a new ServiceNotFoundException with the specific service name that was not found.
     *
     * @param serviceName The name of the gRPC service that could not be located.
     */
    public ServiceNotFoundException(String serviceName) {
        super(String.format("Service '%s' not found", serviceName));
    }
}
