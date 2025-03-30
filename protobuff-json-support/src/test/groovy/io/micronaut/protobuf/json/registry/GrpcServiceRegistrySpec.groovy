package io.micronaut.protobuf.json.registry

import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification
import spock.lang.Subject

class GrpcServiceRegistrySpec extends Specification {

    @Subject
    GrpcServiceRegistry registry = new GrpcServiceRegistry()

    def "should register and retrieve executable method"() {
        given:
        ExecutableMethod executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> "testMethod"
            getDeclaringType() >> TestService.class
        }

        when:
        registry.register(TestService.class, "testMethod", executableMethod)
        def result = registry.getExecutableMethod("TestService", "testMethod")

        then:
        result.isPresent()
        result.get() == executableMethod
    }

    def "should return empty optional when method doesn't exist"() {
        when:
        def result = registry.getExecutableMethod("NonExistentService", "nonExistentMethod")

        then:
        !result.isPresent()
    }

    def "should handle multiple registrations correctly"() {
        given:
        ExecutableMethod method1 = Mock(ExecutableMethod) {
            getMethodName() >> "method1"
            getDeclaringType() >> TestService.class
        }
        ExecutableMethod method2 = Mock(ExecutableMethod) {
            getMethodName() >> "method2"
            getDeclaringType() >> AnotherTestService.class
        }

        when:
        registry.register(TestService.class, "method1", method1)
        registry.register(AnotherTestService.class, "method2", method2)

        then:
        registry.getExecutableMethod("TestService", "method1").isPresent()
        registry.getExecutableMethod("AnotherTestService", "method2").isPresent()
        registry.getExecutableMethod("TestService", "method1").get() == method1
        registry.getExecutableMethod("AnotherTestService", "method2").get() == method2
    }

    static class TestService {
        void testMethod() {}
    }

    static class AnotherTestService {
        void method2() {}
    }
}
