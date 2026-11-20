package io.micronaut.grpc.channels

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class GrpcExecutorSpec extends Specification {

    void "server handlers and client callbacks run on virtual threads with the blocking executor"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                       : 'GrpcExecutorSpec',
                'grpc.server.port'                : port,
                'grpc.server.executor'            : TaskExecutors.BLOCKING,
                'grpc.channels.executors.address'  : "localhost:$port",
                'grpc.channels.executors.plaintext': true,
                'grpc.channels.executors.executor' : TaskExecutors.BLOCKING,
        ])
        def context = server.applicationContext
        ThreadRecorder recorder = context.getBean(ThreadRecorder)
        GreeterGrpc.GreeterStub stub = context.getBean(Client).stub
        CompletableFuture<Thread> callbackThread = new CompletableFuture<>()

        when:
        stub.sayHello(HelloRequest.newBuilder().setName('Fred').build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                callbackThread.complete(Thread.currentThread())
            }

            @Override
            void onError(Throwable t) {
                callbackThread.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // Nothing to do: the reply is handled in onNext.
            }
        })

        then:
        callbackThread.get(10, TimeUnit.SECONDS).virtual
        recorder.serverThread.virtual

        cleanup:
        server?.close()
    }

    void "the global client executor applies to every channel"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                       : 'GrpcExecutorSpec',
                'grpc.server.port'                : port,
                'grpc.client.executor'            : TaskExecutors.BLOCKING,
                'grpc.channels.executors.address'  : "localhost:$port",
                'grpc.channels.executors.plaintext': true,
        ])
        GreeterGrpc.GreeterStub stub = server.applicationContext.getBean(Client).stub
        CompletableFuture<Thread> callbackThread = new CompletableFuture<>()

        when:
        stub.sayHello(HelloRequest.newBuilder().setName('Fred').build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                callbackThread.complete(Thread.currentThread())
            }

            @Override
            void onError(Throwable t) {
                callbackThread.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // Nothing to do: the reply is handled in onNext.
            }
        })

        then:
        callbackThread.get(10, TimeUnit.SECONDS).virtual

        cleanup:
        server?.close()
    }

    void "an unknown executor name fails with a clear message"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                       : 'GrpcExecutorSpec',
                'grpc.server.enabled'             : false,
                'grpc.channels.executors.address'  : 'localhost:1',
                'grpc.channels.executors.plaintext': true,
                'grpc.channels.executors.executor' : 'missing',
        ])

        when:
        context.getBean(Client)

        then:
        BeanInstantiationException e = thrown()
        e.message.contains('No executor bean named [missing] is available for gRPC channel [executors]')

        cleanup:
        context.close()
    }

    void "an Executor instance from a programmatic property source is used directly"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        ExecutorService custom = Executors.newSingleThreadExecutor({ Runnable r -> new Thread(r, 'custom-grpc-callbacks') } as ThreadFactory)
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                       : 'GrpcExecutorSpec',
                'grpc.server.port'                : port,
                'grpc.channels.executors.address'  : "localhost:$port".toString(),
                'grpc.channels.executors.plaintext': true,
                'grpc.channels.executors.executor' : custom,
        ])
        GreeterGrpc.GreeterStub stub = server.applicationContext.getBean(Client).stub

        expect:
        callbackThread(stub).name == 'custom-grpc-callbacks'

        cleanup:
        server?.close()
        custom?.shutdownNow()
    }

    void "the executor follows the channel configuration matched for the injection point"() {
        given: 'the injection point uses a different letter case than the configuration key'
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                       : 'GrpcExecutorSpec-case',
                'grpc.server.port'                : port,
                'grpc.channels.executors.address'  : "localhost:$port".toString(),
                'grpc.channels.executors.plaintext': true,
                'grpc.channels.executors.executor' : TaskExecutors.BLOCKING,
        ])
        GreeterGrpc.GreeterStub stub = server.applicationContext.getBean(UpperCaseClient).stub

        expect:
        callbackThread(stub).virtual

        cleanup:
        server?.close()
    }

    private static Thread callbackThread(GreeterGrpc.GreeterStub stub) {
        CompletableFuture<Thread> callbackThread = new CompletableFuture<>()
        stub.sayHello(HelloRequest.newBuilder().setName('Fred').build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                callbackThread.complete(Thread.currentThread())
            }

            @Override
            void onError(Throwable t) {
                callbackThread.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // Nothing to do: the reply is handled in onNext.
            }
        })
        callbackThread.get(10, TimeUnit.SECONDS)
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcExecutorSpec-case')
    static class UpperCaseClient {
        final GreeterGrpc.GreeterStub stub

        UpperCaseClient(@GrpcChannel('EXECUTORS') Channel channel) {
            this.stub = GreeterGrpc.newStub(channel)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcExecutorSpec')
    static class Client {
        final GreeterGrpc.GreeterStub stub

        Client(@GrpcChannel('executors') Channel channel) {
            this.stub = GreeterGrpc.newStub(channel)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcExecutorSpec')
    static class ThreadRecorder implements ServerInterceptor {
        volatile Thread serverThread

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            serverThread = Thread.currentThread()
            return next.startCall(call, headers)
        }
    }
}
