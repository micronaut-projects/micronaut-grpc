package helloworld

import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Property(name = "spec.name", value = "NamedChannelClientsTest")
@Property(name = "grpc.server.port", value = "50162")
@Property(name = "grpc.channels.greeter.address", value = "localhost:50162")
@Property(name = "grpc.channels.greeter.plaintext", value = "true")
@MicronautTest
class NamedChannelClientsTest extends Specification {

    @Inject
    GreeterGrpc.GreeterStub greeterStub

    void "test named channel"() {
        given:
        CompletableFuture<String> message = new CompletableFuture<>()

        when:
        greeterStub.sayHello(HelloRequest.newBuilder().setName("Fred").build(), new StreamObserver<HelloReply>() {
            @Override
            void onNext(HelloReply reply) {
                message.complete(reply.message)
            }

            @Override
            void onError(Throwable t) {
                message.completeExceptionally(t)
            }

            @Override
            void onCompleted() {
                // the reply is already published by onNext; nothing to do on completion
            }
        })

        then:
        message.get(10, TimeUnit.SECONDS) == "Hello Fred"
    }
}
