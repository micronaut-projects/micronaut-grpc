package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "ServerInterceptorTest")
@MicronautTest
class ServerInterceptorTest extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub

    void "test interceptors are registered"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Fred").build()

        expect:
        blockingStub.sayHello(request).message == "Hello Fred"
        // the ordered bean and the interceptor wrapped by the factory both intercept the call
        CustomInterceptor.INTERCEPTED == ["helloworld.Greeter/SayHello", "helloworld.Greeter/SayHello"]
    }
}
