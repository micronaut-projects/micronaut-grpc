package helloworld

import io.grpc.stub.StreamObserver
import javax.inject.Singleton

@Singleton
class GreetingEndpoint(val greetingService : GreetingService) : GreeterGrpc.GreeterImplBase() {
    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloReply>) {
        val message = greetingService.sayHello(request.name)
        val reply = HelloReply.newBuilder().setMessage(message).build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}