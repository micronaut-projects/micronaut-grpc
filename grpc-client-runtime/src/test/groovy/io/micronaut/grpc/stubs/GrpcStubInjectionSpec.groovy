package io.micronaut.grpc.stubs

import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GrpcStubInjectionSpec extends Specification {

    @Shared
    int port = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                       : 'GrpcStubInjectionSpec',
            'grpc.server.port'                : port,
            'grpc.channels.stubs.address'      : "localhost:$port".toString(),
            'grpc.channels.stubs.plaintext'    : true,
            'grpc.client.disabled.enabled'     : false,
    ])

    void "blocking, async and future stubs are injected into fields"() {
        given:
        FieldClient client = server.applicationContext.getBean(FieldClient)
        CompletableFuture<String> asyncReply = new CompletableFuture<>()

        when:
        client.async.echo(request('async'), new StreamObserver<EchoReply>() {
            @Override
            void onNext(EchoReply value) {
                asyncReply.complete(value.text)
            }

            @Override
            void onError(Throwable t) {
                asyncReply.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // Nothing to do: the reply is handled in onNext.
            }
        })
        def futureReply = client.future.echo(request('future'))

        then:
        client.blocking.withDeadlineAfter(10, TimeUnit.SECONDS).echo(request('blocking')).text == 'echo blocking'
        asyncReply.get(10, TimeUnit.SECONDS) == 'echo async'
        futureReply.get(10, TimeUnit.SECONDS).text == 'echo future'
    }

    void "a stub is injected through a constructor"() {
        expect:
        server.applicationContext.getBean(ConstructorClient).stub
                .withDeadlineAfter(10, TimeUnit.SECONDS).echo(request('constructor')).text == 'echo constructor'
    }

    void "stubs of the same channel share one managed channel"() {
        given:
        FieldClient fields = server.applicationContext.getBean(FieldClient)
        ConstructorClient constructor = server.applicationContext.getBean(ConstructorClient)

        expect:
        fields.blocking.channel.is(fields.async.channel)
        fields.async.channel.is(fields.future.channel)
        constructor.stub.channel.is(fields.blocking.channel)
    }

    void "a nullable stub of a disabled channel is null"() {
        expect:
        server.applicationContext.getBean(DisabledClient).stub == null
    }

    private static EchoRequest request(String text) {
        EchoRequest.newBuilder().setText(text).build()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcStubInjectionSpec')
    static class EchoService extends EchoGrpc.EchoImplBase {
        @Override
        void echo(EchoRequest request, StreamObserver<EchoReply> responseObserver) {
            responseObserver.onNext(EchoReply.newBuilder().setText("echo ${request.text}".toString()).build())
            responseObserver.onCompleted()
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcStubInjectionSpec')
    static class FieldClient {
        @Inject
        @GrpcChannel('stubs')
        protected EchoGrpc.EchoBlockingStub blocking

        @Inject
        @GrpcChannel('stubs')
        protected EchoGrpc.EchoStub async

        @Inject
        @GrpcChannel('stubs')
        protected EchoGrpc.EchoFutureStub future
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcStubInjectionSpec')
    static class ConstructorClient {
        final EchoGrpc.EchoBlockingStub stub

        ConstructorClient(@GrpcChannel('stubs') EchoGrpc.EchoBlockingStub stub) {
            this.stub = stub
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcStubInjectionSpec')
    static class DisabledClient {
        final EchoGrpc.EchoBlockingStub stub

        DisabledClient(@Nullable @GrpcChannel('disabled') EchoGrpc.EchoBlockingStub stub) {
            this.stub = stub
        }
    }
}
