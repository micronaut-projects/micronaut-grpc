package helloworld

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "ServerBuilderListenerTest")
@MicronautTest
class ServerBuilderListenerTest extends Specification {

    @Inject
    GreeterGrpc.GreeterBlockingStub blockingStub

    void "test max inbound message size"() {
        given:
        HelloRequest request = HelloRequest.newBuilder()
            .setName("x" * 2000)
            .build()

        when:
        blockingStub.sayHello(request)

        then:
        StatusRuntimeException ex = thrown()
        ex.status.code == Status.Code.RESOURCE_EXHAUSTED
    }
}
