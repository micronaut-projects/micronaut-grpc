package io.micronaut.grpc.channels;

import io.grpc.NameResolver;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.env.Environment;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.ExecutorService;

@Named(GrpcDefaultManagedChannelConfiguration.NAME)
@ConfigurationProperties(GrpcDefaultManagedChannelConfiguration.PREFIX)
@Primary
public class GrpcDefaultManagedChannelConfiguration extends GrpcManagedChannelConfiguration {
    public static final String NAME = "default";
    public static final String PREFIX = "grpc.client";

    public GrpcDefaultManagedChannelConfiguration(
            String name,
            Environment env,
            ExecutorService executorService) {
        super(name, env, executorService);
    }

    @Override
    @Inject
    public void setNameResolverFactory(@Nullable NameResolver.Factory factory) {
        super.setNameResolverFactory(factory);
    }
}
