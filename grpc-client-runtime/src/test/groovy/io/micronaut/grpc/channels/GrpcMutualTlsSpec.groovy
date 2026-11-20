package io.micronaut.grpc.channels

import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloRequest
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class GrpcMutualTlsSpec extends Specification {

    void "a server that requires client certificates accepts only clients with a trusted certificate"() {
        given:
        EmbeddedServer server = start('require')
        Clients clients = server.applicationContext.getBean(Clients)

        expect: 'a client with a certificate signed by the trusted CA gets through'
        sayHello(clients.trusted) == 'Hello Fred'

        when: 'a client presents no certificate'
        sayHello(clients.anonymous)

        then:
        StatusRuntimeException noCert = thrown()
        noCert.status.code == Status.Code.UNAVAILABLE

        when: 'a client presents a certificate the server does not trust'
        sayHello(clients.untrusted)

        then:
        StatusRuntimeException badCert = thrown()
        badCert.status.code == Status.Code.UNAVAILABLE

        cleanup:
        server?.close()
    }

    void "optional client authentication accepts clients with and without a certificate"() {
        given:
        EmbeddedServer server = start('optional')
        Clients clients = server.applicationContext.getBean(Clients)

        expect:
        sayHello(clients.anonymous) == 'Hello Fred'
        sayHello(clients.trusted) == 'Hello Fred'

        cleanup:
        server?.close()
    }

    void "the client verifies the server against its trust certificates"() {
        given: 'the server certificate comes from a private CA the JVM does not trust'
        EmbeddedServer server = start('none')
        Clients clients = server.applicationContext.getBean(Clients)

        expect: 'a client trusting that CA connects'
        sayHello(clients.anonymous) == 'Hello Fred'

        when: 'a client relying on the default trust store connects'
        sayHello(clients.defaultTrust)

        then:
        StatusRuntimeException e = thrown()
        e.status.code == Status.Code.UNAVAILABLE

        cleanup:
        server?.close()
    }

    void "grpc.client.ssl provides defaults for channels without their own ssl settings"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, serverConfig(port, 'require') + [
                'spec.name'                          : 'GrpcMutualTlsSpec-defaults',
                'grpc.client.ssl.trust-certificates'  : 'classpath:mtls/ca.crt',
                'grpc.client.ssl.cert-chain'          : 'classpath:mtls/client.crt',
                'grpc.client.ssl.private-key'         : 'classpath:mtls/client.key',
                'grpc.channels.defaulted.address'     : "localhost:$port".toString(),
        ])

        expect:
        sayHello(server.applicationContext.getBean(DefaultsClient).channel) == 'Hello Fred'

        cleanup:
        server?.close()
    }

    void "client authentication without a server certificate is rejected"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'grpc.server.enabled'    : false,
                'grpc.server.ssl.client-auth': 'require',
        ])

        when:
        context.getBean(GrpcServerConfiguration)

        then:
        BeanInstantiationException e = thrown()
        e.message.contains("'trust-certificates' and 'client-auth' require 'cert-chain' and 'private-key'")

        cleanup:
        context.close()
    }

    void "a client certificate without its private key is rejected"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name'                              : 'GrpcMutualTlsSpec-defaults',
                'grpc.server.enabled'                    : false,
                'grpc.channels.defaulted.address'        : 'localhost:1',
                'grpc.channels.defaulted.ssl.cert-chain' : 'classpath:mtls/client.crt',
        ])

        when:
        context.getBean(DefaultsClient)

        then:
        BeanInstantiationException e = thrown()
        e.message.contains("must be configured for a client certificate")

        cleanup:
        context.close()
    }

    private static EmbeddedServer start(String clientAuth) {
        int port = SocketUtils.findAvailableTcpPort()
        String address = "localhost:$port".toString()
        ApplicationContext.run(EmbeddedServer, serverConfig(port, clientAuth) + [
                'spec.name'                                     : 'GrpcMutualTlsSpec',
                'grpc.channels.trusted.address'                 : address,
                'grpc.channels.trusted.ssl.trust-certificates'  : 'classpath:mtls/ca.crt',
                'grpc.channels.trusted.ssl.cert-chain'          : 'classpath:mtls/client.crt',
                'grpc.channels.trusted.ssl.private-key'         : 'classpath:mtls/client.key',
                'grpc.channels.anonymous.address'               : address,
                'grpc.channels.anonymous.ssl.trust-certificates': 'classpath:mtls/ca.crt',
                'grpc.channels.untrusted.address'               : address,
                'grpc.channels.untrusted.ssl.trust-certificates': 'classpath:mtls/ca.crt',
                'grpc.channels.untrusted.ssl.cert-chain'        : 'classpath:mtls/untrusted.crt',
                'grpc.channels.untrusted.ssl.private-key'       : 'classpath:mtls/untrusted.key',
                'grpc.channels.default-trust.address'           : address,
        ])
    }

    private static Map<String, Object> serverConfig(int port, String clientAuth) {
        [
                'grpc.server.port'                  : port,
                'grpc.server.ssl.cert-chain'        : 'classpath:mtls/server.crt',
                'grpc.server.ssl.private-key'       : 'classpath:mtls/server.key',
                'grpc.server.ssl.trust-certificates': 'classpath:mtls/ca.crt',
                'grpc.server.ssl.client-auth'       : clientAuth,
        ]
    }

    private static String sayHello(Channel channel) {
        GreeterGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .sayHello(HelloRequest.newBuilder().setName('Fred').build())
                .message
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcMutualTlsSpec')
    static class Clients {
        final Channel trusted
        final Channel anonymous
        final Channel untrusted
        final Channel defaultTrust

        Clients(@GrpcChannel('trusted') Channel trusted,
                @GrpcChannel('anonymous') Channel anonymous,
                @GrpcChannel('untrusted') Channel untrusted,
                @GrpcChannel('default-trust') Channel defaultTrust) {
            this.trusted = trusted
            this.anonymous = anonymous
            this.untrusted = untrusted
            this.defaultTrust = defaultTrust
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcMutualTlsSpec-defaults')
    static class DefaultsClient {
        final Channel channel

        DefaultsClient(@GrpcChannel('defaulted') Channel channel) {
            this.channel = channel
        }
    }
}
