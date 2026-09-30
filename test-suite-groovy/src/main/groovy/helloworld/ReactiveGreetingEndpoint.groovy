package helloworld

import groovy.transform.CompileStatic
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

// tag::reactive-service[]
@CompileStatic
@Singleton
class ReactiveGreetingEndpoint extends ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase {

    private final GreetingService greetingService

    ReactiveGreetingEndpoint(GreetingService greetingService) {
        this.greetingService = greetingService
    }

    @Override
    Mono<HelloReply> sayHello(Mono<HelloRequest> request) {
        request.map(helloRequest ->
            HelloReply.newBuilder()
                .setMessage(greetingService.sayHello(helloRequest.name))
                .build()
        )
    }
}
// end::reactive-service[]
