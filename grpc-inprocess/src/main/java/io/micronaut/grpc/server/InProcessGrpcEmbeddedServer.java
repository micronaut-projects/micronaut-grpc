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

import java.net.URI;
import java.net.URL;
import java.util.List;

import io.grpc.ServerBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.ApplicationConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.io.socket.SocketUtils.LOCALHOST;

/**
 * Replaces the embedded server bean when the in-process transport is enabled.
 *
 * @since 5.0.0
 */
@Singleton
@Secondary
@Named(GrpcServerConfiguration.PREFIX)
@Replaces(GrpcEmbeddedServer.class)
@Requires(classes = {ServerBuilder.class, ServiceInstance.class})
@Requires(property = GrpcServerConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(property = GrpcServerConfiguration.IN_PROCESS_NAME)
@Internal
public class InProcessGrpcEmbeddedServer extends GrpcEmbeddedServer {

    private final GrpcInProcessServerConfiguration inProcessConfiguration;

    /**
     * @param applicationContext The application context
     * @param applicationConfiguration The application configuration
     * @param grpcServerConfiguration The gRPC server configuration
     * @param inProcessConfiguration The in-process server configuration
     * @param serverBuilder The server builder
     * @param eventPublisher The event publisher
     * @param computeInstanceMetadataResolver The computed instance metadata
     * @param metadataContributors The metadata contributors
     */
    @Internal
    InProcessGrpcEmbeddedServer(@NonNull ApplicationContext applicationContext,
                                @NonNull ApplicationConfiguration applicationConfiguration,
                                @NonNull GrpcServerConfiguration grpcServerConfiguration,
                                @NonNull GrpcInProcessServerConfiguration inProcessConfiguration,
                                @NonNull ServerBuilder<?> serverBuilder,
                                @NonNull ApplicationEventPublisher eventPublisher,
                                @Nullable ComputeInstanceMetadataResolver computeInstanceMetadataResolver,
                                @Nullable List<ServiceInstanceMetadataContributor> metadataContributors) {
        super(
            applicationContext,
            applicationConfiguration,
            grpcServerConfiguration,
            serverBuilder,
            eventPublisher,
            computeInstanceMetadataResolver,
            metadataContributors
        );
        this.inProcessConfiguration = inProcessConfiguration;
    }

    @Override
    public String getHost() {
        return inProcessConfiguration.getInProcessName().orElse(LOCALHOST);
    }

    @Override
    public String getScheme() {
        return "in-process";
    }

    @Override
    public URL getURL() {
        throw new UnsupportedOperationException("In-process gRPC server does not expose a URL");
    }

    @Override
    public URI getURI() {
        return URI.create(getScheme() + ":" + getHost());
    }
}
