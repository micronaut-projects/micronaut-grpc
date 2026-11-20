import java

from jakarta.inject import Singleton
from reactor.core.publisher import Mono

from .GreetingService import GreetingService

# `helloworld` is both the Java package of the generated gRPC classes and the package of these
# Python sources, so the generated types are looked up by name instead of imported.
HelloReply = java.type("helloworld.HelloReply")
HelloRequest = java.type("helloworld.HelloRequest")
ReactorReactiveGreeterGrpc = java.type("helloworld.ReactorReactiveGreeterGrpc")


# tag::reactive-service[]
@Singleton
class ReactiveGreetingEndpoint(ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase):

    def __init__(self, greeting_service: GreetingService):
        self.greeting_service = greeting_service

    def sayHello(self, request: Mono[HelloRequest]) -> Mono[HelloReply]:
        return request.map(
            lambda hello_request: HelloReply.newBuilder()
                .setMessage(self.greeting_service.say_hello(hello_request.getName()))
                .build()
        )
# end::reactive-service[]
