# tag::imports[]
import java

from io.grpc.stub import StreamObserver
from jakarta.inject import Singleton

from .GreetingService import GreetingService

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloReply = java.type("helloworld.HelloReply")
HelloRequest = java.type("helloworld.HelloRequest")
# end::imports[]


# tag::clazz[]
@Singleton
class GreetingEndpoint(GreeterGrpc.GreeterImplBase):  # <1>

    # <2>
    def __init__(self, greeting_service: GreetingService):
        self.greeting_service = greeting_service

    def sayHello(self, request: HelloRequest, response_observer: StreamObserver[HelloReply]) -> None:
        # <3>
        message = self.greeting_service.say_hello(request.getName())
        reply = HelloReply.newBuilder().setMessage(message).build()
        response_observer.onNext(reply)
        response_observer.onCompleted()
# end::clazz[]
