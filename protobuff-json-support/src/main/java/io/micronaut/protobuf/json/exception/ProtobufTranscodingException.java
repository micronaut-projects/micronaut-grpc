package io.micronaut.protobuf.json.exception;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Custom exception for Protobuf transcoding errors.
 */
public class ProtobufTranscodingException extends HttpStatusException {
    /**
     * Constructs a new instance of ProtobufTranscodingException with a specified error message
     * and the cause of the exception.
     *
     * @param message The detail message for this exception, providing information about the error.
     */
    public ProtobufTranscodingException(String message, String json) {
        super(HttpStatus.BAD_REQUEST, String.format("Message: [%s]  Json[%s]", message, json));
    }

    public ProtobufTranscodingException(String message, Throwable e) {
        super(HttpStatus.BAD_REQUEST, String.format("Message: [%s]  Json[%s]", message,
            e.getMessage()));
    }
}
