package helloworld;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * The Reactor gRPC service of the reactive-grpc documentation example. TODO(python): the generated
 * {@code ReactiveGreeterImplBase} declares two {@code sayHello} overloads ({@code HelloRequest} and
 * {@code Mono<HelloRequest>}) and a Python method overrides every overload of its name, so the unary
 * overload the server calls ({@code ServerCalls.oneToOne(request, serviceImpl::sayHello, ...)}) hands the
 * Python {@code sayHello(Mono[HelloRequest])} the raw request (the call fails with {@code UNKNOWN}); the
 * reactive server of this test suite is written in Java and the Reactor client stub is used from Python in
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
