package helloworld;

// tag::imports[]
import io.grpc.ManagedChannelBuilder;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
// tag::clazz[]
@Singleton
public class ManagedChannelBuilderListener implements BeanCreatedEventListener<ManagedChannelBuilder<?>> {

    @Override
    public ManagedChannelBuilder<?> onCreated(BeanCreatedEvent<ManagedChannelBuilder<?>> event) {
        final ManagedChannelBuilder<?> channelBuilder = event.getBean();
        channelBuilder.maxInboundMessageSize(1024);
        return channelBuilder;
    }
}
// end::clazz[]
