package io.micronaut.grpc.channels;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;

@Factory
public class GrpcManagedChannelFactory {

    private final ApplicationContext beanContext;

    public GrpcManagedChannelFactory(ApplicationContext beanContext) {
        this.beanContext = beanContext;
    }

    @Bean
    @Primary
    protected ManagedChannel managedChannel(@Parameter String target) {
        final NettyChannelBuilder nettyChannelBuilder = beanContext.createBean(NettyChannelBuilder.class, target);
        return nettyChannelBuilder.build();
    }
}
