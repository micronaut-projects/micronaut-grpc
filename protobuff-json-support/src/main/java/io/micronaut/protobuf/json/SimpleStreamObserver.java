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

import io.grpc.stub.StreamObserver;
import io.micronaut.core.annotation.Experimental;

/**
 * Simple StreamObserver implementation for handling gRPC responses.
 * @param <T> the type of response values this observer handles
 */
@Experimental
public class SimpleStreamObserver<T> implements StreamObserver<T> {
    private T response;
    private Throwable error;

    /**
     * Processes the next value from the stream and stores it as the response.
     *
     * This method is part of the {@code StreamObserver} lifecycle and is called
     * each time a new value is emitted by the gRPC stream. The received value
     * is stored and can be accessed later.
     *
     * @param value the value received from the gRPC stream
     */
    @Override
    public void onNext(T value) {
        response = value;
    }

    /**
     * Handles errors that occur during gRPC calls.
     *
     * This method is invoked when an error is encountered during the execution
     * of a gRPC call. The error is captured and stored for later retrieval or
     * processing.
     *
     * @param t the {@code Throwable} representing the error that occurred
     */
    @Override
    public void onError(Throwable t) {
        error = t;
    }

    /**
     * Notifies that the gRPC call has been completed successfully.
     *
     * This method is invoked when the server has successfully completed sending
     * all responses. It is part of the {@code StreamObserver} lifecycle and indicates
     * that no more data will be received.
     *
     * No action is needed in this implementation.
     */
    @Override
    public void onCompleted() {
        // No action needed
    }

    /**
     * Retrieves the response of a gRPC call if available.
     * If an error occurred during the gRPC call, a {@link GrpcInvocationException}
     * is thrown with the corresponding error details.
     *
     * @return the response of the gRPC call
     * @throws GrpcInvocationException if the gRPC call resulted in an error
     */
    public T getResponse() {
        if (error != null) {
            throw new GrpcInvocationException("gRPC call failed", error);
        }
        return response;
    }
}
