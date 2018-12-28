package io.micronaut.grpc

import io.grpc.ServerBuilder
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.server.GrpcServerConfiguration
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

@MicronautTest
class GrpcServerConfigurationSpec extends Specification {

    void "test GRPC configuration"() {

        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(
                'grpc.server.port': port,
                'grpc.server.handshake-timeout':'11s'
        )

        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        ServerBuilder serverBuilder = configuration.getServerBuilder()
        def server = serverBuilder.build()
        server.start()

        expect:
        serverBuilder != null
        server.getPort() == port

        cleanup:
        server.shutdown()
        ctx.close()
    }
}
