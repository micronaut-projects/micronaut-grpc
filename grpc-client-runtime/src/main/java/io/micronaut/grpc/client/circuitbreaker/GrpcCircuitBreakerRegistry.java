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

import io.grpc.ClientInterceptor;
import io.grpc.StatusRuntimeException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.retry.CircuitBreakerOperationsFactory;
import io.micronaut.retry.CircuitBreakerPolicy;
import io.micronaut.retry.CircuitState;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Holds one circuit breaker per gRPC channel that enables {@code grpc.channels.<name>.circuit-breaker.enabled}.
 * All stubs created from the same channel share its circuit breaker.
 *
 * <p>Requires {@code io.micronaut:micronaut-retry} on the classpath.</p>
 *
 * @since 5.3.0
 */
@Singleton
@Requires(classes = CircuitBreakerOperationsFactory.class)
public class GrpcCircuitBreakerRegistry {

    private final Environment environment;
    private final CircuitBreakerOperationsFactory operationsFactory;
    private final ExecutorService ioExecutor;
    private final Map<String, Optional<GrpcCircuitBreakerInterceptor>> interceptors = new ConcurrentHashMap<>();

    /**
     * @param environment The environment
     * @param operationsFactory The factory for circuit breaker operations
     * @param ioExecutor The executor that runs the callbacks of rejected calls that don't specify their own executor
     */
    public GrpcCircuitBreakerRegistry(Environment environment,
                                      CircuitBreakerOperationsFactory operationsFactory,
                                      @Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        this.environment = environment;
        this.operationsFactory = operationsFactory;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Returns the circuit breaker interceptor of a channel, if its circuit breaker is enabled.
     *
     * @param channelName The channel name
     * @return The interceptor
     */
    public Optional<ClientInterceptor> findInterceptor(String channelName) {
        return find(channelName).map(ClientInterceptor.class::cast);
    }

    /**
     * Returns the state of a channel's circuit breaker.
     *
     * @param channelName The channel name
     * @return The state, or empty if the channel has no circuit breaker
     */
    public Optional<CircuitState> currentState(String channelName) {
        return find(channelName).map(GrpcCircuitBreakerInterceptor::currentState);
    }

    private Optional<GrpcCircuitBreakerInterceptor> find(String channelName) {
        return interceptors.computeIfAbsent(channelName, name -> {
            GrpcCircuitBreakerConfiguration configuration = GrpcCircuitBreakerConfiguration.of(environment, name);
            if (!configuration.enabled()) {
                return Optional.empty();
            }
            // The interceptor tracks outcomes and never replays a call. maxAttempts counts retries, and its minimum is 1,
            // so a zero overall delay budget is what turns retries off while still letting a failure open the circuit.
            CircuitBreakerPolicy policy = CircuitBreakerPolicy.builder()
                .maxAttempts(1)
                .delay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .resetTimeout(configuration.resetTimeout())
                .capturedException(StatusRuntimeException.class)
                .throwWrappedException(true)
                .build();
            return Optional.of(new GrpcCircuitBreakerInterceptor(configuration, operationsFactory.createCircuitBreakerOperations(policy), ioExecutor));
        });
    }
}
