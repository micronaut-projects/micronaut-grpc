package helloworld

// tag::imports[]
import io.grpc.ManagedChannel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
// end::imports[]

// tag::test[]
@MicronautTest // <1>
class GreetingEndpointTest(
    private val greetingClient: GreeterGrpcKt.GreeterCoroutineStub, // <2>
) : StringSpec({
    "returns a greeting response" {
        greetingClient.sayHello( // <3>
            HelloRequest.newBuilder().setName("Fred").build(),
        ).message shouldBe "Hello Fred"
    }
})
// end::test[]

// tag::clients[]
@Factory
class Clients {

    @Bean
    fun greetingClient(@GrpcChannel(GrpcServerChannel.NAME) channel: ManagedChannel): GreeterGrpcKt.GreeterCoroutineStub = // <1>
        GreeterGrpcKt.GreeterCoroutineStub(channel) // <2>
}
// end::clients[]
