# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from micronaut.grpc.server import GrpcServerConfiguration
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="ServerBuilderListenerTest")
# tag::clazz[]
@Singleton
class ServerBuilderListener(BeanCreatedEventListener[GrpcServerConfiguration]):

    def onCreated(self, event: BeanCreatedEvent[GrpcServerConfiguration]) -> GrpcServerConfiguration:
        configuration = event.getBean()
        configuration.getServerBuilder().maxInboundMessageSize(1024)
        return configuration
# end::clazz[]
