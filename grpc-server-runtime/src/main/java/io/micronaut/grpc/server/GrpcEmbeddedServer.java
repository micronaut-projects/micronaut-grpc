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
package io.micronaut.grpc.server;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.event.ServiceReadyEvent;
import io.micronaut.discovery.event.ServiceStoppedEvent;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.io.socket.SocketUtils.LOCALHOST;

/**
 * Implementation of the {@link EmbeddedServer} interface for GRPC.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Secondary
@Named(GrpcServerConfiguration.PREFIX)
@Requires(classes = ServerBuilder.class)
@Requires(property = GrpcServerConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class GrpcEmbeddedServer implements EmbeddedServer {

    private final ApplicationContext applicationContext;
    private final ApplicationConfiguration configuration;
    private final Server server;
    private final GrpcServerConfiguration grpcConfiguration;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ComputeInstanceMetadataResolver computeInstanceMetadataResolver;
    private final List<ServiceInstanceMetadataContributor> metadataContributors;
    private ServiceInstance serviceInstance;

    /**
     * Default constructor.
     *
     * @param applicationContext The application context
     * @param applicationConfiguration The application configuration
     * @param grpcServerConfiguration The GRPC server configuration
     * @param serverBuilder The server builder
     * @param eventPublisher The event publisher
     * @param computeInstanceMetadataResolver The computed instance metadata
     * @param metadataContributors The metadata contributors
     */
    @Internal
    GrpcEmbeddedServer(
        @NonNull ApplicationContext applicationContext,
        @NonNull ApplicationConfiguration applicationConfiguration,
        @NonNull GrpcServerConfiguration grpcServerConfiguration,
        @NonNull ServerBuilder<?> serverBuilder,
        @NonNull ApplicationEventPublisher eventPublisher,
        @Nullable ComputeInstanceMetadataResolver computeInstanceMetadataResolver,
        @Nullable List<ServiceInstanceMetadataContributor> metadataContributors) {
        ArgumentUtils.requireNonNull("applicationContext", applicationContext);
        ArgumentUtils.requireNonNull("applicationConfiguration", applicationConfiguration);
        ArgumentUtils.requireNonNull("grpcServerConfiguration", grpcServerConfiguration);
        this.applicationContext = applicationContext;
        this.configuration = applicationConfiguration;
        this.grpcConfiguration = grpcServerConfiguration;
        this.eventPublisher = eventPublisher;
        this.server = serverBuilder.build();
        this.computeInstanceMetadataResolver = computeInstanceMetadataResolver;
        this.metadataContributors = metadataContributors;
    }

    /**
     * @return The underlying GRPC {@link Server}.
     */
    public @NonNull Server getServer() {
        return server;
    }

    /**
     * @return The configuration for the server
     */
    public @NonNull GrpcServerConfiguration getServerConfiguration() {
        return grpcConfiguration;
    }

    @Override
    public int getPort() {
        // support eager init
        if (!isRunning()) {
            start();
        }
        return server.getPort();
    }

    @Override
    public String getHost() {
        return grpcConfiguration.getServerHost().orElse(LOCALHOST);
    }

    @Override
    public String getScheme() {
        return grpcConfiguration.isSecure() ? "https" : "http";
    }

    @Override
    public URL getURL() {
        try {
            return getURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Server URI: " + getURI());
        }
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public URI getURI() {
        return URI.create(getScheme() + "://" + getHost() + ":" + getPort());
    }

    @Override
    public EmbeddedServer start() {
        if (running.compareAndSet(false, true)) {

            try {
                server.start();
                eventPublisher.publishEvent(new ServerStartupEvent(this));
                getApplicationConfiguration().getName().ifPresent(id -> {

                    Map<String, String> metadata = new LinkedHashMap<>();
                    if (computeInstanceMetadataResolver != null) {
                        final Optional<ComputeInstanceMetadata> cim = computeInstanceMetadataResolver.resolve(
                            applicationContext.getEnvironment()
                        );

                        cim.ifPresent(computeInstanceMetadata -> metadata.putAll(computeInstanceMetadata.getMetadata()));

                    }

                    this.serviceInstance = new GrpcServerInstance(
                        this,
                        id,
                        getURI(),
                        metadata,
                        metadataContributors,
                        grpcConfiguration
                    );
                    applicationContext.publishEvent(new ServiceReadyEvent(serviceInstance));
                });
            } catch (IOException e) {
                throw new ApplicationStartupException("Unable to start GRPC server: " + e.getMessage(), e);
            }
        }
        return this;
    }

    @Override
    public EmbeddedServer stop() {
        if (running.compareAndSet(true, false)) {

            try {
                eventPublisher.publishEvent(new ServerShutdownEvent(this));
                if (serviceInstance != null) {
                    applicationContext.publishEvent(new ServiceStoppedEvent(serviceInstance));
                }
            } finally {
                server.shutdown();
                try {
                    server.awaitTermination(grpcConfiguration.getAwaitTermination().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

        }
        return this;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public ApplicationConfiguration getApplicationConfiguration() {
        return this.configuration;
    }

    @Override
    public boolean isRunning() {
        return running.get() && !server.isTerminated();
    }
}
