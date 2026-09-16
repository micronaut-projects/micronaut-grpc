# tag::imports[]
import logging

from jakarta.inject import Singleton
from micronaut.grpc.annotation import GrpcRestJsonExposed
from org.example.grpc import GreeterGrpc, HelloRequest, HelloResponse

try:
    from io.grpc import BindableService, ServerServiceDefinition
    from io.grpc.stub import StreamObserver
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import BindableService, ServerServiceDefinition
    from grpc.stub import StreamObserver

LOG = logging.getLogger(__name__)
# end::imports[]


# tag::clazz[]
@Singleton
class GreeterService(BindableService):

    @GrpcRestJsonExposed
    def sayHello(self, request: HelloRequest, response_observer: StreamObserver[HelloResponse]) -> None:
        LOG.debug("Received request: %s", request)
        name = request.getName()
        greeting = "Hello, " + name

        reply = HelloResponse.newBuilder().setGreeting(greeting).build()

        response_observer.onNext(reply)
        response_observer.onCompleted()

    def bindService(self) -> ServerServiceDefinition:
        return GreeterGrpc.bindService(self)
# end::clazz[]
