package io.micronaut.protobuf.json.exception;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Exception thrown to indicate that a gRPC JSON payload is malformed or invalid.
 * <br/>
 * This exception extends {@code HttpStatusException} and can be used to signal a specific
 * HTTP status and provide additional context about the malformed JSON payload. It is typically
 * associated with HTTP client or server interactions where invalid JSON content related to gRPC
 * transcoding is encountered.
 */
public class MalformedGrpcJsonException extends HttpStatusException {
    /**
     * @param status The {@link HttpStatus}
     * @param body   The arbitrary object to return
     */
    public MalformedGrpcJsonException(HttpStatus status, Object body) {
        super(status, body);
    }
}
