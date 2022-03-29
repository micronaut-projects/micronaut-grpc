package io.micronaut.grpc.server


import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.internal.ServerImpl
import io.grpc.netty.NettyServerBuilder
import io.micronaut.core.order.Ordered
import io.micronaut.grpc.server.interceptor.OrderedServerInterceptor
import spock.lang.Specification

class GrpcServerBuilderSpec extends Specification {

    def "test interceptor order - all implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new OrderedServerInterceptor(Mock(ServerInterceptor), 4),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 1),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 3),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 0),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 2)
        ]

        when:
        ServerBuilder<?> serverBuilder = grpcServerBuilder.serverBuilder(mockGrpcConfiguration, null, interceptors, null)

        then:
        1 * mockGrpcConfiguration.serverBuilder >> NettyServerBuilder.forPort(8080)
        0 * _

        and:
        serverBuilder

        when:
        Server server = serverBuilder.build()
        ServerInterceptor[] serverInterceptors = ((ServerImpl) server).interceptors

        then:
        serverInterceptors.length == 5
        (serverInterceptors[0] as OrderedServerInterceptor).order == 4
        (serverInterceptors[1] as OrderedServerInterceptor).order == 3
        (serverInterceptors[2] as OrderedServerInterceptor).order == 2
        (serverInterceptors[3] as OrderedServerInterceptor).order == 1
        (serverInterceptors[4] as OrderedServerInterceptor).order == 0

        cleanup:
        server.shutdown().awaitTermination()
    }

    def "test interceptor order - some implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new OrderedServerInterceptor(Mock(ServerInterceptor), 3),
                new NonOrderedServerInterceptor("first"),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 2),
                new NonOrderedServerInterceptor("second"),
                new OrderedServerInterceptor(Mock(ServerInterceptor), 4)
        ]

        when:
        ServerBuilder<?> serverBuilder = grpcServerBuilder.serverBuilder(mockGrpcConfiguration, null, interceptors, null)

        then:
        1 * mockGrpcConfiguration.serverBuilder >> NettyServerBuilder.forPort(8080)
        0 * _

        and:
        serverBuilder

        when:
        Server server = serverBuilder.build()
        ServerInterceptor[] serverInterceptors = ((ServerImpl) server).interceptors

        then:
        serverInterceptors.length == 5
        (serverInterceptors[0] as NonOrderedServerInterceptor).name == "first"
        (serverInterceptors[1] as NonOrderedServerInterceptor).name == "second"
        (serverInterceptors[2] as OrderedServerInterceptor).order == 4
        (serverInterceptors[3] as OrderedServerInterceptor).order == 3
        (serverInterceptors[4] as OrderedServerInterceptor).order == 2

        cleanup:
        server.shutdown().awaitTermination()
    }

    def "test interceptor order - none implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new NonOrderedServerInterceptor("first"),
                new NonOrderedServerInterceptor("second"),
                new NonOrderedServerInterceptor("third"),
                new NonOrderedServerInterceptor("fourth"),
                new NonOrderedServerInterceptor("fifth")
        ]

        when:
        ServerBuilder<?> serverBuilder = grpcServerBuilder.serverBuilder(mockGrpcConfiguration, null, interceptors, null)

        then:
        1 * mockGrpcConfiguration.serverBuilder >> NettyServerBuilder.forPort(8080)
        0 * _

        and:
        serverBuilder

        when:
        Server server = serverBuilder.build()
        ServerInterceptor[] serverInterceptors = ((ServerImpl) server).interceptors

        then:
        serverInterceptors.length == 5
        (serverInterceptors[0] as NonOrderedServerInterceptor).name == "first"
        (serverInterceptors[1] as NonOrderedServerInterceptor).name == "second"
        (serverInterceptors[2] as NonOrderedServerInterceptor).name == "third"
        (serverInterceptors[3] as NonOrderedServerInterceptor).name == "fourth"
        (serverInterceptors[4] as NonOrderedServerInterceptor).name == "fifth"

        cleanup:
        server.shutdown().awaitTermination()
    }

    private static class NonOrderedServerInterceptor implements ServerInterceptor {
        private final String name

        private NonOrderedServerInterceptor(final String name) {
            this.name = name
        }

        @Override
        <T, S> ServerCall.Listener<T> interceptCall(final ServerCall<T, S> call, final Metadata headers, final ServerCallHandler<T, S> next) {
            new ServerCall.Listener<T>() {}
        }

    }

}
