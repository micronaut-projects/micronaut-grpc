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

import io.grpc.NameResolver;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.env.Environment;
import io.micronaut.scheduling.TaskExecutors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ExecutorService;

/**
 * Default configuration for all GRPC clients.
 *
 * @author graemerocher
 * @since 1.0
 */
@Named(GrpcDefaultManagedChannelConfiguration.NAME)
@ConfigurationProperties(GrpcDefaultManagedChannelConfiguration.PREFIX)
@Primary
public class GrpcDefaultManagedChannelConfiguration extends GrpcManagedChannelConfiguration {
    public static final String NAME = "default";
    public static final String PREFIX = "grpc.client";

    /**
     * Default constructor.
     * @param name The name
     * @param env The environment
     * @param executorService The executor service
     */
    public GrpcDefaultManagedChannelConfiguration(
            String name,
            Environment env,
            @Named(TaskExecutors.IO) ExecutorService executorService) {
        super(name, env, executorService);
    }

    @Override
    @Inject
    @Deprecated
    public void setNameResolverFactory(@Nullable NameResolver.Factory factory) {
        super.setNameResolverFactory(factory);
    }
}
