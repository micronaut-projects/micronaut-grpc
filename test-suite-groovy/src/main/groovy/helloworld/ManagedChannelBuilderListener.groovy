package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.ManagedChannelBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
// tag::clazz[]
@CompileStatic
@Singleton
class ManagedChannelBuilderListener implements BeanCreatedEventListener<ManagedChannelBuilder<?>> {

    @Override
    ManagedChannelBuilder<?> onCreated(BeanCreatedEvent<ManagedChannelBuilder<?>> event) {
        final ManagedChannelBuilder<?> channelBuilder = event.bean
        channelBuilder.maxInboundMessageSize(1024)
        channelBuilder
    }
}
// end::clazz[]
