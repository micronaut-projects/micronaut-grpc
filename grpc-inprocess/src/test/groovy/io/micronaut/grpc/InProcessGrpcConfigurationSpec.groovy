/*
 * Copyright 2017-2026 original authors
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

import io.grpc.ServerBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.grpc.server.GrpcEmbeddedServer
import spock.lang.Specification

class InProcessGrpcConfigurationSpec extends Specification {

    void "test in-process server builder replaces netty server builder"() {
        given:
        def ctx = ApplicationContext.run([
                'grpc.server.in-process-name': 'grpc-test'
        ])

        when:
        ServerBuilder<?> serverBuilder = ctx.getBean(ServerBuilder)
        GrpcEmbeddedServer embeddedServer = ctx.getBean(GrpcEmbeddedServer)
        def server = serverBuilder.build()
        server.start()

        then:
        serverBuilder instanceof InProcessServerBuilder
        embeddedServer.URI.toString() == 'in-process:grpc-test'
        server.port == -1

        cleanup:
        server.shutdown().awaitTermination()
        ctx.close()
    }

    void "test in-process server does not support SSL"() {
        given:
        def ctx = ApplicationContext.run([
                'grpc.server.in-process-name': 'grpc-test',
                'grpc.server.ssl.cert-chain' : 'classpath:example.crt',
                'grpc.server.ssl.private-key': 'classpath:example.key',
        ])

        when:
        ctx.getBean(ServerBuilder)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('SSL is not supported for in-process gRPC servers')
        e.cause instanceof ConfigurationException

        cleanup:
        ctx.close()
    }
}
