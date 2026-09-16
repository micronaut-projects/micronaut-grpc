package helloworld

import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "ManagedChannelBuilderListenerTest")
@Property(name = "grpc.server.port", value = "50171")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50171")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class ManagedChannelBuilderListenerTest {

    @Inject
    @field:Named("greeter")
    lateinit var greeterStub: GreeterGrpcKt.GreeterCoroutineStub

    @Test
    fun testMaxInboundMessageSize() {
        // the reply is larger than the 1024 bytes the channel accepts
        val request = HelloRequest.newBuilder()
            .setName("x".repeat(2000))
            .build()
        val ex = assertThrows(StatusException::class.java) {
            runBlocking { greeterStub.sayHello(request) }
        }
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.status.code)
    }

    // the "grpc-server" test channel bypasses the channel builders, so the test uses the named "greeter" channel
    @Requires(property = "spec.name", value = "ManagedChannelBuilderListenerTest")
    @Factory
    class GreeterChannelClients {

        @Bean
        @Named("greeter")
        fun greeterStub(@GrpcChannel("greeter") channel: ManagedChannel): GreeterGrpcKt.GreeterCoroutineStub =
            GreeterGrpcKt.GreeterCoroutineStub(channel)
    }
}
