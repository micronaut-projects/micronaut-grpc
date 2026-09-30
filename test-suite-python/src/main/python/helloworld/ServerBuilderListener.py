# tag::imports[]
from io.grpc import ServerBuilder
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="ServerBuilderListenerTest")
# tag::clazz[]
@Singleton
class ServerBuilderListener(BeanCreatedEventListener[ServerBuilder]):

    def onCreated(self, event: BeanCreatedEvent[ServerBuilder]) -> ServerBuilder:
        builder = event.getBean()
        builder.maxInboundMessageSize(1024)
        return builder
# end::clazz[]
