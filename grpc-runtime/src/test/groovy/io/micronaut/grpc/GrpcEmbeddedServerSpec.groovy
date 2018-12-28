package io.micronaut.grpc

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.event.ServiceShutdownEvent
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class GrpcEmbeddedServerSpec extends Specification {

    void "test fires server startup events - no application name"() {

        when:
        GrpcEmbeddedServer embeddedServer = ApplicationContext.run(GrpcEmbeddedServer)
        EventConsumer consumer = embeddedServer.getApplicationContext().getBean(EventConsumer)

        then:
        embeddedServer.isRunning()
        consumer.startup != null
        consumer.serviceStarted == null
        consumer.shutdown == null
        consumer.serviceShutdownEvent == null

        when:
        embeddedServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        consumer.shutdown != null
        consumer.serviceShutdownEvent == null
        conditions.eventually {
            embeddedServer.getServer().isTerminated()
        }


    }

    void "test fires server startup events with application name"() {

        when:
        GrpcEmbeddedServer embeddedServer = ApplicationContext.run(GrpcEmbeddedServer, [
                'micronaut.application.name':'test'
        ])
        EventConsumer consumer = embeddedServer.getApplicationContext().getBean(EventConsumer)

        then:
        embeddedServer.isRunning()
        consumer.startup != null
        consumer.serviceStarted != null
        consumer.shutdown == null
        consumer.serviceShutdownEvent == null

        when:
        embeddedServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        consumer.shutdown != null
        consumer.serviceShutdownEvent != null
        conditions.eventually {
            embeddedServer.getServer().isTerminated()
        }

    }

    @Singleton
    static class EventConsumer {
        ServerStartupEvent startup
        ServerShutdownEvent shutdown
        ServiceStartedEvent serviceStarted
        ServiceShutdownEvent serviceShutdownEvent

        @EventListener
        void receive1(ServerStartupEvent startup) {
            this.startup = startup
        }

        @EventListener
        void receive2(ServerShutdownEvent shutdown) {
            this.shutdown = shutdown
        }

        @EventListener
        void receive3(ServiceStartedEvent serviceStarted) {
            this.serviceStarted = serviceStarted
        }

        @EventListener
        void receive4(ServiceShutdownEvent serviceShutdownEvent) {
            this.serviceShutdownEvent = serviceShutdownEvent
        }
    }
}
