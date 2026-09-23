package helloworld

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "ServerInterceptorTest")
@MicronautTest
class ServerInterceptorTest {

    @Inject
    lateinit var greetingClient: GreeterGrpcKt.GreeterCoroutineStub

    @Test
    fun testInterceptorsAreRegistered() = runBlocking {
        val request = HelloRequest.newBuilder().setName("Fred").build()
        assertEquals("Hello Fred", greetingClient.sayHello(request).message)
        // the ordered bean and the interceptor wrapped by the factory both intercept the call
        assertEquals(
            listOf("helloworld.Greeter/SayHello", "helloworld.Greeter/SayHello"),
            CustomInterceptor.INTERCEPTED
        )
    }
}
