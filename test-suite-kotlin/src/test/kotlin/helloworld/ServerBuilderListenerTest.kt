package helloworld

import io.grpc.Status
import io.grpc.StatusException
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "ServerBuilderListenerTest")
@MicronautTest
class ServerBuilderListenerTest {

    @Inject
    lateinit var greetingClient: GreeterGrpcKt.GreeterCoroutineStub

    @Test
    fun testMaxInboundMessageSize() {
        val request = HelloRequest.newBuilder()
            .setName("x".repeat(2000))
            .build()
        val ex = assertThrows(StatusException::class.java) {
            runBlocking { greetingClient.sayHello(request) }
        }
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.status.code)
    }
}
