package io.micronaut.grpc

import io.grpc.Channel
import io.grpc.examples.helloworld.Greeter2Grpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
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
