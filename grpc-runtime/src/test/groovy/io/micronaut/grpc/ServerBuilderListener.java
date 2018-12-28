package io.micronaut.grpc;

import io.grpc.ServerBuilder;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

@Singleton
public class ServerBuilderListener implements BeanCreatedEventListener<ServerBuilder<?>> {
    @Override
    public ServerBuilder<?> onCreated(BeanCreatedEvent<ServerBuilder<?>> event) {
        final ServerBuilder<?> builder = event.getBean();
        builder.maxInboundMessageSize(1024);
        return builder;
    }
}
