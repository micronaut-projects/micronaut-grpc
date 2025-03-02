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

        when:
        registry.registerService(serviceName, serviceBean, methods)
        def result = registry.getService(serviceName)

        then:
        result.isPresent()
        with(result.get()) {
            getServiceBean() == serviceBean
            getMethod("testMethod") == method
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

        when:
        registry.registerService("service1", service1, methods)
        registry.registerService("service2", service2, methods)

        then:
        registry.getService("service1").isPresent()
        registry.getService("service2").isPresent()
        registry.getService("service1").get().getServiceBean() == service1
        registry.getService("service2").get().getServiceBean() == service2
    }

    def "service definition should return correct method"() {
        given:
        def serviceBean = Mock(TestService)
        def method = TestService.getDeclaredMethod("testMethod")
        def methods = [(method.name): method]
        def serviceDef = new GrpcServiceRegistry.ServiceDefinition(serviceBean, methods)

        expect:
        serviceDef.getMethod("testMethod") == method
        serviceDef.getMethod("nonExistentMethod") == null
    }

    // Helper class for testing
    static class TestService {
        void testMethod() {}
    }
}
