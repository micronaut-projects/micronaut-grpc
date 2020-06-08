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

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;

/**
 * Factory bean for creating {@link ManagedChannel} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcManagedChannelFactory {

    private final ApplicationContext beanContext;

    /**
     * Default constructor.
     * @param beanContext The bean context
     */
    public GrpcManagedChannelFactory(ApplicationContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * Builds a managed channel for the given target.
     * @param target The target
     * @return The channel
     */
    @Bean
    @Primary
    protected ManagedChannel managedChannel(@Parameter String target) {
        final NettyChannelBuilder nettyChannelBuilder = beanContext.createBean(NettyChannelBuilder.class, target);
        return nettyChannelBuilder.build();
    }
}
