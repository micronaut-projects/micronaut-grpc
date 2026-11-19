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
package io.micronaut.grpc.client.circuitbreaker;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.retry.CircuitBreakerOperations;
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.exception.CircuitOpenException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ClientInterceptor} that guards every call of a channel with a Micronaut circuit breaker.
 *
 * <p>A call that ends with one of the configured failure status codes counts as a failure. After
 * {@link GrpcCircuitBreakerConfiguration#failureThreshold()} consecutive failures the circuit opens and calls fail
 * immediately with {@link Status#UNAVAILABLE} without reaching the network. After the reset timeout the circuit is
 * half-open: a single trial call is let through, and closes the circuit if it succeeds or opens it again if it fails.
 * Other calls made while the trial is in flight fail fast. The circuit state is updated before the caller is notified
 * of a call's result.</p>
 *
 * <p>The interceptor never retries. Streaming calls cannot be replayed from an interceptor; configure retries with the
 * gRPC service config instead.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class GrpcCircuitBreakerInterceptor implements ClientInterceptor {

    private final GrpcCircuitBreakerConfiguration configuration;
    private final CircuitBreakerOperations operations;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean trialInFlight = new AtomicBoolean();

    /**
     * @param configuration The circuit breaker configuration
     * @param operations The circuit breaker operations, configured with a single attempt
     */
    GrpcCircuitBreakerInterceptor(GrpcCircuitBreakerConfiguration configuration, CircuitBreakerOperations operations) {
        this.configuration = configuration;
        this.operations = operations;
    }

    /**
     * @return The current state of the circuit
     */
    public CircuitState currentState() {
        return operations.currentState();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new CircuitBreakerCall<>(next.newCall(method, callOptions), callOptions.getExecutor());
    }

    /**
     * Records the outcome of a call.
     *
     * @param status The final status of the call
     * @param trial Whether the call was let through while the circuit was half-open
     * @return The failure to report to the circuit breaker, or {@code null} if the call does not open the circuit
     */
    private @Nullable Throwable recordOutcome(Status status, Metadata trailers, boolean trial) {
        if (!configuration.failureStatusCodes().contains(status.getCode())) {
            consecutiveFailures.set(0);
            return null;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (trial || failures >= configuration.failureThreshold()) {
            consecutiveFailures.set(0);
            return status.asRuntimeException(trailers);
        }
        return null;
    }

    private final class CircuitBreakerCall<ReqT, RespT> extends ForwardingClientCall<ReqT, RespT> {

        private final ClientCall<ReqT, RespT> delegate;
        @Nullable
        private final Executor callExecutor;
        private volatile boolean rejected;

        CircuitBreakerCall(ClientCall<ReqT, RespT> delegate, @Nullable Executor callExecutor) {
            this.delegate = delegate;
            this.callExecutor = callExecutor;
        }

        @Override
        protected ClientCall<ReqT, RespT> delegate() {
            return delegate;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            boolean trial = operations.currentState() == CircuitState.HALF_OPEN;
            if (trial && !trialInFlight.compareAndSet(false, true)) {
                // Only one trial call while half-open; the others fail fast until it completes.
                reject(responseListener, null);
                return;
            }
            try {
                operations.executeCompletionStage(() -> {
                    CompletableFuture<Void> outcome = new CompletableFuture<>();
                    delegate.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            // Update the circuit before the caller sees the result, so that a call the caller starts
                            // from its completion callback already observes the new state.
                            try {
                                Throwable failure = recordOutcome(status, trailers, trial);
                                if (failure == null) {
                                    outcome.complete(null);
                                } else {
                                    outcome.completeExceptionally(failure);
                                }
                            } finally {
                                if (trial) {
                                    trialInFlight.set(false);
                                }
                                super.onClose(status, trailers);
                            }
                        }
                    }, headers);
                    return outcome;
                });
            } catch (CircuitOpenException e) {
                if (trial) {
                    trialInFlight.set(false);
                }
                reject(responseListener, e);
            }
        }

        private void reject(Listener<RespT> responseListener, @Nullable CircuitOpenException cause) {
            rejected = true;
            Status status = Status.UNAVAILABLE
                .withDescription("Circuit breaker for gRPC channel [" + configuration.channelName() + "] is open")
                .withCause(cause);
            Runnable close = () -> responseListener.onClose(status, new Metadata());
            if (callExecutor != null) {
                callExecutor.execute(close);
            } else {
                close.run();
            }
        }

        @Override
        public void request(int numMessages) {
            if (!rejected) {
                super.request(numMessages);
            }
        }

        @Override
        public void cancel(@Nullable String message, @Nullable Throwable cause) {
            if (!rejected) {
                super.cancel(message, cause);
            }
        }

        @Override
        public void halfClose() {
            if (!rejected) {
                super.halfClose();
            }
        }

        @Override
        public void sendMessage(ReqT message) {
            if (!rejected) {
                super.sendMessage(message);
            }
        }

        @Override
        public void setMessageCompression(boolean enabled) {
            if (!rejected) {
                super.setMessageCompression(enabled);
            }
        }

        @Override
        public boolean isReady() {
            return !rejected && super.isReady();
        }
    }
}
