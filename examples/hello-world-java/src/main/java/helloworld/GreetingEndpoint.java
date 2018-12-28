package helloworld;

import io.grpc.stub.StreamObserver;

import javax.inject.Singleton;

@Singleton
public class GreetingEndpoint extends GreeterGrpc.GreeterImplBase {

    private final GreetingService greetingService;

    public GreetingEndpoint(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        final String message = greetingService.sayHello(request.getName());
        HelloReply reply = HelloReply.newBuilder().setMessage(message).build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
