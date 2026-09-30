package helloworld

// tag::imports[]
import io.grpc.ServerBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "ServerBuilderListenerTest")
// tag::clazz[]
@Singleton
class ServerBuilderListener : BeanCreatedEventListener<ServerBuilder<*>> {

    override fun onCreated(event: BeanCreatedEvent<ServerBuilder<*>>): ServerBuilder<*> {
        val builder = event.bean
        builder.maxInboundMessageSize(1024)
        return builder
    }
}
// end::clazz[]
