package io.micronaut.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.event.ServiceShutdownEvent;
import io.micronaut.discovery.event.ServiceStartedEvent;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.core.io.socket.SocketUtils.LOCALHOST;

@Singleton
@Requires(missingBeans = EmbeddedServer.class)
@Requires(classes = ServerBuilder.class)
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

    public GrpcEmbeddedServer(
            @Nonnull ApplicationContext applicationContext,
            @Nonnull ApplicationConfiguration applicationConfiguration,
            @Nonnull GrpcServerConfiguration grpcServerConfiguration,
            @Nonnull ServerBuilder<?> serverBuilder,
            @Nonnull ApplicationEventPublisher eventPublisher,
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
    public @Nonnull Server getServer() {
        return server;
    }

    /**
     * @return The configuration for the server
     */
    public @Nonnull GrpcServerConfiguration getServerConfiguration() {
        return grpcConfiguration;
    }

    @Override
    public int getPort() {
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
                            metadataContributors
                    );
                    applicationContext.publishEvent(new ServiceStartedEvent(serviceInstance));
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
                eventPublisher.publishEvent(new ServerShutdownEvent( this));
                if (serviceInstance != null) {
                    applicationContext.publishEvent(new ServiceShutdownEvent(serviceInstance));
                }
            } finally {
                server.shutdown();
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
