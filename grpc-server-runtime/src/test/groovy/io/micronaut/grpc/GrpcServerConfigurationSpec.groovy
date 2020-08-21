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
        def ctx = ApplicationContext.run([
            'grpc.server.port'               : port,
            'grpc.server.handshake-timeout': '11s',
            'grpc.server.instance-id'      : 'hello-grpc'
        ])

        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        ServerBuilder serverBuilder = configuration.getServerBuilder()
        def server = serverBuilder.build()
        server.start()

        expect:
        serverBuilder != null
        server.getPort() == port
        configuration.instanceId == 'hello-grpc'

        cleanup:
        server.shutdown()
        ctx.close()
    }

    void "test GRPC SSL configuration"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
            'grpc.server.port'             : port,
            'grpc.server.ssl.cert-chain'   : 'classpath:example.crt',
            'grpc.server.ssl.private-key'  : 'classpath:example.key',
        ])

        when:
        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        ServerBuilder serverBuilder = configuration.getServerBuilder()
        def server = serverBuilder.build()
        server.start()

        then:
        noExceptionThrown()

        cleanup:
        server.shutdown()
        ctx.close()
    }
}
