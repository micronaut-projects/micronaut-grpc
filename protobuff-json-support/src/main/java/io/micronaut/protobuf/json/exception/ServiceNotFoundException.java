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
package io.micronaut.protobuf.json.exception;

import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.HttpStatus;

/**
 * Exception thrown when a requested service cannot be found.
 * <p>
 * This exception extends {@link HttpStatusException} and is specifically used
 * to signal a {@code NOT_FOUND} HTTP status when a gRPC service is not
 * available in the registry.
 * <p>
 * The exception message includes the name of the service that was not found.
 */
public class ServiceNotFoundException extends HttpStatusException {
    /**
     * Constructs a new ServiceNotFoundException with a detailed error message
     * indicating the service that could not be found.
     *
     * @param serviceName The name of the service that was not found. This value
     *                    will be included in the exception message to provide
     *                    context about the missing service.
     */
    public ServiceNotFoundException(String serviceName) {
        super(HttpStatus.NOT_FOUND, String.format("Service '%s' not found", serviceName));
    }
}
