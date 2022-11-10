package io.micronaut.grpc.server.health

import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.health.v1.HealthGrpc
import io.grpc.protobuf.services.HealthStatusManager
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcEmbeddedServer
import io.micronaut.grpc.server.GrpcServerChannel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@Property(name = "spec.name", value = "GrpcServerHealthSpec")
class GrpcServerHealthSpec extends Specification {

    void "grpc HealthCheck is enabled by default, and reports SERVING"() {
        given:
        GrpcEmbeddedServer server = createServer()

        when:
        def bean = server.applicationContext.getBean(TestBean)
        def result = bean.check()

        then:
        result.status == HealthCheckResponse.ServingStatus.SERVING

        cleanup:
        server.close()
    }

    void "disabling the health check results in an UNIMPLEMENTED exception"() {
        given:
        GrpcEmbeddedServer server = createServer('grpc.server.health.enabled': 'false')

        when:
        server.applicationContext.getBean(TestBean).check()

        then:
        def exception = thrown(StatusRuntimeException)
        exception.status.code == Status.Code.UNIMPLEMENTED

        and: 'The health beans are not registered'
        !server.applicationContext.findBean(HealthStatusManagerContainer).present
        !server.applicationContext.findBean(HealthStatusManager).present

        cleanup:
        server.close()
    }

    void 'checking an unknown service results in a NOT_FOUND exception'() {
        given:
        def server = createServer()
        def bean = server.applicationContext.getBean(TestBean)

        when: 'we query a non-existent service'
        def nonExistentResult = bean.check("non_existent")

        then:
        def exception = thrown(StatusRuntimeException)
        exception.status.code == Status.Code.NOT_FOUND

        cleanup:
        server.close()
    }

    void "status can be changed by the user"() {
        given:
        def server = createServer()
        def manager = server.applicationContext.getBean(HealthStatusManager)
        def bean = server.applicationContext.getBean(TestBean)

        when: 'we set the status for some services'
        manager.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES, HealthCheckResponse.ServingStatus.NOT_SERVING)
        manager.setStatus("other_service", HealthCheckResponse.ServingStatus.SERVING)

        and: 'we query their health'
        def allServiceResult = bean.check()
        def otherServiceResult = bean.check("other_service")

        then: 'we get back the status set above'
        allServiceResult.status == HealthCheckResponse.ServingStatus.NOT_SERVING
        otherServiceResult.status == HealthCheckResponse.ServingStatus.SERVING

        cleanup:
        server.close()
    }

    private GrpcEmbeddedServer createServer(Map props = [:]) {
        def port = SocketUtils.findAvailableTcpPort()
        def defaults = [
                'grpc.server.port': port,
                'spec.name'       : 'GrpcServerHealthSpec',
        ]
        ApplicationContext.run(GrpcEmbeddedServer, defaults + props)
    }

    @Factory
    @Requires(property = "spec.name", value = "GrpcServerHealthSpec")
    static class Clients {

        @Singleton
        HealthGrpc.HealthBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            HealthGrpc.newBlockingStub(channel)
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "GrpcServerHealthSpec")
    static class TestBean {

        @Inject
        HealthGrpc.HealthBlockingStub blockingStub

        HealthCheckResponse check() {
            blockingStub.check(HealthCheckRequest.newBuilder().build())
        }

        HealthCheckResponse check(String serviceName) {
            blockingStub.check(HealthCheckRequest.newBuilder().setService(serviceName).build())
        }
    }
}
