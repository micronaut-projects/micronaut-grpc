package helloworld

// tag::imports[]
import io.grpc.stub.StreamObserver
import javax.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton // <1>
class GreetingEndpoint(val greetingService : GreetingService) : GreeterGrpc.GreeterImplBase() { // <2>
    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
    	// <3>
        val message = greetingService.sayHello(request.name)
        val reply = HelloReply.newBuilder().setMessage(message).build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
// end::clazz[]