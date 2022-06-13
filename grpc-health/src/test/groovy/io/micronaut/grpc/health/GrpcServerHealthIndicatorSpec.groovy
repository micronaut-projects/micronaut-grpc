package io.micronaut.grpc.health

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.util.CollectionUtils
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

class GrpcServerHealthIndicatorSpec extends Specification {
    void "test grpc health indicator - UP"() {
        def port = SocketUtils.findAvailableTcpPort()

        given:
        GrpcEmbeddedServer server = ApplicationContext.run(GrpcEmbeddedServer, ["grpc.server.port": port])

        when:
        GrpcServerHealthIndicator healthIndicator = server.getApplicationContext().getBean(GrpcServerHealthIndicator)
        HealthResult result = Mono.from(healthIndicator.result).block()

        then:
        result.status == HealthStatus.UP
        result.details.port == port
        result.details.host == "localhost"

        cleanup:
        server.close()
    }

    @Unroll
    void "test grpc health indicator - Disabled"() {
        given:
        GrpcEmbeddedServer server = ApplicationContext.run(GrpcEmbeddedServer, CollectionUtils.mapOf(
                "grpc.server.health.enabled", configvalue))

        when:
        def optional = server.getApplicationContext().findBean(GrpcServerHealthIndicator)

        then:
        !optional.isPresent()

        cleanup:
        server.stop()

        where:
        configvalue << [false, "false", "no", ""]
    }

    void "test grpc health indicator after gRPC-Server is stopped"() {

        given:
        GrpcEmbeddedServer server = ApplicationContext.run(GrpcEmbeddedServer)

        when:
        server.stop()

        and:
        GrpcServerHealthIndicator healthIndicator = server.getApplicationContext().getBean(GrpcServerHealthIndicator)
        HealthResult result = Mono.from(healthIndicator.result).block()

        then:
        result.status == HealthStatus.DOWN
        result.details.port == "N/A"
        result.details.host == server.host

        cleanup:
        server.close()
    }

}
