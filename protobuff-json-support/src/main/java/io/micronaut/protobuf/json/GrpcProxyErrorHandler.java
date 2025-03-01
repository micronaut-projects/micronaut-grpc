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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles creation of error responses for the gRPC JSON proxy.
 */
@Singleton
@Experimental
public class GrpcProxyErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProxyErrorHandler.class);

    /**
     * Creates a JSON error response for various error scenarios.
     *
     * @param status The HTTP status code
     * @param message The error message
     * @param error The exception (optional)
     * @return HTTP response with JSON error details
     */
    @NonNull
    public HttpResponse<String> createErrorResponse(
            @NonNull HttpStatus status,
            @NonNull String message,
            Throwable error) {
        if (error != null) {
            LOG.error(message, error);
        } else {
            LOG.error(message);
        }

        String jsonError = createErrorJson(message);
        return HttpResponse.status(status).body(jsonError);
    }

    /**
     * Creates a not found error response.
     *
     * @param entityType The type of entity not found (e.g., "Service", "Method")
     * @param entityName The name of the entity that wasn't found
     * @return HTTP response with not found error
     */
    @NonNull
    public HttpResponse<String> createNotFoundResponse(
            @NonNull String entityType,
            @NonNull String entityName) {
        String message = String.format("%s '%s' not found", entityType, entityName);
        return createErrorResponse(HttpStatus.NOT_FOUND, message, null);
    }

    /**
     * Creates a bad request error response for invalid input.
     *
     * @param message The error message
     * @param error The exception that caused the error
     * @return HTTP response with bad request error
     */
    @NonNull
    public HttpResponse<String> createBadRequestResponse(
            @NonNull String message,
            @NonNull Throwable error) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, message, error);
    }

    /**
     * Creates a server error response.
     *
     * @param message The error message
     * @param error The exception that caused the error
     * @return HTTP response with server error
     */
    @NonNull
    public HttpResponse<String> createServerErrorResponse(
            @NonNull String message,
            @NonNull Throwable error) {
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, error);
    }

    private String createErrorJson(@NonNull String message) {
        return String.format("{\"error\":\"%s\"}",
                message.replace("\"", "\\\""));
    }
}
