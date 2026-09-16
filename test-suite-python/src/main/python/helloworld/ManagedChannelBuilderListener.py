# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from micronaut.grpc.channels import GrpcManagedChannelConfiguration
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="ManagedChannelBuilderListenerTest")
# tag::clazz[]
@Singleton
class ManagedChannelBuilderListener(BeanCreatedEventListener[GrpcManagedChannelConfiguration]):

    def onCreated(self, event: BeanCreatedEvent[GrpcManagedChannelConfiguration]) -> GrpcManagedChannelConfiguration:
        configuration = event.getBean()
        configuration.getChannelBuilder().maxInboundMessageSize(1024)
        return configuration
# end::clazz[]
