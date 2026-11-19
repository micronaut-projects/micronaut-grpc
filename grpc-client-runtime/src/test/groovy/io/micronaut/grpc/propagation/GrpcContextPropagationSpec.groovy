package io.micronaut.grpc.propagation

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.order.Ordered
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.client.propagation.GrpcClientContextPropagationInterceptor
import io.micronaut.grpc.server.propagation.GrpcServerContextPropagationInterceptor
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GrpcContextPropagationSpec extends Specification {

    static final MethodDescriptor<String, String> ECHO = MethodDescriptor.<String, String> newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName('test.Context', 'Echo'))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build()

    void "client callbacks see the caller's context"() {
        given:
        EmbeddedServer server = start([:])
        Channel channel = server.applicationContext.getBean(Client).channel
        CompletableFuture<String> seenInCallback = new CompletableFuture<>()

        when:
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new Tenant('acme')).propagate()) {
            ClientCalls.asyncUnaryCall(channel.newCall(ECHO, CallOptions.DEFAULT), 'hi', new StreamObserver<String>() {
                @Override
                void onNext(String value) {
                    seenInCallback.complete(currentTenant())
                }

                @Override
                void onError(Throwable t) {
                    seenInCallback.completeExceptionally(t)
                }

                @Override
                void onCompleted() {
                    // Nothing to do: the reply is handled in onNext.
                }
            })
        }

        then:
        seenInCallback.get(10, TimeUnit.SECONDS) == 'acme'

        cleanup:
        server?.close()
    }

    void "other client interceptors see the caller's context in their response callbacks"() {
        given:
        EmbeddedServer server = start([:])
        def context = server.applicationContext
        Channel channel = context.getBean(Client).channel
        RecordingClientInterceptor recorder = context.getBean(RecordingClientInterceptor)

        when: 'an async call, so that callbacks run on the channel executor rather than the calling thread'
        CompletableFuture<String> reply = new CompletableFuture<>()
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new Tenant('acme')).propagate()) {
            ClientCalls.asyncUnaryCall(channel.newCall(ECHO, CallOptions.DEFAULT), 'hi', new StreamObserver<String>() {
                @Override
                void onNext(String value) {
                    reply.complete(value)
                }

                @Override
                void onError(Throwable t) {
                    reply.completeExceptionally(t)
                }

                @Override
                void onCompleted() {
                    // Nothing to do: the reply is handled in onNext.
                }
            })
        }

        then:
        reply.get(10, TimeUnit.SECONDS) == 'hi from tenant-from-header'
        recorder.seenInOnMessage.get(10, TimeUnit.SECONDS) == 'acme'
        recorder.seenInOnClose.get(10, TimeUnit.SECONDS) == 'acme'

        cleanup:
        server?.close()
    }

    void "client callbacks have no context when propagation is disabled"() {
        given:
        EmbeddedServer server = start([(GrpcClientContextPropagationInterceptor.ENABLED): false])
        Channel channel = server.applicationContext.getBean(Client).channel
        CompletableFuture<String> seenInCallback = new CompletableFuture<>()

        when:
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new Tenant('acme')).propagate()) {
            ClientCalls.asyncUnaryCall(channel.newCall(ECHO, CallOptions.DEFAULT), 'hi', new StreamObserver<String>() {
                @Override
                void onNext(String value) {
                    seenInCallback.complete(currentTenant())
                }

                @Override
                void onError(Throwable t) {
                    seenInCallback.completeExceptionally(t)
                }

                @Override
                void onCompleted() {
                    // Nothing to do: the reply is handled in onNext.
                }
            })
        }

        then:
        seenInCallback.get(10, TimeUnit.SECONDS) == 'none'

        cleanup:
        server?.close()
    }

    void "the service method sees context set by a server interceptor"() {
        given:
        EmbeddedServer server = start([:])
        Channel channel = server.applicationContext.getBean(Client).channel

        expect:
        ClientCalls.blockingUnaryCall(channel, ECHO, CallOptions.DEFAULT, 'hi') == 'hi from tenant-from-header'

        cleanup:
        server?.close()
    }

    void "the service method loses the interceptor's context when server propagation is disabled"() {
        given:
        EmbeddedServer server = start([(GrpcServerContextPropagationInterceptor.ENABLED): false])
        Channel channel = server.applicationContext.getBean(Client).channel

        expect:
        ClientCalls.blockingUnaryCall(channel, ECHO, CallOptions.DEFAULT, 'hi') == 'hi from none'

        cleanup:
        server?.close()
    }

    private static EmbeddedServer start(Map<String, Object> extra) {
        int port = SocketUtils.findAvailableTcpPort()
        ApplicationContext.run(EmbeddedServer, [
                'spec.name'                         : 'GrpcContextPropagationSpec',
                'grpc.server.port'                  : port,
                'grpc.channels.propagation.address'  : "localhost:$port",
                'grpc.channels.propagation.plaintext': true,
        ] + extra)
    }

    static String currentTenant() {
        PropagatedContext.find().flatMap { it.find(Tenant) }.map { it.name }.orElse('none')
    }

    static class Tenant implements PropagatedContextElement {
        final String name

        Tenant(String name) {
            this.name = name
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcContextPropagationSpec')
    static class Client {
        final Channel channel

        Client(@GrpcChannel('propagation') Channel channel) {
            this.channel = channel
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'GrpcContextPropagationSpec')
    static class Services {
        @Singleton
        ServerServiceDefinition echoService() {
            ServerServiceDefinition.builder('test.Context')
                    .addMethod(ECHO, ServerCalls.asyncUnaryCall { String request, StreamObserver<String> responses ->
                        responses.onNext("$request from ${currentTenant()}".toString())
                        responses.onCompleted()
                    })
                    .build()
        }
    }

    /**
     * Sets a context element around startCall only, as an authentication interceptor would.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcContextPropagationSpec')
    static class TenantInterceptor implements ServerInterceptor, Ordered {
        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            PropagatedContext.getOrEmpty().plus(new Tenant('tenant-from-header')).propagate({ next.startCall(call, headers) } as java.util.function.Supplier)
        }

        @Override
        int getOrder() {
            return 0
        }
    }

    /**
     * A client interceptor with the default order that reads the context in its own listener before delegating.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcContextPropagationSpec')
    static class RecordingClientInterceptor implements ClientInterceptor {
        final CompletableFuture<String> seenInOnMessage = new CompletableFuture<>()
        final CompletableFuture<String> seenInOnClose = new CompletableFuture<>()

        @Override
        <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            new RecordingCall(next.newCall(method, callOptions), this)
        }
    }

    static class RecordingCall extends ForwardingClientCall.SimpleForwardingClientCall {
        final RecordingClientInterceptor recorder

        RecordingCall(ClientCall delegate, RecordingClientInterceptor recorder) {
            super(delegate)
            this.recorder = recorder
        }

        @Override
        void start(ClientCall.Listener responseListener, Metadata headers) {
            delegate().start(new RecordingListener(responseListener, recorder), headers)
        }
    }

    /**
     * Forwards to a stored listener; Groovy cannot call super methods declared in gRPC's package-private forwarding base class.
     */
    static class RecordingListener extends ClientCall.Listener {
        final ClientCall.Listener next
        final RecordingClientInterceptor recorder

        RecordingListener(ClientCall.Listener next, RecordingClientInterceptor recorder) {
            this.next = next
            this.recorder = recorder
        }

        @Override
        void onHeaders(Metadata headers) {
            next.onHeaders(headers)
        }

        @Override
        void onMessage(Object message) {
            recorder.seenInOnMessage.complete(GrpcContextPropagationSpec.currentTenant())
            next.onMessage(message)
        }

        @Override
        void onClose(Status status, Metadata trailers) {
            recorder.seenInOnClose.complete(GrpcContextPropagationSpec.currentTenant())
            next.onClose(status, trailers)
        }

        @Override
        void onReady() {
            next.onReady()
        }
    }

    static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        InputStream stream(String value) {
            new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
        }

        @Override
        String parse(InputStream stream) {
            new String(stream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }
}
