package io.micronaut.grpc.channels;

import io.grpc.ClientInterceptor;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.*;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;

import java.util.List;
import java.util.concurrent.ExecutorService;


@Factory
public class GrpcChannelBuilderFactory {

    private final ApplicationContext beanContext;
    private final ExecutorService executorService;


    public GrpcChannelBuilderFactory(
            ApplicationContext beanContext,
            @javax.inject.Named(TaskExecutors.IO) ExecutorService executorService) {
        this.beanContext = beanContext;
        this.executorService = executorService;
    }

    @Bean
    @Prototype
    protected NettyChannelBuilder managedChannelBuilder(@Parameter String target, List<ClientInterceptor> interceptors) {
        GrpcManagedChannelConfiguration config = beanContext.findBean(GrpcManagedChannelConfiguration.class, Qualifiers.byName(target)).orElseGet(() ->
                {
                    final GrpcDefaultManagedChannelConfiguration mcc = new GrpcDefaultManagedChannelConfiguration(
                            target,
                            beanContext.getEnvironment(),
                            executorService
                    );
                    beanContext.inject(mcc);
                    return mcc;
                }
        );
        final NettyChannelBuilder channelBuilder = config.getChannelBuilder();
        if (CollectionUtils.isNotEmpty(interceptors)) {
            channelBuilder.intercept(interceptors);
        }
        return channelBuilder;
    }
}
