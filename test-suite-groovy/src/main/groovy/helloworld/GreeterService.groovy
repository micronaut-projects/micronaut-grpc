package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.stub.StreamObserver
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
// end::imports[]

// tag::clazz[]
@CompileStatic
@Singleton
class GreeterService extends GreeterGrpc.GreeterImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(GreeterService)

    @GrpcRestJsonExposed
    @Override
    void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        LOG.debug("Received request: {}", request)
        String name = request.name
        String greeting = "Hello, " + name

        HelloResponse reply = HelloResponse.newBuilder()
            .setGreeting(greeting)
            .build()

        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
// end::clazz[]
