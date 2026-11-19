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

import io.grpc.Status;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.grpc.channels.GrpcManagedChannelConfiguration;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Circuit breaker settings of a gRPC channel, read from {@code grpc.channels.<name>.circuit-breaker.*}.
 *
 * @param channelName The channel name
 * @param enabled Whether the circuit breaker is enabled for the channel
 * @param failureThreshold The number of consecutive failed calls that opens the circuit
 * @param resetTimeout How long the circuit stays open before a trial call is let through
 * @param failureStatusCodes The status codes that count as failures
 * @since 5.3.0
 */
public record GrpcCircuitBreakerConfiguration(@NonNull String channelName,
                                              boolean enabled,
                                              int failureThreshold,
                                              @NonNull Duration resetTimeout,
                                              @NonNull Set<Status.Code> failureStatusCodes) {

    /**
     * The property suffix under a channel's prefix.
     */
    public static final String PREFIX = "circuit-breaker";

    /**
     * The default number of consecutive failures that opens the circuit.
     */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /**
     * The default time the circuit stays open.
     */
    public static final Duration DEFAULT_RESET_TIMEOUT = Duration.ofSeconds(20);

    /**
     * The status codes that count as failures by default: those that indicate the server is unreachable or overloaded,
     * rather than an application error.
     */
    public static final Set<Status.Code> DEFAULT_FAILURE_STATUS_CODES = Set.copyOf(EnumSet.of(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED));

    /**
     * Validates the configuration.
     */
    public GrpcCircuitBreakerConfiguration {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("grpc.channels." + channelName + ".circuit-breaker.failure-threshold must be at least 1");
        }
        if (resetTimeout.isZero() || resetTimeout.isNegative()) {
            throw new IllegalArgumentException("grpc.channels." + channelName + ".circuit-breaker.reset-timeout must be greater than 0");
        }
        failureStatusCodes = Set.copyOf(failureStatusCodes);
    }

    /**
     * Reads the circuit breaker settings of a channel from the environment.
     *
     * @param environment The environment
     * @param channelName The channel name
     * @return The configuration
     */
    public static @NonNull GrpcCircuitBreakerConfiguration of(@NonNull Environment environment, @NonNull String channelName) {
        String prefix = GrpcManagedChannelConfiguration.PREFIX + '.' + channelName + '.' + PREFIX + '.';
        boolean enabled = environment.getProperty(prefix + "enabled", Boolean.class, false);
        int threshold = environment.getProperty(prefix + "failure-threshold", Integer.class, DEFAULT_FAILURE_THRESHOLD);
        Duration resetTimeout = environment.getProperty(prefix + "reset-timeout", Duration.class, DEFAULT_RESET_TIMEOUT);
        Set<Status.Code> codes = environment.getProperty(prefix + "failure-status-codes", Argument.listOf(String.class))
            .filter(list -> !list.isEmpty())
            .map(GrpcCircuitBreakerConfiguration::toCodes)
            .orElse(DEFAULT_FAILURE_STATUS_CODES);
        return new GrpcCircuitBreakerConfiguration(channelName, enabled, threshold, resetTimeout, codes);
    }

    private static Set<Status.Code> toCodes(List<String> names) {
        EnumSet<Status.Code> codes = EnumSet.noneOf(Status.Code.class);
        for (String name : names) {
            codes.add(Status.Code.valueOf(name.trim().toUpperCase(Locale.ENGLISH).replace('-', '_')));
        }
        return codes;
    }
}
