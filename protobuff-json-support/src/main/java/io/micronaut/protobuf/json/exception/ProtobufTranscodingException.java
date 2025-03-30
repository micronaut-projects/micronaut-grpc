package io.micronaut.protobuf.json.exception;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Custom exception for Protobuf transcoding errors.
 */
public class ProtobufTranscodingException extends HttpStatusException {

    /**
     * Constructs a new {@code ProtobufTranscodingException} with a detailed error
     * message and the underlying cause of the exception.
     * <br>
     * This exception is typically thrown when a failure occurs during Protobuf-to-JSON
     * transcoding, often due to serialization issues or invalid input data.
     *
     * @param message A detailed message explaining the cause of the exception.
     * @param e       The underlying throwable that triggered this exception.
     */
    public ProtobufTranscodingException(String message, Throwable e) {
        super(HttpStatus.BAD_REQUEST, String.format("Message: [%s]  Json[%s]", message,
            e.getMessage()));
    }
}
