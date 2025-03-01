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

import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.HttpStatus;

/**
 * Exception thrown to indicate that a specified method could not be found within
 * a given context, such as a gRPC service.
 *<br/>
 * This exception extends {@link HttpStatusException} and is typically used
 * to signal a {@link HttpStatus#NOT_FOUND} (404) response when the desired
 * method is unavailable or does not exist in the provided service or registry.
 *<br/>
 * The exception message provides detailed information about the missing method,
 * including its name.
 */
public class MethodNotFoundException extends HttpStatusException {
    /**
     * Constructs a new {@code MethodNotFoundException} with a detailed message indicating
     * the method name that could not be found.
     * <br/>
     * This exception is typically thrown when a requested gRPC method is not registered
     * in the {@code GrpcServiceRegistry}.
     *
     * @param methodName The name of the method that could not be found. Must not be null.
     */
    public MethodNotFoundException(String methodName) {
        super(HttpStatus.NOT_FOUND, String.format("Method '%s' not found", methodName));
    }
}
