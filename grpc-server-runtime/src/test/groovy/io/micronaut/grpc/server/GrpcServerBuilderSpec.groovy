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
import spock.lang.Specification

class GrpcServerBuilderSpec extends Specification {


    def "test interceptor order - all implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new OrderableServerInterceptor(4),
                new OrderableServerInterceptor(1),
                new OrderableServerInterceptor(3),
                new OrderableServerInterceptor(0),
                new OrderableServerInterceptor(2)
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
        (serverInterceptors[0] as Ordered).order == 0
        (serverInterceptors[1] as Ordered).order == 1
        (serverInterceptors[2] as Ordered).order == 2
        (serverInterceptors[3] as Ordered).order == 3
        (serverInterceptors[4] as Ordered).order == 4
    }

    def "test interceptor order - some implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new OrderableServerInterceptor(4),
                new NonOrderableServerInterceptor("first"),
                new OrderableServerInterceptor(3),
                new NonOrderableServerInterceptor("second"),
                new OrderableServerInterceptor(2)
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
        (serverInterceptors[0] as Ordered).order == 2
        (serverInterceptors[1] as Ordered).order == 3
        (serverInterceptors[2] as Ordered).order == 4
        (serverInterceptors[3] as NonOrderableServerInterceptor).name == "first"
        (serverInterceptors[4] as NonOrderableServerInterceptor).name == "second"
    }

    def "test interceptor order - none implement Ordered"() {
        given:
        GrpcServerBuilder grpcServerBuilder = new GrpcServerBuilder()

        GrpcServerConfiguration mockGrpcConfiguration = Mock()
        List<ServerInterceptor> interceptors = [
                new NonOrderableServerInterceptor("first"),
                new NonOrderableServerInterceptor("second"),
                new NonOrderableServerInterceptor("third"),
                new NonOrderableServerInterceptor("fourth"),
                new NonOrderableServerInterceptor("fifth")
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
        (serverInterceptors[0] as NonOrderableServerInterceptor).name == "first"
        (serverInterceptors[1] as NonOrderableServerInterceptor).name == "second"
        (serverInterceptors[2] as NonOrderableServerInterceptor).name == "third"
        (serverInterceptors[3] as NonOrderableServerInterceptor).name == "fourth"
        (serverInterceptors[4] as NonOrderableServerInterceptor).name == "fifth"
    }

    private static class OrderableServerInterceptor implements ServerInterceptor, Ordered {

        private final int order

        OrderableServerInterceptor(final int order) {
            this.order = order
        }

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call, final Metadata headers, final ServerCallHandler<ReqT, RespT> next) {
            return new ServerCall.Listener<ReqT>() {
                @Override
                void onMessage(final ReqT message) {
                    super.onMessage(message)
                }
            }
        }

        @Override
        int getOrder() {
            return order
        }

    }

    private static class NonOrderableServerInterceptor implements ServerInterceptor {

        private final String name

        NonOrderableServerInterceptor(final String name) {
            this.name = name
        }

        @Override
        def <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call, final Metadata headers, final ServerCallHandler<ReqT, RespT> next) {
            return new ServerCall.Listener<ReqT>() {
                @Override
                void onMessage(final ReqT message) {
                    super.onMessage(message)
                }
            }
        }
    }

}
