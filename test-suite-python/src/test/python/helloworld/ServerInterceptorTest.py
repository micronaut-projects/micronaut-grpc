import java
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .CustomInterceptor import INTERCEPTED

# TODO(python): java.type needed because `helloworld` is both the Java package of the generated gRPC classes and the package of these Python sources
GreeterGrpc = java.type("helloworld.GreeterGrpc")
HelloRequest = java.type("helloworld.HelloRequest")


@Property(name="spec.name", value="ServerInterceptorTest")
@MicronautTest
class ServerInterceptorTest:

    blocking_stub: Annotated[GreeterGrpc.GreeterBlockingStub, Inject]

    @Test
    def test_interceptors_are_registered(self):
        request = HelloRequest.newBuilder().setName("Fred").build()
        assert self.blocking_stub.sayHello(request).getMessage() == "Hello Fred"
        # the interceptor wrapped by the factory intercepts the call
        # (TODO(python): a Python class cannot implement `ServerInterceptor` and `Ordered` itself, see CustomInterceptor)
        assert INTERCEPTED == ["helloworld.Greeter/SayHello"]
