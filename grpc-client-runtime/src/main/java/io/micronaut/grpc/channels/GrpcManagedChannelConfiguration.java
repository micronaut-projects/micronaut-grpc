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
package io.micronaut.grpc.channels;

import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.Named;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

/**
 * A managed channel configuration.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class GrpcManagedChannelConfiguration implements Named {

    public static final String PREFIX = "grpc.channels";
    public static final String SETTING_TARGET = ".target";
    public static final String SETTING_URL = ".address";
    public static final String CONNECT_ON_STARTUP = ".connect-on-startup";
    public static final String CONNECTION_TIMEOUT = ".connection-timeout";
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    protected final String name;

    @ConfigurationBuilder(prefixes = {"use", ""}, allowZeroArgs = true, excludes = {"defaultServiceConfig"})
    protected final NettyChannelBuilder channelBuilder;

    private final boolean connectOnStartup;
    private final Duration connectionTimeout;

    /**
     * Constructors a new managed channel configuration.
     *
     * @param name            The name
     * @param env             The environment
     * @param executorService The executor service to use
     */
    protected GrpcManagedChannelConfiguration(String name, String propertyPrefix, Environment env, ExecutorService executorService) {
        this.name = name;
        this.connectOnStartup = env.getProperty(propertyPrefix + CONNECT_ON_STARTUP, Boolean.class).isPresent();
        this.connectionTimeout = env.getProperty(propertyPrefix + CONNECTION_TIMEOUT, Long.class)
            .filter(t -> t > 0)
            .map(Duration::ofSeconds)
            .orElse(DEFAULT_CONNECTION_TIMEOUT);

        this.channelBuilder = env.getProperty(propertyPrefix + SETTING_URL, SocketAddress.class)
            .map(this::getChannelBuilder)
            .orElseGet(() -> env.getProperty(propertyPrefix + SETTING_TARGET, String.class)
                .map(NettyChannelBuilder::forTarget)
                .orElseGet(() -> {
                    final URI uri = name.contains("//") ? URI.create(name) : null;
                    if (uri != null && uri.getHost() != null && uri.getPort() > -1) {
                        NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(uri.getHost(), uri.getPort());
                        if ("http".equalsIgnoreCase(uri.getScheme())) {
                            nettyChannelBuilder.usePlaintext();
                        }
                        return nettyChannelBuilder;
                    } else {
                        return NettyChannelBuilder.forTarget(name);
                    }
                })
            );
        this.getChannelBuilder().executor(executorService);
    }

    private NettyChannelBuilder getChannelBuilder(SocketAddress serverAddress) {
        if (serverAddress instanceof InetSocketAddress isa) {
            return NettyChannelBuilder.forTarget(formatTarget(isa.getHostString(), isa.getPort()));
        } else {
            return NettyChannelBuilder.forAddress(serverAddress);
        }
    }

    static String formatTarget(String host, int port) {
        if (host.indexOf(':') > -1 && !(host.startsWith("[") && host.endsWith("]"))) {
            return '[' + host + "]:" + port;
        }
        return host + ':' + port;
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
     * @since 3.4.0
     */
    public boolean isConnectOnStartup() {
        return connectOnStartup;
    }

    /**
     * @return connection timeout for the channel
     * @since 3.4.0
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

    /**
     * Applies the gRPC default service config using Micronaut's nested/raw map binding.
     *
     * @param serviceConfig The service config map
     */
    public void setDefaultServiceConfig(
        @MapFormat(transformation = MapFormat.MapTransformation.NESTED, keyFormat = StringConvention.RAW)
        Map<String, Object> serviceConfig) {
        if (serviceConfig != null) {
            channelBuilder.defaultServiceConfig(normalizeServiceConfig(serviceConfig));
        }
    }

    private Map<String, ?> normalizeServiceConfig(Map<String, Object> serviceConfig) {
        Map<String, Object> normalized = new LinkedHashMap<>(serviceConfig.size());
        for (Map.Entry<String, Object> entry : serviceConfig.entrySet()) {
            normalized.put(entry.getKey(), normalizeServiceConfigValue(entry.getValue()));
        }
        return normalized;
    }

    private Object normalizeServiceConfigValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeServiceConfigValue(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object entry : list) {
                normalized.add(normalizeServiceConfigValue(entry));
            }
            return normalized;
        }
        if (value instanceof Number number && !(value instanceof Double)) {
            return number.doubleValue();
        }
        return value;
    }
}
