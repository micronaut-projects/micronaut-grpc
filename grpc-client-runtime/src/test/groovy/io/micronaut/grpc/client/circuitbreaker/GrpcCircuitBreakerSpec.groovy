package io.micronaut.grpc.client.circuitbreaker

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloRequest
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.retry.CircuitState
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicInteger

class GrpcCircuitBreakerSpec extends Specification {

    void "configuration defaults"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        def config = GrpcCircuitBreakerConfiguration.of(context.environment, 'orders')

        then:
        !config.enabled()
        config.failureThreshold() == 5
        config.resetTimeout() == GrpcCircuitBreakerConfiguration.DEFAULT_RESET_TIMEOUT
        config.failureStatusCodes() == [Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED] as Set
        !context.getBean(GrpcCircuitBreakerRegistry).currentState('orders').present

        cleanup:
        context.close()
    }

    void "configuration reads channel properties"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'grpc.channels.orders.circuit-breaker.enabled'             : true,
                'grpc.channels.orders.circuit-breaker.failure-threshold'   : 3,
                'grpc.channels.orders.circuit-breaker.reset-timeout'       : '2s',
                'grpc.channels.orders.circuit-breaker.failure-status-codes': ['unavailable', 'resource-exhausted'],
        ])

        when:
        def config = GrpcCircuitBreakerConfiguration.of(context.environment, 'orders')

        then:
        config.enabled()
        config.failureThreshold() == 3
        config.resetTimeout().seconds == 2
        config.failureStatusCodes() == [Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED] as Set
        context.getBean(GrpcCircuitBreakerRegistry).currentState('orders').get() == CircuitState.CLOSED

        cleanup:
        context.close()
    }

    void "circuit opens after consecutive failures, fails fast, then closes after a successful trial call"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                             : 'GrpcCircuitBreakerSpec',
                'grpc.server.port'                                      : port,
                'grpc.channels.breaker.address'                         : "localhost:$port",
                'grpc.channels.breaker.plaintext'                       : true,
                'grpc.channels.breaker.circuit-breaker.enabled'          : true,
                'grpc.channels.breaker.circuit-breaker.failure-threshold': 2,
                'grpc.channels.breaker.circuit-breaker.reset-timeout'    : '500ms',
        ])
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        GreeterGrpc.GreeterBlockingStub stub = context.getBean(Client).stub
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)

        expect: 'calls go through while the circuit is closed'
        stub.sayHello(request()).message == 'Hello Fred'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        when: 'an application error does not count as a failure'
        failing.hits.set(0)
        failing.status = Status.INVALID_ARGUMENT
        3.times { callIgnoringErrors(stub) }

        then:
        failing.hits.get() == 3
        registry.currentState('breaker').get() == CircuitState.CLOSED

        when: 'the server becomes unavailable'
        failing.hits.set(0)
        failing.status = Status.UNAVAILABLE
        callIgnoringErrors(stub)

        then: 'one failure is below the threshold'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        when:
        callIgnoringErrors(stub)

        then: 'the second consecutive failure opens the circuit'
        failing.hits.get() == 2
        registry.currentState('breaker').get() == CircuitState.OPEN

        when: 'a call is made while the circuit is open'
        stub.sayHello(request())

        then: 'it fails fast without reaching the server'
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.UNAVAILABLE
        e.status.description.contains('Circuit breaker for gRPC channel [breaker] is open')
        failing.hits.get() == 2

        when: 'the server recovers and the reset timeout passes'
        failing.status = null
        new PollingConditions(timeout: 5).eventually {
            assert registry.currentState('breaker').get() == CircuitState.HALF_OPEN
        }

        then: 'the trial call succeeds and closes the circuit'
        stub.sayHello(request()).message == 'Hello Fred'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        cleanup:
        server?.close()
    }

    void "a failed trial call opens the circuit again"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                             : 'GrpcCircuitBreakerSpec',
                'grpc.server.port'                                      : port,
                'grpc.channels.breaker.address'                         : "localhost:$port",
                'grpc.channels.breaker.plaintext'                       : true,
                'grpc.channels.breaker.circuit-breaker.enabled'          : true,
                'grpc.channels.breaker.circuit-breaker.failure-threshold': 1,
                'grpc.channels.breaker.circuit-breaker.reset-timeout'    : '300ms',
        ])
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        GreeterGrpc.GreeterBlockingStub stub = context.getBean(Client).stub
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        failing.status = Status.UNAVAILABLE

        when:
        callIgnoringErrors(stub)

        then:
        registry.currentState('breaker').get() == CircuitState.OPEN

        when:
        new PollingConditions(timeout: 5).eventually {
            assert registry.currentState('breaker').get() == CircuitState.HALF_OPEN
        }
        callIgnoringErrors(stub)

        then:
        failing.hits.get() == 2
        registry.currentState('breaker').get() == CircuitState.OPEN

        cleanup:
        server?.close()
    }

    private static HelloRequest request() {
        HelloRequest.newBuilder().setName('Fred').build()
    }

    private static void callIgnoringErrors(GreeterGrpc.GreeterBlockingStub stub) {
        try {
            stub.sayHello(request())
        } catch (StatusRuntimeException ignored) {
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcCircuitBreakerSpec')
    static class Client {
        final GreeterGrpc.GreeterBlockingStub stub

        Client(@GrpcChannel('breaker') Channel channel) {
            this.stub = GreeterGrpc.newBlockingStub(channel)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcCircuitBreakerSpec')
    static class FailingInterceptor implements ServerInterceptor {
        volatile Status status
        final AtomicInteger hits = new AtomicInteger()

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            hits.incrementAndGet()
            Status current = status
            if (current != null) {
                call.close(current, new Metadata())
                return new ServerCall.Listener<ReqT>() {}
            }
            return next.startCall(call, headers)
        }
    }
}
