package io.micronaut.grpc.channels;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.grpc.annotation.GrpcChannel;
import io.micronaut.grpc.server.GrpcServerChannel;
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

@Singleton
public class GrpcChannelScope implements CustomScope<GrpcChannel>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcChannelScope.class);
    private final Map<ChannelKey, ManagedChannel> channels = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

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
        if (!Channel.class.isAssignableFrom(argument.getType()) ) {
            throw new DependencyInjectionException(resolutionContext, argument, "@GrpcChannel used on type that is not a Channel");
        }

        if (GrpcServerChannel.NAME.equalsIgnoreCase(value)) {
            return (T) applicationContext.getBean(ManagedChannel.class, Qualifiers.byName(GrpcServerChannel.NAME));
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
