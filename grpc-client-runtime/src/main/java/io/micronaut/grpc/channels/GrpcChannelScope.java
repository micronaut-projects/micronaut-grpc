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

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.ParametrizedProvider;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom scope for injecting {@link ManagedChannel} instances that are dependency injected and shutdown when
 * the application shuts down.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class GrpcChannelScope implements CustomScope<GrpcChannel>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcChannelScope.class);
    private final Map<ChannelKey, ManagedChannel> channels = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    /**
     * Default constructor.
     * @param applicationContext The application context
     */
    public GrpcChannelScope(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Class<GrpcChannel> annotationType() {
        return GrpcChannel.class;
    }

    @Override
    public <T> T get(
            BeanResolutionContext resolutionContext,
            BeanDefinition<T> beanDefinition,
            BeanIdentifier identifier,
            Provider<T> provider) {
        BeanResolutionContext.Segment segment = resolutionContext.getPath().currentSegment().orElseThrow(() ->
                new IllegalStateException("@GrpcChannel used in invalid location")
        );
        Argument argument = segment.getArgument();
        String value = argument.getAnnotationMetadata().getValue(GrpcChannel.class, String.class).orElse(null);
        if (StringUtils.isEmpty(value)) {
            throw new DependencyInjectionException(resolutionContext, argument, "No value specified to @GrpcChannel annotation");
        }
        if (!Channel.class.isAssignableFrom(argument.getType())) {
            throw new DependencyInjectionException(resolutionContext, argument, "@GrpcChannel used on type that is not a Channel");
        }

        if ("grpc-server".equalsIgnoreCase(value)) {
            return (T) applicationContext.getBean(ManagedChannel.class, Qualifiers.byName("grpc-server"));
        }

        if (!(provider instanceof ParametrizedProvider)) {
            throw new DependencyInjectionException(resolutionContext, argument, "GrpcChannelScope called with invalid bean provider");
        }
        value = applicationContext.resolveRequiredPlaceholders(value);
        String finalValue = value;
        return (T) channels.computeIfAbsent(new ChannelKey(identifier, value), channelKey ->
                (ManagedChannel) ((ParametrizedProvider<T>) provider).get(finalValue)
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        final Optional<ChannelKey> key = this.channels.keySet().stream().filter(k -> k.identifier.equals(identifier)).findFirst();
        if (key.isPresent()) {
            return key.map(channelKey -> (T) channels.remove(channelKey));
        }
        return Optional.empty();
    }

    @Override
    @PreDestroy
    public void close() {
        for (ManagedChannel channel : channels.values()) {
            if (!channel.isShutdown()) {
                try {
                    channel.shutdown();
                } catch (Exception e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Error shutting down GRPC channel: " + e.getMessage(), e);
                    }
                }
            }
        }
        channels.clear();
    }

    /**
     * Client key.
     */
    private static class ChannelKey {
        final BeanIdentifier identifier;
        final String value;

        public ChannelKey(BeanIdentifier identifier, String value) {
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
