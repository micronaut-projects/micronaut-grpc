package helloworld

import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration

// tag::reactive-test[]
@MicronautTest
class ReactiveGreetingEndpointTest extends Specification {

    @Inject
    ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub reactiveStub

    void "test reactive greeting endpoint"() {
        when:
        String message = Mono.just(HelloRequest.newBuilder().setName("Fred").build())
            .transform(reactiveStub::sayHello)
            .map(HelloReply::getMessage)
            .block(Duration.ofSeconds(5))

        then:
        message == "Hello Fred"
    }
}
// end::reactive-test[]

@Factory
class ReactiveClients {

    // tag::reactive-client[]
    @Bean
    ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub reactiveStub(
            @GrpcChannel(GrpcServerChannel.NAME) ManagedChannel channel) {
        ReactorReactiveGreeterGrpc.newReactorStub(channel)
    }
    // end::reactive-client[]
}
