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

import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.Named;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * A managed channel configuration.
 *
 * @author graemerocher
 * @since 1.0.0
 *
 */
public abstract class GrpcManagedChannelConfiguration implements Named {
    public static final String PREFIX = "grpc.channels";
    public static final String SETTING_TARGET = ".target";
    public static final String SETTING_URL = ".address";
    public static final String CONNECT_ON_STARTUP = ".connect-on-startup";
    public static final String CONNECTION_TIMEOUT = ".connection-timeout";
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    protected final String name;

    @ConfigurationBuilder(prefixes = {"use", ""}, allowZeroArgs = true)
    protected final NettyChannelBuilder channelBuilder;

    private final boolean connectOnStartup;
    private final Duration connectionTimeout;

    /**
     * Constructors a new managed channel configuration.
     * @param name The name
     * @param env The environment
     * @param executorService The executor service to use
     */
    public GrpcManagedChannelConfiguration(String name, Environment env, ExecutorService executorService) {
        this.name = name;
        this.connectOnStartup = env.getProperty(PREFIX + '.' + name + CONNECT_ON_STARTUP, Boolean.class).isPresent();
        this.connectionTimeout = env.getProperty(PREFIX + '.' + name + CONNECTION_TIMEOUT, Long.class)
            .filter(t -> t > 0)
            .map(Duration::ofSeconds)
            .orElse(DEFAULT_CONNECTION_TIMEOUT);
        final Optional<SocketAddress> socketAddress = env.getProperty(PREFIX + '.' + name + SETTING_URL, SocketAddress.class);
        if (socketAddress.isPresent()) {
            SocketAddress serverAddress = socketAddress.get();
            if (serverAddress instanceof InetSocketAddress) {
                InetSocketAddress isa = (InetSocketAddress) serverAddress;
                if (isa.isUnresolved()) {
                    isa = new InetSocketAddress(isa.getHostString(), isa.getPort());
                }
                this.channelBuilder = NettyChannelBuilder.forAddress(isa.getHostName(), isa.getPort());
            } else {
                this.channelBuilder = NettyChannelBuilder.forAddress(serverAddress);
            }
        } else {
            final Optional<String> target = env.getProperty(PREFIX + '.' + name + SETTING_TARGET, String.class);
            if (target.isPresent()) {
                this.channelBuilder = NettyChannelBuilder.forTarget(
                        target.get()
                );

            } else {
                final URI uri = name.contains("//") ? URI.create(name) : null;
                if (uri != null && uri.getHost() != null && uri.getPort() > -1) {
                    this.channelBuilder = NettyChannelBuilder.forAddress(uri.getHost(), uri.getPort());
                    if ("http".equalsIgnoreCase(uri.getScheme())) {
                        this.channelBuilder.usePlaintext();
                    }
                } else {
                    this.channelBuilder = NettyChannelBuilder.forTarget(name);
                }
            }
        }
        this.getChannelBuilder().executor(executorService);
    }

    /**
     * @return name of the channel
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return true if connect on startup is set for channel
     */
    public boolean isConnectOnStartup() {
        return connectOnStartup;
    }

    /**
     * @return connection timeout for the channel
     */
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * @return The channel builder.
     */
    public NettyChannelBuilder getChannelBuilder() {
        return channelBuilder;
    }
}
