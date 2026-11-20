package helloworld

import jakarta.inject.Singleton
import reactor.core.publisher.Mono

// tag::reactive-service[]
@Singleton
class ReactiveGreetingEndpoint(private val greetingService: GreetingService) : ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase() {

    override fun sayHello(request: Mono<HelloRequest>): Mono<HelloReply> =
        request.map { helloRequest ->
            HelloReply.newBuilder()
                .setMessage(greetingService.sayHello(helloRequest.name))
                .build()
        }
}
// end::reactive-service[]
