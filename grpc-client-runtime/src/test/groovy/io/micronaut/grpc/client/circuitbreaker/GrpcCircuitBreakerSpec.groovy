package io.micronaut.grpc.client.circuitbreaker

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.retry.CircuitState
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    void "the circuit state is updated before the caller is notified"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config(port, 1, '20s'))
        def context = server.applicationContext
        context.getBean(FailingInterceptor).status = Status.UNAVAILABLE
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        CompletableFuture<CircuitState> stateInCallback = new CompletableFuture<>()

        when:
        GreeterGrpc.newStub(context.getBean(Client).channel).sayHello(request(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                // Nothing to do: only the error path matters to this test.
            }

            @Override
            void onError(Throwable t) {
                stateInCallback.complete(registry.currentState('breaker').get())
            }

            @Override
            void onCompleted() {
                stateInCallback.complete(null)
            }
        })

        then:
        stateInCallback.get(10, TimeUnit.SECONDS) == CircuitState.OPEN

        cleanup:
        server?.close()
    }

    void "only one trial call is let through while the circuit is half-open"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config(port, 1, '300ms'))
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        Client client = context.getBean(Client)
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        failing.status = Status.UNAVAILABLE
        callIgnoringErrors(client.stub)
        new PollingConditions(timeout: 5).eventually {
            assert registry.currentState('breaker').get() == CircuitState.HALF_OPEN
        }
        failing.status = null
        failing.hits.set(0)
        CountDownLatch release = new CountDownLatch(1)
        failing.hold = release

        when: 'a trial call is in flight'
        CompletableFuture<String> trial = CompletableFuture.supplyAsync { client.stub.sayHello(request()).message }
        new PollingConditions(timeout: 5).eventually {
            assert failing.hits.get() == 1
        }

        and: 'another call is made'
        client.stub.sayHello(request())

        then: 'it fails fast without reaching the server'
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.UNAVAILABLE
        e.status.description.contains('is open')
        failing.hits.get() == 1

        when: 'the trial call completes'
        release.countDown()

        then:
        trial.get(10, TimeUnit.SECONDS) == 'Hello Fred'
        registry.currentState('breaker').get() == CircuitState.CLOSED
        client.stub.sayHello(request()).message == 'Hello Fred'

        cleanup:
        release?.countDown()
        server?.close()
    }

    void "the call that crosses the reset timeout is the trial, without anyone reading the state first"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config(port, 1, '300ms'))
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        Client client = context.getBean(Client)
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        failing.status = Status.UNAVAILABLE
        callIgnoringErrors(client.stub)
        failing.status = null
        failing.hits.set(0)
        CountDownLatch release = new CountDownLatch(1)
        failing.hold = release
        Thread.sleep(600) // past the reset timeout; nothing reads the circuit state meanwhile

        when: 'the first call after the timeout is in flight'
        CompletableFuture<String> trial = CompletableFuture.supplyAsync { client.stub.sayHello(request()).message }
        new PollingConditions(timeout: 5).eventually {
            assert failing.hits.get() == 1
        }

        and: 'another call is made'
        client.stub.sayHello(request())

        then: 'the first call was the trial, so the second fails fast'
        StatusRuntimeException e = thrown()
        e.status.description.contains('is open')
        failing.hits.get() == 1

        when:
        release.countDown()

        then:
        trial.get(10, TimeUnit.SECONDS) == 'Hello Fred'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        cleanup:
        release?.countDown()
        server?.close()
    }

    void "a call admitted before the circuit opened does not decide the trial"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config(port, 1, '300ms'))
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        Client client = context.getBean(Client)
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        CountDownLatch slowRelease = new CountDownLatch(1)
        CountDownLatch trialRelease = new CountDownLatch(1)

        and: 'a slow call is admitted while the circuit is closed'
        failing.hold = slowRelease
        CompletableFuture<String> slow = CompletableFuture.supplyAsync { client.stub.sayHello(request()).message }
        new PollingConditions(timeout: 5).eventually {
            assert failing.hits.get() == 1
        }

        and: 'the circuit opens, and later a trial call is in flight'
        failing.status = Status.UNAVAILABLE
        callIgnoringErrors(client.stub)
        failing.status = null
        failing.hold = trialRelease
        Thread.sleep(600)
        CompletableFuture<String> trial = CompletableFuture.supplyAsync { client.stub.sayHello(request()).message }
        new PollingConditions(timeout: 5).eventually {
            assert failing.hits.get() == 3
        }

        when: 'the slow call succeeds while the trial is in flight'
        slowRelease.countDown()
        slow.get(10, TimeUnit.SECONDS)

        and:
        client.stub.sayHello(request())

        then: 'the circuit is still half-open, so new calls fail fast'
        StatusRuntimeException e = thrown()
        e.status.description.contains('is open')
        failing.hits.get() == 3

        when: 'the trial succeeds'
        trialRelease.countDown()

        then:
        trial.get(10, TimeUnit.SECONDS) == 'Hello Fred'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        cleanup:
        slowRelease?.countDown()
        trialRelease?.countDown()
        server?.close()
    }

    void "channels built twice from the same configuration count each call once"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, config(port, 2, '20s'))
        def context = server.applicationContext
        FailingInterceptor failing = context.getBean(FailingInterceptor)
        context.getBean(Client)
        SecondClient second = context.getBean(SecondClient)
        GrpcCircuitBreakerRegistry registry = context.getBean(GrpcCircuitBreakerRegistry)
        failing.status = Status.UNAVAILABLE

        when: 'one call fails on the channel built second'
        callIgnoringErrors(second.stub)

        then: 'it counts once, below the threshold of two'
        registry.currentState('breaker').get() == CircuitState.CLOSED

        when:
        callIgnoringErrors(second.stub)

        then:
        registry.currentState('breaker').get() == CircuitState.OPEN

        cleanup:
        server?.close()
    }

    private static Map<String, Object> config(int port, int threshold, String resetTimeout) {
        [
                'spec.name'                                             : 'GrpcCircuitBreakerSpec',
                'grpc.server.port'                                      : port,
                'grpc.channels.breaker.address'                         : "localhost:$port".toString(),
                'grpc.channels.breaker.plaintext'                       : true,
                'grpc.channels.breaker.circuit-breaker.enabled'          : true,
                'grpc.channels.breaker.circuit-breaker.failure-threshold': threshold,
                'grpc.channels.breaker.circuit-breaker.reset-timeout'    : resetTimeout,
        ]
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
        final Channel channel
        final GreeterGrpc.GreeterBlockingStub stub

        Client(@GrpcChannel('breaker') Channel channel) {
            this.channel = channel
            this.stub = GreeterGrpc.newBlockingStub(channel)
        }
    }

    /**
     * A second injection point for the same channel, so that its channel is built again from the same configuration.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcCircuitBreakerSpec')
    static class SecondClient {
        final GreeterGrpc.GreeterBlockingStub stub

        SecondClient(@GrpcChannel('breaker') Channel otherChannel) {
            this.stub = GreeterGrpc.newBlockingStub(otherChannel)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcCircuitBreakerSpec')
    static class FailingInterceptor implements ServerInterceptor {
        volatile Status status
        volatile CountDownLatch hold
        final AtomicInteger hits = new AtomicInteger()

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            hits.incrementAndGet()
            CountDownLatch latch
            synchronized (this) {
                // each latch holds back only the next call that reaches the server
                latch = hold
                hold = null
            }
            latch?.await(10, TimeUnit.SECONDS)
            Status current = status
            if (current != null) {
                call.close(current, new Metadata())
                return new ServerCall.Listener<ReqT>() {}
            }
            return next.startCall(call, headers)
        }
    }
}
