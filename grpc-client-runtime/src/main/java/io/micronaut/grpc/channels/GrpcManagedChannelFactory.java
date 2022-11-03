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

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory bean for creating {@link ManagedChannel} instances.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class GrpcManagedChannelFactory implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcManagedChannelFactory.class);
    private final Map<ChannelKey, ManagedChannel> channels = new ConcurrentHashMap<>();
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
     * @param injectionPoint The injection point
     * @return The channel
     */
    @Bean
    @Primary
    protected ManagedChannel managedChannel(InjectionPoint<Channel> injectionPoint) {
        Argument<?> argument;
        if (injectionPoint instanceof FieldInjectionPoint) {
            argument = ((FieldInjectionPoint<?, ?>) injectionPoint).asArgument();
        } else if (injectionPoint instanceof ArgumentInjectionPoint) {
            argument = ((ArgumentInjectionPoint<?, ?>) injectionPoint).getArgument();
        } else {
            throw new ConfigurationException("Cannot directly create channels use @Inject or constructor injection instead");
        }

        String target = argument.getAnnotationMetadata().stringValue(GrpcChannel.class).orElse(null);
        if (StringUtils.isEmpty(target)) {
            throw new ConfigurationException("No value specified to @GrpcChannel annotation: " + injectionPoint);
        }

        if ("grpc-server".equalsIgnoreCase(target)) {
            return beanContext.getBean(ManagedChannel.class, Qualifiers.byName("grpc-server"));
        }


        return channels.computeIfAbsent(new ChannelKey(argument, target), channelKey -> {
            final NettyChannelBuilder nettyChannelBuilder = beanContext.createBean(NettyChannelBuilder.class, target);
            ManagedChannel channel = nettyChannelBuilder.build();
            beanContext.findBean(GrpcNamedManagedChannelConfiguration.class, Qualifiers.byName(target))
                .ifPresent(channelConfig -> {
                    if (channelConfig.isConnectOnStartup()) {
                        LOG.debug("Connecting to the channel: {}", target);
                        if (!connectOnStartup(channel, channelConfig.getConnectionTimeout())) {
                            throw new IllegalStateException("Unable to connect to the channel: " + target);
                        }
                        LOG.debug("Successfully connected to the channel: {}", target);
                    }
                });
            return channel;
        });
    }

    private boolean connectOnStartup(ManagedChannel channel, Duration timeout) {
        channel.getState(true); // request connection
        final CountDownLatch readyLatch = new CountDownLatch(1);
        waitForReady(channel, readyLatch);
        try {
            return readyLatch.await(timeout.getSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void waitForReady(ManagedChannel channel, CountDownLatch readyLatch) {
        final ConnectivityState state = channel.getState(false);
        if (state == ConnectivityState.READY) {
            readyLatch.countDown();
        } else {
            channel.notifyWhenStateChanged(state, () -> waitForReady(channel, readyLatch));
        }
    }

    @Override
    @PreDestroy
    public void close() {
        for (ManagedChannel channel : channels.values()) {
            if (!channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Error shutting down GRPC channel: {}", e.getMessage(), e);
                    }
                }
            }
        }
        channels.clear();
    }

    /**
     * Client key.
     */
    private static final class ChannelKey {
        final Argument<?> identifier;
        final String value;

        public ChannelKey(Argument<?> identifier, String value) {
            this.identifier = identifier;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ChannelKey clientKey = (ChannelKey) o;
            return Objects.equals(identifier, clientKey.identifier) &&
                    Objects.equals(value, clientKey.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, value);
        }
    }
}
