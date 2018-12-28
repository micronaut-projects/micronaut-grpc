package io.micronaut.grpc.channels;

import io.grpc.NameResolver;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.env.Environment;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.concurrent.ExecutorService;

@EachProperty(GrpcManagedChannelConfiguration.PREFIX)
public class GrpcNamedManagedChannelConfiguration extends GrpcManagedChannelConfiguration {
    public GrpcNamedManagedChannelConfiguration(
            @Parameter String name,
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
