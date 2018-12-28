package helloworld

import groovy.transform.CompileStatic
import io.grpc.stub.StreamObserver

import javax.inject.Singleton

@CompileStatic
@Singleton
class GreetingEndpoint extends GreeterGrpc.GreeterImplBase {

    final GreetingService greetingService

    GreetingEndpoint(GreetingService greetingService) {
        this.greetingService = greetingService
    }

    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply.newBuilder().with {
            message = greetingService.sayHello(request.name)
            responseObserver.onNext(build())
            responseObserver.onCompleted()
        }
    }
}
