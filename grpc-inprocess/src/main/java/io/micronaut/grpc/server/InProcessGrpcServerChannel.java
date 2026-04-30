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
package io.micronaut.grpc.server;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.TaskExecutors;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Provides an in-process managed channel for the embedded gRPC server.
 *
 * @since 5.0.0
 */
@Factory
@Requires(property = GrpcServerConfiguration.IN_PROCESS_NAME)
@Internal
public class InProcessGrpcServerChannel {

    /**
     * Constructs a managed in-process server channel.
     *
     * @param inProcessConfiguration The in-process server configuration
     * @param executorService The executor service
     * @param clientInterceptors The client interceptors
     * @return The channel
     */
    @Singleton
    @Named(GrpcServerChannel.NAME)
    @Requires(beans = GrpcEmbeddedServer.class)
    @Bean(preDestroy = "shutdown")
    @Replaces(value = ManagedChannel.class, factory = GrpcServerChannel.class, named = GrpcServerChannel.NAME)
    protected ManagedChannel serverChannel(GrpcInProcessServerConfiguration inProcessConfiguration,
                                           @Named(TaskExecutors.IO) ExecutorService executorService,
                                           List<ClientInterceptor> clientInterceptors) {
        ManagedChannelBuilder<?> builder = InProcessChannelBuilder.forName(
            inProcessConfiguration.getInProcessName().orElseThrow(IllegalStateException::new)
        ).executor(executorService);
        if (CollectionUtils.isNotEmpty(clientInterceptors)) {
            Collections.reverse(clientInterceptors);
            builder.intercept(clientInterceptors);
        }
        return builder.build();
    }
}
