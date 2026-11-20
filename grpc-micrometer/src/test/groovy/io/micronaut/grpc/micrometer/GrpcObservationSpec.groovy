package io.micronaut.grpc.micrometer

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import io.grpc.ServerInterceptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.order.Ordered
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class GrpcObservationSpec extends Specification {

    static final String SERVICE = 'test.Observed'

    static final MethodDescriptor<String, String> ECHO = MethodDescriptor.<String, String> newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, 'Echo'))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build()

    void "successful and failed calls are timed on the server and the client"() {
        given:
        EmbeddedServer server = start([:])
        def context = server.applicationContext
        Channel channel = context.getBean(Client).channel
        MeterRegistry registry = context.getBean(SimpleMeterRegistry)

        when:
        String reply = ClientCalls.blockingUnaryCall(channel, ECHO, options(), 'hello')
        ClientCalls.blockingUnaryCall(channel, ECHO, options(), 'fail')

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.UNAVAILABLE
        reply == 'hello'

        and: 'the server observation stops after the call closes, so allow it a moment'
        new PollingConditions(timeout: 5).eventually {
            assert count(registry, 'grpc.server', 'OK') == 1
            assert count(registry, 'grpc.server', 'UNAVAILABLE') == 1
            assert count(registry, 'grpc.client', 'OK') == 1
            assert count(registry, 'grpc.client', 'UNAVAILABLE') == 1
        }

        cleanup:
        server?.close()
    }

    void "the interceptors run ahead of interceptors with the default order"() {
        given:
        EmbeddedServer server = start([:])
        def context = server.applicationContext

        when:
        ServerInterceptor serverInterceptor = context.getBean(ServerInterceptor, Qualifiers.byName('grpc-server-observation'))
        ClientInterceptor clientInterceptor = context.getBean(ClientInterceptor, Qualifiers.byName('grpc-client-observation'))

        then:
        (serverInterceptor as Ordered).order == GrpcObservationFactory.ORDER
        (clientInterceptor as Ordered).order == GrpcObservationFactory.ORDER
        GrpcObservationFactory.ORDER < Ordered.LOWEST_PRECEDENCE
        GrpcObservationFactory.ORDER < 0

        cleanup:
        server?.close()
    }

    void "server and client observations can be turned off separately"() {
        given:
        EmbeddedServer server = start([
                (GrpcObservationFactory.SERVER_ENABLED): serverEnabled,
                (GrpcObservationFactory.CLIENT_ENABLED): clientEnabled,
        ])
        def context = server.applicationContext

        expect:
        context.containsBean(ServerInterceptor, Qualifiers.byName('grpc-server-observation')) == serverEnabled
        context.containsBean(ClientInterceptor, Qualifiers.byName('grpc-client-observation')) == clientEnabled

        cleanup:
        server?.close()

        where:
        serverEnabled | clientEnabled
        false         | true
        true          | false
    }

    private static EmbeddedServer start(Map<String, Object> extra) {
        int port = SocketUtils.findAvailableTcpPort()
        ApplicationContext.run(EmbeddedServer, [
                'spec.name'                      : 'GrpcObservationSpec',
                'grpc.server.port'               : port,
                'grpc.channels.observed.address'  : "localhost:$port".toString(),
                'grpc.channels.observed.plaintext': true,
                // micronaut-micrometer-core also ships Micrometer's MetricCollecting* gRPC interceptors
                'micronaut.metrics.binders.grpc.enabled': false,
        ] + extra)
    }

    private static CallOptions options() {
        CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)
    }

    private static long count(MeterRegistry registry, String name, String statusCode) {
        Timer timer = registry.find(name)
                .tag('rpc.service', SERVICE)
                .tag('rpc.method', 'Echo')
                .tag('grpc.status_code', statusCode)
                .timer()
        timer == null ? 0 : timer.count()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcObservationSpec')
    static class Client {
        final Channel channel

        Client(@GrpcChannel('observed') Channel channel) {
            this.channel = channel
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'GrpcObservationSpec')
    static class Beans {

        @Singleton
        SimpleMeterRegistry simpleMeterRegistry() {
            new SimpleMeterRegistry()
        }

        @Singleton
        ServerServiceDefinition echoService() {
            ServerServiceDefinition.builder(SERVICE)
                    .addMethod(ECHO, ServerCalls.asyncUnaryCall { String request, StreamObserver<String> responses ->
                        if (request == 'fail') {
                            responses.onError(Status.UNAVAILABLE.withDescription('failing on purpose').asRuntimeException())
                        } else {
                            responses.onNext(request)
                            responses.onCompleted()
                        }
                    })
                    .build()
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
