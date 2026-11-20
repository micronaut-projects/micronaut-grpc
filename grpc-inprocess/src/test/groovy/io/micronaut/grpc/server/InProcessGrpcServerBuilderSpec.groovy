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
package io.micronaut.grpc.server

import io.grpc.ServerBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class InProcessGrpcServerBuilderSpec extends Specification {

    void "test in-process server builder applies inbound limits"() {
        given:
        def ctx = ApplicationContext.run([
                'grpc.server.in-process-name'          : 'grpc-test',
                'grpc.server.max-inbound-message-size' : 1024,
                'grpc.server.max-inbound-metadata-size': 2048,
        ])

        when:
        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        ServerBuilder<?> serverBuilder = ctx.getBean(ServerBuilder)
        def server = serverBuilder.build()
        server.start()

        then:
        configuration.getMaxInboundMessageSize() == 1024
        configuration.getMaxInboundMetadataSize() == 2048
        serverBuilder instanceof InProcessServerBuilder
        server.port == -1

        cleanup:
        server.shutdown().awaitTermination()
        ctx.close()
    }
}
