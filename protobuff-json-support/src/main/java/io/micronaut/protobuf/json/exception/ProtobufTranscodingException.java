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
