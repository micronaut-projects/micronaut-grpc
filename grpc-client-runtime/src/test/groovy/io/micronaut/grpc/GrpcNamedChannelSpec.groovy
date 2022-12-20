package io.micronaut.grpc

import io.grpc.Channel
import io.grpc.examples.helloworld.Greeter2Grpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.channels.GrpcManagedChannelConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Retry
import spock.lang.Specification

class GrpcNamedChannelSpec extends Specification {

    // retry because on Cloud CI you may have a race condition regarding port availability and binding
    @Retry
    void "test named client"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'grpc.server.port'               : port,
                'grpc.channels.greeter.address'  : "localhost:$port",
                'grpc.channels.greeter.plaintext': true
        ])
        def context = embeddedServer.getApplicationContext()
        def testBean = context.getBean(TestBean)
        def config = context.getBean(GrpcManagedChannelConfiguration, Qualifiers.byName("greeter"))
        def channel = testBean.blockingStub.channel
        expect:
        channel != null

        testBean.sayHello("Fred") == "Hello 2 Fred"
        config.name == 'greeter'

        cleanup:
        embeddedServer.close()
    }

    void "test named client times out with connect-on-startup"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'grpc.server.port'                        : port,
                'grpc.channels.greeter.address'           : "localhost:${SocketUtils.findAvailableTcpPort()}",
                'grpc.channels.greeter.plaintext'         : true,
                'grpc.channels.greeter.connect-on-startup': true,
                'grpc.channels.greeter.connection-timeout': 10
        ])
        def context = embeddedServer.getApplicationContext()

        when:
        context.getBean(TestBean)

        then:
        def ex = thrown(BeanInstantiationException)
        def cause = ex.getCause()
        cause.getClass() == IllegalStateException
        cause.message == "Unable to connect to the channel: greeter"

        cleanup:
        embeddedServer.close()
    }

    @Retry
    void "test named client successfully with connect-on-startup"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'grpc.server.port'                        : port,
                'grpc.channels.greeter.address'           : "localhost:$port",
                'grpc.channels.greeter.plaintext'         : true,
                'grpc.channels.greeter.connect-on-startup': true,
                'grpc.channels.greeter.connection-timeout': 5
        ])
        def context = embeddedServer.getApplicationContext()
        def testBean = context.getBean(TestBean)
        def config = context.getBean(GrpcManagedChannelConfiguration, Qualifiers.byName("greeter"))
        def channel = testBean.blockingStub.channel

        expect:
        channel != null

        testBean.sayHello("Fred") == "Hello 2 Fred"
        config.name == 'greeter'

        cleanup:
        embeddedServer.close()
    }

    @Retry
    void "test named client - eager init"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext
                .builder()
                .eagerInitSingletons(true)
                .properties([
                        'my.port'                        : SocketUtils.findAvailableTcpPort(),
                        'grpc.server.port'               : port,
                        'grpc.channels.greeter.address'  : "localhost:$port",
                        'grpc.channels.greeter.plaintext': true
                ])
                .run(EmbeddedServer)
        def context = embeddedServer.getApplicationContext()
        def testBean = context.getBean(TestBean)
        def config = context.getBean(GrpcManagedChannelConfiguration, Qualifiers.byName("greeter"))
        def channel = testBean.blockingStub.channel

        expect:
        channel != null

        testBean.sayHello("Fred") == "Hello 2 Fred"
        config.name == 'greeter'

        cleanup:
        embeddedServer.close()
    }

    @Singleton
    static class TestBean {
        @Inject
        Greeter2Grpc.Greeter2BlockingStub blockingStub

        String sayHello(String message) {
            blockingStub.sayHello(
                    HelloRequest.newBuilder().setName(message).build()
            ).message
        }
    }


    @Factory
    static class Clients {
        @Singleton
        Greeter2Grpc.Greeter2BlockingStub blockingStub(@GrpcChannel("greeter") Channel channel) {
            Greeter2Grpc.newBlockingStub(channel)
        }

    }

    @Singleton
    static class GreeterImpl extends Greeter2Grpc.Greeter2ImplBase {
        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello 2 " + request.getName()).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
