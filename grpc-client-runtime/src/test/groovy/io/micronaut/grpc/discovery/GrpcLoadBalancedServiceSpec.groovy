package io.micronaut.grpc.discovery

import io.grpc.ManagedChannel
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.examples.helloworld.MultiNodeGreeterGrpc
import io.grpc.stub.StreamObserver
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

class GrpcLoadBalancedServiceSpec extends Specification {

    void "test GRPC named service discovery with round robin load balancing"() {

        given: "A service is run on multiple servers"
        def port1 = SocketUtils.findAvailableTcpPort()
        def port2 = SocketUtils.findAvailableTcpPort()
        def port3 = SocketUtils.findAvailableTcpPort()

        EmbeddedServer server1 = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name': 'greet',
                'grpc.server.port'          : port1
        ])

        EmbeddedServer server2 = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name': 'greet',
                'grpc.server.port'          : port2
        ])

        EmbeddedServer server3 = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name': 'greet',
                'grpc.server.port'          : port3
        ])

        and: 'then a client is run that declares the service'
        ApplicationContext client = ApplicationContext.run([
                (GrpcNameResolverProvider.ENABLED) : true,
                'grpc.channels.greet.plaintext'    : true,
                'grpc.channels.greet.default-load-balancing-policy' : 'round_robin',
                'micronaut.http.services.greet.urls[0]': server1.URL.toString(),
                'micronaut.http.services.greet.urls[1]': server2.URL.toString(),
                'micronaut.http.services.greet.urls[2]': server3.URL.toString()
        ])

        when: 'the service is called three times'
        MultiNodeGreeterGrpc.MultiNodeGreeterFutureStub stub = client.getBean(MultiNodeGreeterGrpc.MultiNodeGreeterFutureStub)
        Set<String> results = new HashSet<>()
        for (int i=0; i<3; i++) {
            results.add(stub.sayHello(HelloRequest.newBuilder().setName("test").build()).get().message)
        }

        then: 'the calls are load balanced across the 3 different servers'
        results.size() == 3

        cleanup:
        client.stop()
        server1.stop()
        server2.stop()
        server3.stop()
    }

    @Factory
    static class Clients {
        @Singleton
        MultiNodeGreeterGrpc.MultiNodeGreeterFutureStub futureStub(@GrpcChannel("greet") ManagedChannel channel) {
            MultiNodeGreeterGrpc.newFutureStub(
                    channel
            )
        }
    }

    @Singleton
    static class MultiNodeGreeterImpl extends MultiNodeGreeterGrpc.MultiNodeGreeterImplBase {

        @Value('${grpc.server.port}')
        Integer port

        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName() + " from " + port).build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
