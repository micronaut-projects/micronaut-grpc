package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.stub.StreamObserver
import jakarta.inject.Singleton
// end::imports[]


// tag::clazz[]
@CompileStatic
@Singleton
class GreetingEndpoint extends GreeterGrpc.GreeterImplBase { // <1>

    final GreetingService greetingService

    // <2>
    GreetingEndpoint(GreetingService greetingService) {
        this.greetingService = greetingService
    }

    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        // <3>
        HelloReply.newBuilder().with {
            message = greetingService.sayHello(request.name)
            responseObserver.onNext(build())
            responseObserver.onCompleted()
        }
    }
}
// end::clazz[]
