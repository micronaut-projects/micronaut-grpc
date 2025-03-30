package io.micronaut.protobuf.grpc

import io.grpc.stub.StreamObserver
import jakarta.inject.Singleton
import org.example.grpc.AnotherGrpc
import org.example.grpc.AnotherRequest
import org.example.grpc.AnotherResponse
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class AnotherGrpcService extends AnotherGrpc.AnotherImplBase {

    private static final Logger log = LoggerFactory.getLogger(AnotherGrpcService.class)

    AnotherGrpcService() {
        log.info("AnotherGrpcService bean created")
    }

    @GrpcRestJsonExposed
    @Override
    void anotherMethod(AnotherRequest request, StreamObserver<AnotherResponse> responseObserver) {
        log.info("Invoked anotherMethod with request: '{}'", request.message)

        def reply = AnotherResponse.newBuilder()
                .setReply("Response: ${request.message}")
                .build()

        responseObserver.onNext(reply)
        responseObserver.onCompleted()
    }
}
