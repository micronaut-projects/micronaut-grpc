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
package io.micronaut.grpc.channels;

import java.util.Optional;
import java.util.concurrent.Executor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

/**
 * Resolves the callback executor configured for a gRPC channel.
 *
 * @since 5.3.0
 */
@Internal
public final class GrpcChannelExecutors {

    private static final String EXECUTOR = ".executor";

    private GrpcChannelExecutors() {
    }

    /**
     * Resolves the executor configured by {@code grpc.channels.<name>.executor}, or by {@code grpc.client.executor} for
     * every channel. The value is the name of an {@link Executor} bean, or an {@link Executor} instance supplied by a
     * programmatic property source. Without either, the channel keeps the I/O executor.
     *
     * @param context The application context
     * @param channelName The name of the matched channel configuration
     * @return The executor, if one is configured
     */
    public static Optional<Executor> resolve(ApplicationContext context, String channelName) {
        Environment environment = context.getEnvironment();
        return environment.getProperty(GrpcManagedChannelConfiguration.PREFIX + '.' + channelName + EXECUTOR, Object.class)
            .or(() -> environment.getProperty(GrpcDefaultManagedChannelConfiguration.PREFIX + EXECUTOR, Object.class))
            .flatMap(value -> toExecutor(context, value, channelName));
    }

    private static Optional<Executor> toExecutor(ApplicationContext context, Object value, String channelName) {
        if (value instanceof Executor executor) {
            return Optional.of(executor);
        }
        String name = value.toString();
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        return Optional.of(context.findBean(Executor.class, Qualifiers.byName(name))
            .orElseThrow(() -> new ConfigurationException("No executor bean named [" + name + "] is available for gRPC channel [" + channelName + "]")));
    }
}
