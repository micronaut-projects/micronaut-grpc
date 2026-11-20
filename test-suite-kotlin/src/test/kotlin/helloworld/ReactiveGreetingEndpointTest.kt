package helloworld

import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.Duration

// tag::reactive-test[]
@MicronautTest
class ReactiveGreetingEndpointTest {

    @Inject
    lateinit var reactiveStub: ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub

    @Test
    fun testReactiveHelloWorld() {
        val message = Mono.just(HelloRequest.newBuilder().setName("Fred").build())
            .transform(reactiveStub::sayHello)
            .map(HelloReply::getMessage)
            .block(Duration.ofSeconds(5))

        assertEquals("Hello Fred", message)
    }
}
// end::reactive-test[]

@Factory
class ReactiveClients {

    // tag::reactive-client[]
    @Bean
    fun reactiveStub(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel): ReactorReactiveGreeterGrpc.ReactorReactiveGreeterStub =
        ReactorReactiveGreeterGrpc.newReactorStub(channel)
    // end::reactive-client[]
}
