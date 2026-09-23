# tag::imports[]
from io.grpc import ManagedChannelBuilder
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="ManagedChannelBuilderListenerTest")
# tag::clazz[]
@Singleton
class ManagedChannelBuilderListener(BeanCreatedEventListener[ManagedChannelBuilder]):

    def onCreated(self, event: BeanCreatedEvent[ManagedChannelBuilder]) -> ManagedChannelBuilder:
        channel_builder = event.getBean()
        channel_builder.maxInboundMessageSize(1024)
        return channel_builder
# end::clazz[]
