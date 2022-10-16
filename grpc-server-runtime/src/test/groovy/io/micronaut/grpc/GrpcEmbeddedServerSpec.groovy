/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.grpc

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.discovery.event.ServiceStoppedEvent
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.grpc.server.health.GrpcServerHealthIndicator
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class GrpcEmbeddedServerSpec extends Specification {

    void "test fires server startup events - no application name"() {
        when:
        GrpcEmbeddedServer embeddedServer = ApplicationContext.run(GrpcEmbeddedServer)
        EventConsumer consumer = embeddedServer.getApplicationContext().getBean(EventConsumer)

        then:
        embeddedServer.isRunning()
        consumer.startup != null
        consumer.serviceReadyEvent == null
        consumer.shutdown == null
        consumer.serviceStoppedEvent == null

        when:
        embeddedServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        consumer.shutdown != null
        consumer.serviceStoppedEvent == null
        conditions.eventually {
            embeddedServer.getServer().isTerminated()
        }
    }

    void "test fires server startup events with application name"() {
        when:
        GrpcEmbeddedServer embeddedServer = ApplicationContext.run(GrpcEmbeddedServer, [
                'micronaut.application.name': 'test'
        ])
        EventConsumer consumer = embeddedServer.getApplicationContext().getBean(EventConsumer)

        then:
        embeddedServer.isRunning()
        consumer.startup != null
        consumer.serviceReadyEvent != null
        consumer.serviceReadyEvent.getSource().id == 'test'
        consumer.shutdown == null
        consumer.serviceStoppedEvent == null

        when:
        embeddedServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        consumer.shutdown != null
        consumer.serviceStoppedEvent != null
        conditions.eventually {
            embeddedServer.getServer().isTerminated()
        }

    }

    void "test fires server startup events with application name and instance id"() {
        when:
        GrpcEmbeddedServer embeddedServer = ApplicationContext.run(GrpcEmbeddedServer, [
                'micronaut.application.name': 'test',
                'grpc.server.instance-id'   : 'test-grpc'
        ])
        EventConsumer consumer = embeddedServer.getApplicationContext().getBean(EventConsumer)

        then:
        embeddedServer.isRunning()
        consumer.serviceReadyEvent.getSource().id == 'test-grpc'

        when:
        embeddedServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        consumer.shutdown != null
        consumer.serviceStoppedEvent != null
        conditions.eventually {
            embeddedServer.getServer().isTerminated()
        }
    }

    void "test server does not exist when disabled"() {
        when:
        def context = ApplicationContext.run([
                'grpc.server.enabled': false
        ])

        then:
        !context.containsBean(GrpcEmbeddedServer)
        !context.containsBean(GrpcServerHealthIndicator)
    }

    @Singleton
    static class EventConsumer {
        ServerStartupEvent startup
        ServerShutdownEvent shutdown
        ServiceReadyEvent serviceReadyEvent
        ServiceStoppedEvent serviceStoppedEvent

        @EventListener
        void receive1(ServerStartupEvent startup) {
            this.startup = startup
        }

        @EventListener
        void receive2(ServerShutdownEvent shutdown) {
            this.shutdown = shutdown
        }

        @EventListener
        void receive3(ServiceReadyEvent serviceReadyEvent) {
            this.serviceReadyEvent = serviceReadyEvent
        }

        @EventListener
        void receive4(ServiceStoppedEvent serviceStoppedEvent) {
            this.serviceStoppedEvent = serviceStoppedEvent
        }
    }
}
