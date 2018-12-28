package io.micronaut.grpc.discovery;

import io.grpc.NameResolver;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.grpc.channels.GrpcDefaultManagedChannelConfiguration;

import javax.inject.Singleton;
import java.util.List;

@Factory
public class GrpcNameResolverFactory {

    public static final String ENABLED = GrpcDefaultManagedChannelConfiguration.PREFIX + ".discovery.enabled";

    @Singleton
    @Requires(beans = DiscoveryClient.class)
    @Requires(property = ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
    protected NameResolver.Factory nameResolverFactory(DiscoveryClient discoveryClient, List<ServiceInstanceList> serviceInstanceLists) {
        return new GrpcNameResolverProvider(discoveryClient, serviceInstanceLists);
    }
}
