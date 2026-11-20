package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.ServerBuilder
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "ServerBuilderListenerTest")
// tag::clazz[]
@CompileStatic
@Singleton
class ServerBuilderListener implements BeanCreatedEventListener<ServerBuilder<?>> {

    @Override
    ServerBuilder<?> onCreated(BeanCreatedEvent<ServerBuilder<?>> event) {
        final ServerBuilder<?> builder = event.bean
        builder.maxInboundMessageSize(1024)
        builder
    }
}
// end::clazz[]
