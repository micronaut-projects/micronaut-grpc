# tag::imports[]
import logging

from io.grpc.stub import StreamObserver
from jakarta.inject import Singleton
from micronaut.grpc.annotation import GrpcRestJsonExposed
from org.example.grpc import GreeterGrpc, HelloRequest, HelloResponse

LOG = logging.getLogger(__name__)
# end::imports[]


# tag::clazz[]
@Singleton
class GreeterService(GreeterGrpc.GreeterImplBase):

    @GrpcRestJsonExposed
    def sayHello(self, request: HelloRequest, response_observer: StreamObserver[HelloResponse]) -> None:
        LOG.debug("Received request: %s", request)
        name = request.getName()
        greeting = "Hello, " + name

        reply = HelloResponse.newBuilder().setGreeting(greeting).build()

        response_observer.onNext(reply)
        response_observer.onCompleted()
# end::clazz[]
