package io.micronaut.grpc.channels;

import io.grpc.NameResolver;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.Named;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public abstract class GrpcManagedChannelConfiguration implements Named {
    public static final String PREFIX = "grpc.channels";
    public static final String SETTING_TARGET = PREFIX + ".target";
    public static final String SETTING_URL = PREFIX + ".url";
    protected final String name;

    @ConfigurationBuilder(prefixes = {"use",""}, allowZeroArgs = true)
    protected final NettyChannelBuilder channelBuilder;
    private boolean resolveName = true;

    public GrpcManagedChannelConfiguration(String name, Environment env, ExecutorService executorService) {
        this.name = name;
        final Optional<SocketAddress> socketAddress = env.getProperty(SETTING_URL, SocketAddress.class);
        if (socketAddress.isPresent()) {
            resolveName = false;
            this.channelBuilder = NettyChannelBuilder.forAddress(socketAddress.get());
        } else {
            final Optional<String> target = env.getProperty(SETTING_TARGET, String.class);
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

    public void setNameResolverFactory(@Nullable NameResolver.Factory factory) {
        if (factory != null && resolveName) {
            channelBuilder.nameResolverFactory(factory);
        }
    }
}
