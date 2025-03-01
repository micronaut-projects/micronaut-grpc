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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A global exception handler for handling {@link HttpStatusException}s in a gRPC-based
 * Micronaut application. This class provides a centralized mechanism to handle exceptions
 * that occur during the processing of HTTP requests, ensuring exceptions are logged and
 * appropriate HTTP responses are returned.
 *<br/>
 * The handler implements the {@link ExceptionHandler} interface, allowing it to intercept
 * exceptions of type {@link HttpStatusException} and generate custom HTTP responses.
 *<br/>
 * Annotations:<br/>
 * <ul>
 * <li> {@code @Produces}: Indicates this class can produce objects for injection.</li>
 * <li> {@code @Singleton}: Specifies that a single instance of this handler will exist within the application context.</li>
 * <li> {@code @Requires}: Ensures this handler is only loaded if the specified classes are
 * available at runtime.</li>
 * <li> {@code @Experimental}: Marks this class as experimental and subject to change in future
 * versions.</li>
 *</ul>
 * <br/>
 * Features:
 * <ul>
 * <li>Logs the error message and the root cause if available.</li>
 * <li>Constructs an HTTP response with the status code from the exception and includes
 *   an error message in the response body.</li>
 * </ul>
 *<br/>
 * Logging:<br/>
 * - Errors are logged using SLF4J {@link Logger}, and the log includes the error message
 *   and the root cause of the exception if present.
 *<br/>
 * Usage:<br/>
 * This class is intended to be used internally by the Micronaut framework for handling
 * {@link HttpStatusException}s. It ensures consistent error representations and provides
 * helpful debugging logs for developers.
 */
@Produces
@Singleton
@Requires(classes = {HttpStatusException.class, ExceptionHandler.class})
@Experimental
public class GrpcGlobalExceptionHandler implements ExceptionHandler<HttpStatusException, HttpResponse<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcGlobalExceptionHandler.class);

    /**
     * Handles a given HTTP request and an associated {@link HttpStatusException}, logs the error,
     * and returns an appropriate HTTP response containing the error message and status code.
     *
     * @param request The HTTP request that resulted in the exception. Must not be null.
     * @param exception The {@link HttpStatusException} that occurred during request processing. Must not be null.
     * @return An {@link HttpResponse} containing the HTTP status from the exception and a response body with an error message.
     */
    @Override
    public HttpResponse<?> handle(HttpRequest request, HttpStatusException exception) {
        if (exception.getCause() != null) {
            LOG.error(exception.getMessage(), exception.getCause());
        } else {
            LOG.error(exception.getMessage());
        }

        return HttpResponse
            .status(exception.getStatus())
            .body(Map.of("error", exception.getMessage()));
    }
}
