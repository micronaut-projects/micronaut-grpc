package helloworld

// tag::imports[]
import javax.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton // <1>
@Suppress("unused")
class GreetingEndpoint(val greetingService : GreetingService) : GreeterGrpcKt.GreeterCoroutineImplBase() { // <2>
    override suspend fun sayHello(request: HelloRequest): HelloReply {
        // <3>
        val message = greetingService.sayHello(request.name)
        val reply = HelloReply.newBuilder().setMessage(message).build()
        return reply
    }
}
// end::clazz[]