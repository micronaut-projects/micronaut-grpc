package io.micronaut.grpc.server.health

import io.grpc.ServerBuilder
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.grpc.server.GrpcServerConfiguration
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.runtime.ApplicationConfiguration
import io.reactivex.internal.subscribers.BlockingFirstSubscriber
import spock.lang.Specification
import spock.lang.Unroll;
import io.micronaut.core.util.CollectionUtils

import javax.annotation.Nonnull
import javax.annotation.Nullable
import javax.inject.Singleton;

class GrpcServerHealthIndicatorSpec extends Specification {
    void "test grpc health indicator - UP"() {
        def port = SocketUtils.findAvailableTcpPort()

        given:
        GrpcEmbeddedServer server = ApplicationContext.run(GrpcEmbeddedServer, ["grpc.server.port": port])

        when:
        GrpcServerHealthIndicator healthIndicator = server.getApplicationContext().getBean(GrpcServerHealthIndicator)
        BlockingFirstSubscriber subscriber = new BlockingFirstSubscriber<HealthResult>()
        healthIndicator.result.subscribe(subscriber)
        HealthResult result = subscriber.blockingGet()

        then:
        result.status == HealthStatus.UP
        result.details.port == port
        result.details.host == "localhost"

        cleanup:
        server.stop();
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

}
