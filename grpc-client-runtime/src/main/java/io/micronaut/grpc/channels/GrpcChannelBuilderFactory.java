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
import java.util.concurrent.ExecutorService;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.util.CollectionUtils;
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
        if (CollectionUtils.isNotEmpty(interceptors)) {
            Collections.reverse(interceptors);
            channelBuilder.intercept(interceptors);
        }
        return channelBuilder;
    }
}
