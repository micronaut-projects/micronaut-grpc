package helloworld

import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Property(name = "spec.name", value = "NamedChannelClientsTest")
@Property(name = "grpc.server.port", value = "50172")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50172")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class NamedChannelClientsTest {

    @Inject
    lateinit var greeterStub: GreeterGrpc.GreeterStub

    @Test
    fun testNamedChannel() {
        val message = CompletableFuture<String>()
        greeterStub.sayHello(HelloRequest.newBuilder().setName("Fred").build(), object : StreamObserver<HelloReply> {
            override fun onNext(reply: HelloReply) {
                message.complete(reply.message)
            }

            override fun onError(t: Throwable) {
                message.completeExceptionally(t)
            }

            override fun onCompleted() {
            }
        })
        assertEquals("Hello Fred", message.get(10, TimeUnit.SECONDS))
    }
}
