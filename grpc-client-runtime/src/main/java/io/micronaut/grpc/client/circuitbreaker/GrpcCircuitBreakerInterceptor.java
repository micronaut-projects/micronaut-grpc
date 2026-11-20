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

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.CircuitBreakerOperations;
import io.micronaut.retry.CircuitState;
import io.micronaut.retry.exception.CircuitOpenException;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Completes outcomes of calls admitted before the circuit last opened. It is not a
     * {@code StatusRuntimeException}, so Micronaut Retry ignores it and leaves the circuit state untouched.
     */
    private static final RuntimeException STALE_OUTCOME = new StaleOutcomeException();

    private final GrpcCircuitBreakerConfiguration configuration;
    private final CircuitBreakerOperations operations;
    private final Executor fallbackExecutor;
    /**
     * Marks calls that this interceptor already guards, so that registering it twice on the same channel builder does
     * not count a call twice or reject its own trial call.
     */
    private final CallOptions.Key<Boolean> guarded;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean trialInFlight = new AtomicBoolean();
    /**
     * Incremented each time a failure is reported, that is each time the circuit opens.
     */
    private final AtomicLong openGeneration = new AtomicLong();

    /**
     * @param configuration The circuit breaker configuration
     * @param operations The circuit breaker operations, configured without retries
     * @param fallbackExecutor The channel's callback executor, used for rejected calls that don't specify their own executor
     */
    GrpcCircuitBreakerInterceptor(GrpcCircuitBreakerConfiguration configuration,
                                  CircuitBreakerOperations operations,
                                  Executor fallbackExecutor) {
        this.configuration = configuration;
        this.operations = operations;
        this.fallbackExecutor = fallbackExecutor;
        this.guarded = CallOptions.Key.create("micronaut-grpc-circuit-breaker-" + configuration.channelName());
    }

    /**
     * @return The current state of the circuit
     */
    public CircuitState currentState() {
        CircuitState state = operations.currentState();
        if (state == CircuitState.OPEN) {
            // CircuitBreakerRetry.currentState() returns the previous state on the call that moves the circuit from
            // OPEN to HALF_OPEN, so read it again to see the state the circuit is now in.
            state = operations.currentState();
        }
        return state;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        if (Boolean.TRUE.equals(callOptions.getOption(guarded))) {
            return next.newCall(method, callOptions);
        }
        return new CircuitBreakerCall<>(method, callOptions.withOption(guarded, Boolean.TRUE), next, Context.current());
    }

    /**
     * Records the outcome of a call.
     *
     * @param status The final status of the call
     * @param trailers The trailers of the call
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
            openGeneration.incrementAndGet();
            return status.asRuntimeException(trailers);
        }
        return null;
    }

    /**
     * A call that is admitted by the circuit breaker when it starts. The downstream call is only created once the call
     * is admitted, so rejected calls never reach the channel.
     *
     * @param <ReqT> The request type
     * @param <RespT> The response type
     */
    private final class CircuitBreakerCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

        private final MethodDescriptor<ReqT, RespT> method;
        private final CallOptions callOptions;
        private final Channel next;
        private final Context context;
        private final AtomicReference<@Nullable ClientCall<ReqT, RespT>> delegate = new AtomicReference<>();
        private volatile boolean rejected;
        private final AtomicReference<@Nullable Status> cancelledBeforeStart = new AtomicReference<>();

        CircuitBreakerCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next, Context context) {
            this.method = method;
            this.callOptions = callOptions;
            this.next = next;
            this.context = context;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            Status cancelled = cancelledBeforeStart.get();
            if (cancelled != null) {
                rejected = true;
                close(responseListener, cancelled);
                return;
            }
            boolean trial = currentState() == CircuitState.HALF_OPEN;
            if (trial && !trialInFlight.compareAndSet(false, true)) {
                // Only one trial call while half-open; the others fail fast until it completes.
                reject(responseListener, null);
                return;
            }
            long generation = openGeneration.get();
            CompletableFuture<Void> outcome = new CompletableFuture<>();
            try {
                // The supplier only hands over the outcome. The call starts after Micronaut Retry has attached its
                // completion handler, so even a call that closes inside start() updates the circuit first.
                operations.executeCompletionStage(() -> outcome);
            } catch (CircuitOpenException e) {
                if (trial) {
                    trialInFlight.set(false);
                }
                reject(responseListener, e);
                return;
            }
            ClientCall<ReqT, RespT> call = newDownstreamCall();
            delegate.set(call);
            call.start(new OutcomeListener<>(responseListener, outcome, trial, generation), headers);
        }

        private ClientCall<ReqT, RespT> newDownstreamCall() {
            Context previous = context.attach();
            try {
                return next.newCall(method, callOptions);
            } finally {
                context.detach(previous);
            }
        }

        private void reject(Listener<RespT> responseListener, @Nullable CircuitOpenException cause) {
            rejected = true;
            Status status = Status.UNAVAILABLE
                .withDescription("Circuit breaker for gRPC channel [" + configuration.channelName() + "] is open")
                .withCause(cause);
            close(responseListener, status);
        }

        private void close(Listener<RespT> responseListener, Status status) {
            Executor executor = callOptions.getExecutor();
            // Like ClientCallImpl, deliver the callback in the gRPC context the call was created in.
            (executor != null ? executor : fallbackExecutor).execute(context.wrap(() -> responseListener.onClose(status, new Metadata())));
        }

        @Override
        public void request(int numMessages) {
            ClientCall<ReqT, RespT> call = delegate.get();
            if (call != null) {
                call.request(numMessages);
            } else if (!rejected) {
                throw new IllegalStateException("Not started");
            }
        }

        @Override
        public void cancel(@Nullable String message, @Nullable Throwable cause) {
            ClientCall<ReqT, RespT> call = delegate.get();
            if (call != null) {
                call.cancel(message, cause);
            } else if (!rejected) {
                Status status = Status.CANCELLED;
                if (message != null) {
                    status = status.withDescription(message);
                }
                if (cause != null) {
                    status = status.withCause(cause);
                }
                cancelledBeforeStart.set(status);
            }
        }

        @Override
        public void halfClose() {
            ClientCall<ReqT, RespT> call = delegate.get();
            if (call != null) {
                call.halfClose();
            } else if (!rejected) {
                throw new IllegalStateException("Not started");
            }
        }

        @Override
        public void sendMessage(ReqT message) {
            ClientCall<ReqT, RespT> call = delegate.get();
            if (call != null) {
                call.sendMessage(message);
            } else if (!rejected) {
                throw new IllegalStateException("Not started");
            }
        }

        @Override
        public void setMessageCompression(boolean enabled) {
            ClientCall<ReqT, RespT> call = delegate.get();
            if (call != null) {
                call.setMessageCompression(enabled);
            }
        }

        @Override
        public boolean isReady() {
            ClientCall<ReqT, RespT> call = delegate.get();
            return call != null && call.isReady();
        }

        @Override
        public Attributes getAttributes() {
            ClientCall<ReqT, RespT> call = delegate.get();
            return call != null ? call.getAttributes() : Attributes.EMPTY;
        }
    }

    /**
     * Reports the outcome of an admitted call to the circuit breaker before notifying the caller.
     *
     * @param <RespT> The response type
     */
    private final class OutcomeListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private final CompletableFuture<Void> outcome;
        private final boolean trial;
        private final long generation;

        OutcomeListener(ClientCall.Listener<RespT> delegate, CompletableFuture<Void> outcome, boolean trial, long generation) {
            super(delegate);
            this.outcome = outcome;
            this.trial = trial;
            this.generation = generation;
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            try {
                if (!trial && generation != openGeneration.get()) {
                    // Admitted before the circuit last opened: the result says nothing about the server now, and must
                    // not decide a trial that is in flight.
                    outcome.completeExceptionally(STALE_OUTCOME);
                } else {
                    Throwable failure = recordOutcome(status, trailers, trial);
                    if (failure == null) {
                        outcome.complete(null);
                    } else {
                        outcome.completeExceptionally(failure);
                    }
                }
            } finally {
                if (trial) {
                    trialInFlight.set(false);
                }
                super.onClose(status, trailers);
            }
        }
    }

    /**
     * Marks the outcome of a call admitted before the circuit last opened.
     */
    private static final class StaleOutcomeException extends RuntimeException {
        StaleOutcomeException() {
            super("Outcome of a call admitted before the circuit last opened", null, false, false);
        }
    }
}
