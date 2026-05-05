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
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.io.ResourceResolver
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.server.GrpcServerConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.Executor

@MicronautTest
class GrpcServerConfigurationSpec extends Specification {

    void "test GRPC configuration"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
                'grpc.server.port'             : port,
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
        server.shutdown().awaitTermination()
        ctx.close()
    }

    void "test GRPC SSL configuration"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
                'grpc.server.port'           : port,
                'grpc.server.ssl.cert-chain' : 'classpath:example.crt',
                'grpc.server.ssl.private-key': 'classpath:example.key',
        ])

        when:
        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        ServerBuilder serverBuilder = configuration.getServerBuilder()
        def server = serverBuilder.build()
        server.start()

        then:
        noExceptionThrown()

        cleanup:
        server.shutdown().awaitTermination()
        ctx.close()
    }

    void "test GRPC server provides resource resolver without micronaut http resource factory"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.builder(new DenyingClassLoader(getClass().classLoader, 'io.micronaut.http.resource.'))
                .environments(Environment.TEST)
                .properties([
                        'grpc.server.port'           : port,
                        'grpc.server.ssl.cert-chain' : 'classpath:example.crt',
                        'grpc.server.ssl.private-key': 'classpath:example.key',
                ])
                .start()

        when:
        ResourceResolver resourceResolver = ctx.getBean(ResourceResolver)
        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        def server = configuration.getServerBuilder().build()
        server.start()

        then:
        resourceResolver.getResourceAsStream('classpath:example.crt').present
        configuration.secure

        cleanup:
        server.shutdown().awaitTermination()
        ctx.close()
    }

    private static final class DenyingClassLoader extends ClassLoader {
        private final List<String> deniedPrefixes

        DenyingClassLoader(ClassLoader parent, String... deniedPrefixes) {
            super(parent)
            this.deniedPrefixes = deniedPrefixes.toList()
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (deniedPrefixes.any { name.startsWith(it) }) {
                throw new ClassNotFoundException(name)
            }
            return super.loadClass(name, resolve)
        }
    }

    void "test GRPC executor can be overridden with named bean configuration"() {
        given:
        def port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
                'grpc.server.port'    : port,
                'grpc.server.executor': 'grpc-test-executor',
                'spec.name'           : 'GrpcServerConfigurationSpec'
        ])

        when:
        GrpcServerConfiguration configuration = ctx.getBean(GrpcServerConfiguration)
        Executor expectedExecutor = ctx.getBean(Executor, Qualifiers.byName('grpc-test-executor'))
        ServerBuilder<?> serverBuilder = ctx.getBean(ServerBuilder)

        then:
        configuration.executor.get() == 'grpc-test-executor'
        configuredExecutor(serverBuilder).is(expectedExecutor)

        cleanup:
        ctx.close()
    }

    void "test GRPC executor rejects unknown bean names"() {
        given:
        def ctx = ApplicationContext.run([
                'grpc.server.executor': 'missing-executor'
        ])

        when:
        ctx.getBean(ServerBuilder)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('No executor bean named [missing-executor] is available')

        cleanup:
        ctx.close()
    }

    private static Executor configuredExecutor(ServerBuilder<?> serverBuilder) {
        def serverImplBuilderField = serverBuilder.class.getDeclaredField('serverImplBuilder')
        serverImplBuilderField.accessible = true
        def serverImplBuilder = serverImplBuilderField.get(serverBuilder)
        serverImplBuilder.getExecutorPool().getObject() as Executor
    }

    @Factory
    @Requires(property = 'spec.name', value = 'GrpcServerConfigurationSpec')
    static class TestExecutors {

        @Singleton
        @Named('grpc-test-executor')
        Executor grpcTestExecutor() {
            new TestExecutor()
        }
    }

    static final class TestExecutor implements Executor {

        @Override
        void execute(Runnable command) {
            command.run()
        }
    }
}
