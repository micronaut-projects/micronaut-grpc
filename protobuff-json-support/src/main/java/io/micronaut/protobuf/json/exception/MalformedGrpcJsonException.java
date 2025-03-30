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
