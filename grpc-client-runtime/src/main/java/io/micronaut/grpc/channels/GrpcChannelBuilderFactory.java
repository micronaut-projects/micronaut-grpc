/*
 * Copyright 2017-2019 original authors
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;

import jakarta.inject.Named;

/**
 * Factory class for creating {@link NettyChannelBuilder} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcChannelBuilderFactory {

    private static final String EXECUTOR = ".executor";

    private final ApplicationContext beanContext;
    private final ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param executorService The I/O executor service
     */
    public GrpcChannelBuilderFactory(
        ApplicationContext beanContext,
        @Named(TaskExecutors.IO) ExecutorService executorService) {
        this.beanContext = beanContext;
        this.executorService = executorService;
    }

    /**
     * Constructor a managed channel build for the given target name and interceptors.
     *
     * @param target The target name
     * @param interceptors The interceptors
     *
     * @return The channel builder
     */
    @Bean
    @Prototype
    protected NettyChannelBuilder managedChannelBuilder(@Parameter String target, List<ClientInterceptor> interceptors) {
        GrpcManagedChannelConfiguration config = beanContext.findBean(GrpcManagedChannelConfiguration.class, Qualifiers.byName(target)).orElseGet(() -> {
                final GrpcDefaultManagedChannelConfiguration mcc = new GrpcDefaultManagedChannelConfiguration(
                    target,
                    beanContext.getEnvironment(),
                    executorService
                );
                beanContext.inject(mcc);
                return mcc;
            }
        );
        final NettyChannelBuilder channelBuilder = config.getChannelBuilder();
        resolveExecutor(config.getName()).ifPresent(channelBuilder::executor);
        if (CollectionUtils.isNotEmpty(interceptors)) {
            Collections.reverse(interceptors);
            channelBuilder.intercept(interceptors);
        }
        return channelBuilder;
    }

    /**
     * Resolves the executor configured by {@code grpc.channels.<name>.executor}, or by {@code grpc.client.executor} for
     * every channel. The value is the name of an {@link Executor} bean, or an {@link Executor} instance supplied by a
     * programmatic property source. Without either, the channel keeps the I/O executor.
     *
     * @param channelName The name of the matched channel configuration
     * @return The executor, if one is configured
     */
    private Optional<Executor> resolveExecutor(String channelName) {
        Environment environment = beanContext.getEnvironment();
        return environment.getProperty(GrpcManagedChannelConfiguration.PREFIX + '.' + channelName + EXECUTOR, Object.class)
            .or(() -> environment.getProperty(GrpcDefaultManagedChannelConfiguration.PREFIX + EXECUTOR, Object.class))
            .flatMap(value -> toExecutor(value, channelName));
    }

    private Optional<Executor> toExecutor(Object value, String channelName) {
        if (value instanceof Executor executor) {
            return Optional.of(executor);
        }
        String name = value.toString();
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        return Optional.of(beanContext.findBean(Executor.class, Qualifiers.byName(name))
            .orElseThrow(() -> new ConfigurationException("No executor bean named [" + name + "] is available for gRPC channel [" + channelName + "]")));
    }
}
