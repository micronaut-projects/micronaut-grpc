package io.micronaut.grpc.server;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.TaskExecutors;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Factory
public class GrpcServerChannel {

    public static final String NAME = "grpc-server";

    @Singleton
    @Named(NAME)
    @Requires(beans = GrpcEmbeddedServer.class)
    @Bean(preDestroy = "shutdown")
    protected ManagedChannel serverChannel(
            GrpcEmbeddedServer server,
            @javax.inject.Named(TaskExecutors.IO) ExecutorService executorService,
            List<ClientInterceptor> clientInterceptors) {
        final ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(
                server.getHost(),
                server.getPort()
        ).executor(executorService);
        if (!server.getServerConfiguration().isSecure()) {
            builder.usePlaintext();
        }
        if (CollectionUtils.isNotEmpty(clientInterceptors)) {
            builder.intercept(clientInterceptors);
        }
        return builder.build();
    }
}
