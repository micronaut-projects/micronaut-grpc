package helloworld;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * The Reactor gRPC service of the reactive-grpc documentation example.
 *
 * <p>This endpoint stays in Java. The generated {@code ReactiveGreeterImplBase} declares two
 * {@code sayHello} overloads ({@code HelloRequest} and {@code Mono<HelloRequest>}) and a Python
 * method overrides every overload of its name, so the unary overload the server calls
 * ({@code ServerCalls.oneToOne(request, serviceImpl::sayHello, ...)}) would hand the Python
 * {@code sayHello(Mono[HelloRequest])} the raw request and the call would fail with
 * {@code UNKNOWN}. The Micronaut Core fix for that is merged but not yet released, so the
 * reactive server of this test suite is written in Java and the Reactor client stub is the part
 * exercised from Python, in {@code ReactiveGreetingEndpointTest.py}.
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
