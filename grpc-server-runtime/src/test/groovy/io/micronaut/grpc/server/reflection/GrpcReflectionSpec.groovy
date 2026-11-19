package io.micronaut.grpc.server.reflection

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.netty.NettyChannelBuilder
import io.grpc.reflection.v1.ServerReflectionGrpc
import io.grpc.reflection.v1.ServerReflectionRequest
import io.grpc.reflection.v1.ServerReflectionResponse
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GrpcReflectionSpec extends Specification {

    void "reflection service is not registered by default"() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'GrpcReflectionSpec')

        expect:
        !context.containsBean(BindableService, Qualifiers.byName(GrpcReflectionFactory.REFLECTION_SERVICE_NAME))

        cleanup:
        context.close()
    }

    void "reflection service lists the registered services when enabled"() {
        given:
        EmbeddedServer server = ApplicationContext.run(GrpcEmbeddedServer, [
                'spec.name'                     : 'GrpcReflectionSpec',
                (GrpcReflectionFactory.REFLECTION_ENABLED): true
        ])
        ManagedChannel channel = NettyChannelBuilder.forAddress('localhost', server.port).usePlaintext().build()

        when:
        List<String> services = listServices(channel)

        then:
        services.contains('helloworld.Greeter')
        services.contains('grpc.reflection.v1.ServerReflection')

        when:
        ServerReflectionResponse response = call(channel, ServerReflectionRequest.newBuilder()
                .setFileContainingSymbol('helloworld.Greeter')
                .build())

        then:
        response.hasFileDescriptorResponse()
        response.fileDescriptorResponse.fileDescriptorProtoCount > 0

        cleanup:
        channel?.shutdownNow()
        server?.close()
    }

    private static List<String> listServices(ManagedChannel channel) {
        ServerReflectionResponse response = call(channel, ServerReflectionRequest.newBuilder().setListServices('').build())
        response.listServicesResponse.serviceList*.name
    }

    private static ServerReflectionResponse call(ManagedChannel channel, ServerReflectionRequest request) {
        CompletableFuture<ServerReflectionResponse> result = new CompletableFuture<>()
        StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc.newStub(channel).serverReflectionInfo(new StreamObserver<ServerReflectionResponse>() {
            @Override
            void onNext(ServerReflectionResponse value) {
                result.complete(value)
            }

            @Override
            void onError(Throwable t) {
                result.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // Nothing to do: the reply is handled in onNext.
            }
        })
        requests.onNext(request)
        requests.onCompleted()
        result.get(10, TimeUnit.SECONDS)
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcReflectionSpec')
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            responseObserver.onNext(HelloReply.newBuilder().setMessage("Hello ${request.name}").build())
            responseObserver.onCompleted()
        }
    }
}
