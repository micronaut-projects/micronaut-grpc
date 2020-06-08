package helloworld

import io.grpc.ManagedChannel
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.annotation.MicronautTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Singleton

@MicronautTest
class GreetingServiceTest {

    @Inject
    lateinit var greetingClient : GreeterGrpcKt.GreeterCoroutineStub

    @Test
    fun testGreetingService()  = runBlocking {
        Assertions.assertEquals(
                "Hello John",
                greetingClient.sayHello(
                        HelloRequest.newBuilder().setName("John").build()
                ).message
        )
    }
}

@Factory
class Clients {
    @Singleton
    fun greetingClient( @GrpcChannel(GrpcServerChannel.NAME) channel : ManagedChannel ) : GreeterGrpcKt.GreeterCoroutineStub {
        return GreeterGrpcKt.GreeterCoroutineStub(
                channel
        )
    }
}