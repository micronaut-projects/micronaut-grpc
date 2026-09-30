package helloworld

// tag::imports[]
import io.grpc.ManagedChannelBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
// tag::clazz[]
@Singleton
class ManagedChannelBuilderListener : BeanCreatedEventListener<ManagedChannelBuilder<*>> {

    override fun onCreated(event: BeanCreatedEvent<ManagedChannelBuilder<*>>): ManagedChannelBuilder<*> {
        val channelBuilder = event.bean
        channelBuilder.maxInboundMessageSize(1024)
        return channelBuilder
    }
}
// end::clazz[]
