package helloworld;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * The Reactor gRPC service of the reactive-grpc documentation example. A Python class cannot extend the
 * generated {@code ReactiveGreeterImplBase} class (TODO(python): Python classes cannot extend Java classes),
 * so the reactive server of this test suite is written in Java; the Reactor client stub is used from Python in
 * {@code ReactiveGreetingEndpointTest.py}.
 */
@Singleton
public class ReactiveGreetingEndpoint extends ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase {

    @Override
    public Mono<HelloReply> sayHello(Mono<HelloRequest> request) {
        return request.map(helloRequest ->
            HelloReply.newBuilder()
                .setMessage("Hello " + helloRequest.getName())
                .build()
        );
    }
}
