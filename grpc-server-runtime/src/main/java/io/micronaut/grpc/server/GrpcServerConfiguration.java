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
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.scheduling.TaskExecutors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.google.common.base.Preconditions;

/**
 * Configuration for the GRPC server.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0
 */
@ConfigurationProperties(GrpcServerConfiguration.PREFIX)
public class GrpcServerConfiguration {

    public static final String PREFIX = "grpc.server";
    public static final String PORT = PREFIX + ".port";
    public static final String HOST = PREFIX + ".host";
    public static final String ENABLED = PREFIX + ".enabled";
    public static final int DEFAULT_PORT = 50051;
    public static final Duration DEFAULT_AWAIT_TERMINATION = Duration.ofSeconds(30);

    @ConfigurationBuilder(prefixes = "", excludes = "protocolNegotiator")
    protected final NettyServerBuilder serverBuilder;
    private final int serverPort;
    private final String serverHost;
    private final ResourceResolver resourceResolver;
    private GrpcSslConfiguration serverConfiguration = new GrpcSslConfiguration();
    private boolean secure;
    private String instanceId = "";
    private Duration awaitTermination = DEFAULT_AWAIT_TERMINATION;

    /**
     * Default constructor.
     *
     * @param environment The environment
     * @param serverHost The server host
     * @param serverPort The server port
     * @param executorService The IO executor service
     * @param resourceResolver The resource resolver
     */
    public GrpcServerConfiguration(
        Environment environment,
        @Property(name = HOST) @Nullable String serverHost,
        @Property(name = PORT) @Nullable Integer serverPort,
        @Named(TaskExecutors.IO) ExecutorService executorService,
        ResourceResolver resourceResolver) {
        this.serverPort = serverPort != null ? serverPort :
            environment.getActiveNames().contains(Environment.TEST) ? SocketUtils.findAvailableTcpPort() : DEFAULT_PORT;
        this.serverHost = serverHost;
        if (serverHost != null) {
            this.serverBuilder = NettyServerBuilder.forAddress(
                new InetSocketAddress(serverHost, this.serverPort)
            );
        } else {
            this.serverBuilder = NettyServerBuilder.forPort(this.serverPort);
        }
        this.serverBuilder.executor(executorService);
        this.resourceResolver = resourceResolver;
    }

    /**
     * Whether SSL is used.
     *
     * @return True if SSL is used
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * The server builder.
     *
     * @return The {@link ServerBuilder}
     */
    public @NonNull ServerBuilder<?> getServerBuilder() {
        return serverBuilder;
    }

    /**
     * The server host.
     *
     * @return The server host
     */
    public Optional<String> getServerHost() {
        return Optional.ofNullable(this.serverHost);
    }

    /**
     * The server port.
     *
     * @return The server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * The instance id.
     *
     * @return The instance id
     */
    public @NonNull String getInstanceId() {
        return instanceId;
    }

    /**
     * Sets the instance id name used for registering the GRPC service in Service Discovery. If this is not set, the
     * application name will be used.
     *
     * @param instanceId The instance id
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Sets the maximum message size allowed to be received on the server. If not called,
     * defaults to 4 MiB. The default provides protection to servers who haven't considered the
     * possibility of receiving large messages while trying to be large enough to not be hit in normal
     * usage.
     *
     * <p>This method is advisory, and implementations may decide to not enforce this.  Currently,
     * the only known transport to not enforce this is {@code InProcessServer}.
     *
     * @param bytes the maximum number of bytes a single message can be.
     *
     * @throws IllegalArgumentException if bytes is negative.
     * @throws UnsupportedOperationException if unsupported.
     * @since 1.13.0
     */
    public void setMaxInboundMessageSize(@ReadableBytes int bytes) {
        // intentional noop rather than throw, this method is only advisory.
        Preconditions.checkArgument(bytes >= 0, "bytes must be >= 0");
        serverBuilder.maxInboundMessageSize(bytes);
    }

    /**
     * Sets the maximum size of metadata allowed to be received. {@code Integer.MAX_VALUE} disables
     * the enforcement. The default is implementation-dependent, but is not generally less than 8 KiB
     * and may be unlimited.
     *
     * <p>This is cumulative size of the metadata. The precise calculation is
     * implementation-dependent, but implementations are encouraged to follow the calculation used for
     * <a href="https://httpwg.org/specs/rfc7540.html#rfc.section.6.5.2">
     * HTTP/2's SETTINGS_MAX_HEADER_LIST_SIZE</a>. It sums the bytes from each entry's key and value,
     * plus 32 bytes of overhead per entry.
     *
     * @param bytes the maximum size of received metadata
     *
     * @throws IllegalArgumentException if bytes is non-positive
     * @since 1.17.0
     */
    public void setMaxInboundMetadataSize(@ReadableBytes int bytes) {
        Preconditions.checkArgument(bytes > 0, "maxInboundMetadataSize must be > 0");
        serverBuilder.maxInboundMetadataSize(bytes);
    }

    /**
     * The SSL configuration.
     *
     * @return The SSL configuration
     */
    public @NonNull GrpcSslConfiguration getServerConfiguration() {
        return serverConfiguration;
    }

    /**
     * Sets the maximum duration application will wait for the server to terminate and release all resources.
     *
     * @param awaitTermination The maximum duration the application will wait for the server to terminate.
     */
    public void setAwaitTermination(Duration awaitTermination) {
        this.awaitTermination = awaitTermination;
    }

    /**
     * Gets the maximum duration application will wait for the server to terminate and release all resources.
     *
     * @return The maximum duration the application will wait for the server to terminate.
     */
    public Duration getAwaitTermination() {
        return awaitTermination;
    }

    /**
     * Sets the SSL configuration.
     *
     * @param sslConfiguration The server configuration
     */
    @Inject
    public void setServerConfiguration(GrpcSslConfiguration sslConfiguration) {
        if (sslConfiguration != null) {
            this.serverConfiguration = sslConfiguration;

            if (resourceResolver != null) {
                final Optional<InputStream> certChain = sslConfiguration.getCertChain().flatMap(resourceResolver::getResourceAsStream);
                final Optional<InputStream> privateKey = sslConfiguration.getPrivateKey().flatMap(resourceResolver::getResourceAsStream);

                final boolean hasCert = certChain.isPresent();
                final boolean hasPrivateKey = privateKey.isPresent();
                if (hasCert && hasPrivateKey) {
                    try {
                        try (InputStream certStream = certChain.get()) {
                            try (InputStream keyStream = privateKey.get()) {
                                serverBuilder.useTransportSecurity(
                                    certStream,
                                    keyStream
                                );
                            }
                        }
                        this.secure = true;
                    } catch (IOException e) {
                        throw new ConfigurationException("Unable to configure SSL certificate: " + e.getMessage(), e);
                    }
                } else {
                    if (hasCert) {
                        try {
                            certChain.get().close();
                        } catch (IOException e) {
                            // ignore
                        }
                        throw new ConfigurationException("Both 'cert-chain' and 'private-key' properties should be configured");
                    } else if (hasPrivateKey) {
                        try {
                            privateKey.get().close();
                        } catch (IOException e) {
                            // ignore
                        }
                        throw new ConfigurationException("Both 'cert-chain' and 'private-key' properties should be configured");
                    }
                }
            }
        }
    }

}
