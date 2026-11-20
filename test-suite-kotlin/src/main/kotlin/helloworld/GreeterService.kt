package helloworld

// tag::imports[]
import io.grpc.stub.StreamObserver
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import org.slf4j.LoggerFactory
// end::imports[]

// tag::clazz[]
@Singleton
class GreeterService : GreeterGrpc.GreeterImplBase() {

    @GrpcRestJsonExposed
    override fun sayHello(request: HelloRequest, responseObserver: StreamObserver<HelloResponse>) {
        LOG.debug("Received request: {}", request)
        val name = request.name
        val greeting = "Hello, $name"

        val reply = HelloResponse.newBuilder()
            .setGreeting(greeting)
            .build()

        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GreeterService::class.java)
    }
}
// end::clazz[]
