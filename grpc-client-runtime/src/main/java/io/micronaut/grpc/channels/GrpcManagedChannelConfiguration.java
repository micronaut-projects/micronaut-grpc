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
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.Named;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
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
    protected final String name;

    @ConfigurationBuilder(prefixes = {"use", ""}, allowZeroArgs = true)
    protected final NettyChannelBuilder channelBuilder;
    private boolean resolveName = true;

    /**
     * Constructors a new managed channel configuration.
     * @param name The name
     * @param env The environment
     * @param executorService The executor service to use
     */
    public GrpcManagedChannelConfiguration(String name, Environment env, ExecutorService executorService) {
        this.name = name;
        final Optional<SocketAddress> socketAddress = env.getProperty(PREFIX + '.' + name + SETTING_URL, SocketAddress.class);
        if (socketAddress.isPresent()) {
            resolveName = false;
            SocketAddress serverAddress = socketAddress.get();
            if (serverAddress instanceof InetSocketAddress) {
                InetSocketAddress isa = (InetSocketAddress) serverAddress;
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
                    resolveName = false;
                    this.channelBuilder = NettyChannelBuilder.forAddress(uri.getHost(), uri.getPort());
                } else {
                    this.channelBuilder = NettyChannelBuilder.forTarget(name);
                }
            }
        }
        this.getChannelBuilder().executor(executorService);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return The channel builder.
     */
    public NettyChannelBuilder getChannelBuilder() {
        return channelBuilder;
    }

    /**
     * Sets the name resolver factor to use.
     * @param factory The factory
     */
    public void setNameResolverFactory(@Nullable NameResolver.Factory factory) {
        if (factory != null && resolveName) {
            channelBuilder.nameResolverFactory(factory);
        }
    }
}
