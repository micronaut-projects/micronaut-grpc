package io.micronaut.protobuf.json.registry

import io.grpc.MethodDescriptor
import io.grpc.protobuf.ProtoUtils
import io.micronaut.inject.ExecutableMethod
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import spock.lang.Specification
import spock.lang.Subject

class GrpcServiceRegistrySpec extends Specification {

    private static final MethodDescriptor<?, ?> TEST_DESCRIPTOR = MethodDescriptor.newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("test.TestService", "testMethod"))
            .setRequestMarshaller(ProtoUtils.marshaller(HelloRequest.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(HelloResponse.getDefaultInstance()))
            .build()

    @Subject
    GrpcServiceRegistry registry = new GrpcServiceRegistry()

    def "should register and retrieve executable method"() {
        given:
        ExecutableMethod executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> "testMethod"
            getDeclaringType() >> TestService.class
        }

        when:
        registry.register(TestService.class, "testMethod", executableMethod, TEST_DESCRIPTOR)
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
        registry.register(TestService.class, "method1", method1, TEST_DESCRIPTOR)
        registry.register(AnotherTestService.class, "method2", method2, TEST_DESCRIPTOR)

        then:
        registry.getExecutableMethod("TestService", "method1").isPresent()
        registry.getExecutableMethod("AnotherTestService", "method2").isPresent()
        registry.getExecutableMethod("TestService", "method1").get() == method1
        registry.getExecutableMethod("AnotherTestService", "method2").get() == method2
    }

    def "should return registered method descriptor"() {
        given:
        ExecutableMethod executableMethod = Mock(ExecutableMethod)

        when:
        registry.register(TestService.class, "testMethod", executableMethod, TEST_DESCRIPTOR)

        then:
        registry.getRegisteredMethod("TestService", "testMethod").present
        registry.getRegisteredMethod("TestService", "testMethod").get().methodDescriptor() == TEST_DESCRIPTOR
    }

    static class TestService {
        void testMethod() {}
    }

    static class AnotherTestService {
        void method2() {}
    }
}
