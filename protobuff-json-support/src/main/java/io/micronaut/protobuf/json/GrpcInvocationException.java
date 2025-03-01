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
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.HttpStatus;

/**
 * Exception indicating a failure during the invocation of a gRPC method.
 * <br>
 * This exception is thrown when an error occurs while processing a gRPC request,
 * typically due to issues such as method invocation failures or unexpected runtime
 * exceptions. It extends {@code HttpStatusException} and is associated with the
 * {@code HttpStatus.INTERNAL_SERVER_ERROR} status.
 * <br>
 * The {@code GrpcInvocationException} is marked as {@code @Experimental}, indicating
 * that its API may change in future releases.
 */
@Experimental
public class GrpcInvocationException extends HttpStatusException {
    /**
     * Constructs a new {@code GrpcInvocationException} with the specified detail message
     * and cause. This exception is used to indicate a failure during the invocation of a
     * gRPC method in the server logic.
     *
     * @param message The detail message explaining the reason for the exception. Must not be null.
     * @param cause The underlying cause of the exception. May be null to indicate that the cause is unknown.
     */
    public GrpcInvocationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
