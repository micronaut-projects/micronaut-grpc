package io.micronaut.grpc.discovery

import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.grpc.stub.StreamObservers
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class GrpcServiceDiscoverySpec extends Specification {

    void "test GRPC named service discovery"() {
        when:"A service is run"
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name':'greet'
        ])

        and:'then a client is run that declares the service'
        ApplicationContext client = ApplicationContext.run([
                (GrpcNameResolverFactory.ENABLED): true,
                'grpc.channels.greet.plaintext':true,
                'micronaut.http.services.greet.url': server.URL.toString()
        ])

        GreeterGrpc.GreeterFutureStub stub = client.getBean(GreeterGrpc.GreeterFutureStub)

        then:
        stub.sayHello(HelloRequest.newBuilder().setName("test").build()).get().message == 'Hello test'


        cleanup:
        client.stop()
        server.stop()
    }

    void "test GRPC channel explicit URI"() {
        when:"A service is run"
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.application.name':'greet'
        ])

        and:'then a client is run that declares the service'
        ApplicationContext client = ApplicationContext.run([
                (GrpcNameResolverFactory.ENABLED): true,
                'grpc.client.plaintext':true,
                'my.port':server.getPort()
        ])

        GreeterGrpc.GreeterStub stub = client.getBean(GreeterGrpc.GreeterStub)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)
        HelloReply reply
        stub.sayHello(HelloRequest.newBuilder().setName("test").build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply value) {
                reply = value
            }

            @Override
            void onError(Throwable t) {

            }

            @Override
            void onCompleted() {

            }
        })
        then:
        conditions.eventually {
            reply != null
        }


        cleanup:
        client.stop()
        server.stop()
    }



    @Factory
    static class Clients {
        @Singleton
        GreeterGrpc.GreeterFutureStub futureStub(@GrpcChannel("greet") ManagedChannel channel) {
            GreeterGrpc.newFutureStub(
                    channel
            )
        }

        @Singleton
        GreeterGrpc.GreeterStub reactiveStub(@GrpcChannel('http://localhost:${my.port}') ManagedChannel channel) {
            GreeterGrpc.newStub(
                    channel
            )
        }
    }
}
