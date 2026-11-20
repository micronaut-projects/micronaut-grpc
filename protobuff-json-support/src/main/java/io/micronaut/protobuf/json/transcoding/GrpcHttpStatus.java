/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.protobuf.json.transcoding;

import io.grpc.Status;
import io.micronaut.core.annotation.Internal;

/**
 * Maps gRPC status codes to HTTP status codes, following {@code google/rpc/code.proto}.
 *
 * @since 5.3.0
 */
@Internal
final class GrpcHttpStatus {

    private GrpcHttpStatus() {
    }

    /**
     * @param code The gRPC status code
     * @return The HTTP status code
     */
    static int of(Status.Code code) {
        return switch (code) {
            case OK -> 200;
            case CANCELLED -> 499;
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE -> 400;
            case DEADLINE_EXCEEDED -> 504;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS, ABORTED -> 409;
            case PERMISSION_DENIED -> 403;
            case UNAUTHENTICATED -> 401;
            case RESOURCE_EXHAUSTED -> 429;
            case UNIMPLEMENTED -> 501;
            case UNAVAILABLE -> 503;
            case UNKNOWN, INTERNAL, DATA_LOSS -> 500;
        };
    }
}
