package io.micronaut.protobuf.json.registry

import spock.lang.Specification
import spock.lang.Subject

class GrpcServiceRegistrySpec extends Specification {

    @Subject
    GrpcServiceRegistry registry = new GrpcServiceRegistry()

    def "should register and retrieve service"() {
        given:
        def serviceName = "testService"
        def serviceBean = Mock(TestService)
        def method = TestService.getDeclaredMethod("testMethod")
        def methods = [(method.name): method]
        def metadata = new GrpcServiceMetadata(serviceBean, GrpcServiceType.BLOCKING, methods)

        when:
        registry.registerService(serviceName, metadata)
        def result = registry.getService(serviceName)

        then:
        result.isPresent()
        with(result.get()) {
            getServiceBean() == serviceBean
            getMethod("testMethod") == method
            getType() == GrpcServiceType.BLOCKING
        }
    }

    def "should return empty optional for non-existent service"() {
        when:
        def result = registry.getService("nonExistentService")

        then:
        !result.isPresent()
    }

    def "should handle multiple service registrations"() {
        given:
        def service1 = Mock(TestService)
        def service2 = Mock(TestService)
        def method = TestService.getDeclaredMethod("testMethod")
        def methods = [(method.name): method]
        def metadata1 = new GrpcServiceMetadata(service1, GrpcServiceType.BLOCKING, methods)
        def metadata2 = new GrpcServiceMetadata(service2, GrpcServiceType.ASYNC, methods)

        when:
        registry.registerService("service1", metadata1)
        registry.registerService("service2", metadata2)

        then:
        registry.getService("service1").isPresent()
        registry.getService("service2").isPresent()
        with(registry.getService("service1").get()) {
            getServiceBean() == service1
            getType() == GrpcServiceType.BLOCKING
        }
        with(registry.getService("service2").get()) {
            getServiceBean() == service2
            getType() == GrpcServiceType.ASYNC
        }
    }

    def "metadata should return correct method"() {
        given:
        def serviceBean = Mock(TestService)
        def method = TestService.getDeclaredMethod("testMethod")
        def methods = [(method.name): method]
        def metadata = new GrpcServiceMetadata(serviceBean, GrpcServiceType.BLOCKING, methods)

        expect:
        metadata.getMethod("testMethod") == method
        metadata.getMethod("nonExistentMethod") == null
    }

    // Helper class for testing
    static class TestService {
        void testMethod() {}
    }
}
