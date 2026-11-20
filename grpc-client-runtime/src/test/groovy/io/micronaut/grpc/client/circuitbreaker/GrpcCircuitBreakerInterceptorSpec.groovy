package io.micronaut.grpc.client.circuitbreaker

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.micronaut.context.ApplicationContext
import io.micronaut.retry.CircuitState
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class GrpcCircuitBreakerInterceptorSpec extends Specification {

    static final MethodDescriptor<String, String> METHOD = MethodDescriptor.<String, String> newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName('test.Breaker', 'Call'))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build()

    static final Executor DIRECT = { Runnable r -> r.run() } as Executor

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'grpc.channels.unit.circuit-breaker.enabled'          : true,
            'grpc.channels.unit.circuit-breaker.failure-threshold': 1,
            'grpc.channels.unit.circuit-breaker.reset-timeout'    : '20s',
    ])

    GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
    ClientInterceptor interceptor = registry.findInterceptor('unit').get()
    Channel next = Mock()

    void "the downstream call is created only when an admitted call starts, and never for a rejected call"() {
        given:
        RecordingListener first = new RecordingListener()
        RecordingListener second = new RecordingListener()

        when: 'a call is created'
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT.withExecutor(DIRECT), next)

        then: 'nothing reaches the channel yet, and the call is not ready'
        0 * next.newCall(*_)
        !call.isReady()

        when: 'it starts and the server is unavailable'
        call.start(first, new Metadata())

        then:
        1 * next.newCall(METHOD, _) >> new ClosingCall(Status.UNAVAILABLE)
        first.status.get(5, TimeUnit.SECONDS).code == Status.Code.UNAVAILABLE
        registry.currentState('unit').get() == CircuitState.OPEN

        when: 'another call starts while the circuit is open'
        ClientCall<String, String> rejected = interceptor.interceptCall(METHOD, CallOptions.DEFAULT.withExecutor(DIRECT), next)
        rejected.start(second, new Metadata())
        rejected.request(1)
        rejected.sendMessage('ignored')
        rejected.halfClose()

        then: 'it fails fast without creating a downstream call'
        0 * next.newCall(*_)
        second.status.get(5, TimeUnit.SECONDS).description.contains('is open')
    }

    void "a rejected call without its own executor is closed on the I/O executor"() {
        given:
        RecordingListener listener = new RecordingListener()

        when: 'a failure opens the circuit, then a call without an executor starts'
        interceptor.interceptCall(METHOD, CallOptions.DEFAULT.withExecutor(DIRECT), next)
                .start(new RecordingListener(), new Metadata())
        interceptor.interceptCall(METHOD, CallOptions.DEFAULT, next).start(listener, new Metadata())

        then:
        1 * next.newCall(*_) >> new ClosingCall(Status.UNAVAILABLE)
        listener.status.get(5, TimeUnit.SECONDS).code == Status.Code.UNAVAILABLE
        listener.thread != Thread.currentThread()
        listener.thread.name.contains('io-executor')
    }

    void "a call cancelled before it starts is closed as cancelled without reaching the channel"() {
        given:
        RecordingListener listener = new RecordingListener()
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT.withExecutor(DIRECT), next)

        when:
        call.cancel('not needed', null)
        call.start(listener, new Metadata())

        then:
        0 * next.newCall(*_)
        listener.status.get(5, TimeUnit.SECONDS).code == Status.Code.CANCELLED
        listener.status.get().description == 'not needed'
    }

    void "registering the interceptor twice guards each call once"() {
        given:
        RecordingListener listener = new RecordingListener()
        Channel twice = new Channel() {
            @Override
            <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                interceptor.interceptCall(method, callOptions, next)
            }

            @Override
            String authority() {
                'test'
            }
        }

        when:
        ClientCall<String, String> call = interceptor.interceptCall(METHOD, CallOptions.DEFAULT.withExecutor(DIRECT), twice)
        call.start(listener, new Metadata())

        then: 'the inner registration passes the call straight to the channel'
        1 * next.newCall(METHOD, _) >> new ClosingCall(Status.OK)
        listener.status.get(5, TimeUnit.SECONDS).ok
    }

    static class RecordingListener extends ClientCall.Listener<String> {
        final CompletableFuture<Status> status = new CompletableFuture<>()
        volatile Thread thread

        @Override
        void onClose(Status s, Metadata trailers) {
            thread = Thread.currentThread()
            status.complete(s)
        }
    }

    /**
     * A downstream call that closes with the given status as soon as it starts.
     */
    static class ClosingCall extends ClientCall<String, String> {
        final Status status

        ClosingCall(Status status) {
            this.status = status
        }

        @Override
        void start(ClientCall.Listener<String> listener, Metadata headers) {
            listener.onClose(status, new Metadata())
        }

        @Override
        void request(int numMessages) {
            // Nothing to do: the call is already closed.
        }

        @Override
        void cancel(String message, Throwable cause) {
            // Nothing to do: the call is already closed.
        }

        @Override
        void halfClose() {
            // Nothing to do: the call is already closed.
        }

        @Override
        void sendMessage(String message) {
            // Nothing to do: the call is already closed.
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
