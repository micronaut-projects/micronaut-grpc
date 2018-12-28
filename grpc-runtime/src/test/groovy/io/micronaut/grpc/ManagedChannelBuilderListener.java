package io.micronaut.grpc;

import io.grpc.ManagedChannelBuilder;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

@Singleton
public class ManagedChannelBuilderListener implements BeanCreatedEventListener<ManagedChannelBuilder<?>> {
    @Override
    public ManagedChannelBuilder<?> onCreated(BeanCreatedEvent<ManagedChannelBuilder<?>> event) {
        final ManagedChannelBuilder<?> channelBuilder = event.getBean();
        channelBuilder.maxInboundMessageSize(1024);
        return channelBuilder;
    }
}
