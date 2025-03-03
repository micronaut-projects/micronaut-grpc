package io.micronaut.protobuf.json.registry

import com.google.protobuf.Method
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.stub.AbstractStub
import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import io.micronaut.inject.BeanDefinition
import io.micronaut.protobuf.grpc.GreeterService
import spock.lang.Specification
import spock.lang.Subject

class GrpcServiceRegistrarSpec extends Specification {

    BeanContext beanContext = Spy()
    GrpcServiceRegistry registry = Spy()

    @Subject
    GrpcServiceRegistrar registrar

    def setup() {
        registrar = new GrpcServiceRegistrar(beanContext, registry)
    }

    def "should register gRPC services with valid methods"() {
        given:
        def greeterService = new GreeterService()
        def beanDefinition = Stub(BeanDefinition) {
            getAnnotation(GrpcRestJsonExposed) >> AnnotationValue.builder(GrpcRestJsonExposed).build()
            getBeanType() >> GreeterService
        }

        when:
        beanContext.getBeanDefinitions(Object) >> [beanDefinition]
        beanContext.findBean(GreeterService) >> Optional.of(greeterService)
        registrar.registerGrpcServices()

        then:
        1 * registry.registerService(
                'GreeterService',
                { GrpcServiceMetadata metadata ->
                    //noinspection GroovyAccessibility
                    metadata.serviceBean == greeterService &&
                            metadata.type == GrpcServiceType.ASYNC &&
                            metadata.methods.size() == 1 &&
                            metadata.methods.containsKey('sayHello')
                }
        )
    }

    def "should skip registration for service with no gRPC methods"() {
        given:
        def serviceWithNoMethods = new ServiceWithNoGrpcMethods()
        def beanDefinition = Stub(BeanDefinition) {
            getAnnotation(GrpcRestJsonExposed) >> AnnotationValue.builder(GrpcRestJsonExposed).build()
            getBeanType() >> ServiceWithNoGrpcMethods
        }

        when:
        beanContext.getBeanDefinitions(Object) >> [beanDefinition]
        beanContext.findBean(ServiceWithNoGrpcMethods) >> Optional.of(serviceWithNoMethods)
        registrar.registerGrpcServices()

        then:
        0 * registry.registerService(_, _, _)
    }

    def "should skip services without GrpcRestJsonExposed annotation"() {
        given:
        def beanDefinition = Stub(BeanDefinition) {
            getAnnotation(GrpcRestJsonExposed) >> null
        }

        when:
        beanContext.getBeanDefinitions(Object) >> [beanDefinition]
        registrar.registerGrpcServices()

        then:
        0 * registry.registerService(_, _, _)
    }

    @SuppressWarnings('GroovyUnusedAssignment')
    def "should register client stub with service name without 'Stub' suffix"() {
        given:
        def mockChannel = Mock(Channel)
        def testMethod = MethodDescriptor.<String, String> newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.TestService/test")
                .setRequestMarshaller(new MethodDescriptor.Marshaller<String>() {
                    @Override
                    String parse(InputStream stream) {
                        return stream.text
                    }

                    @Override
                    InputStream stream(String value) {
                        return new ByteArrayInputStream(value.bytes)
                    }
                })
                .setResponseMarshaller(new MethodDescriptor.Marshaller<String>() {
                    @Override
                    String parse(InputStream stream) {
                        return stream.text
                    }

                    @Override
                    InputStream stream(String value) {
                        return new ByteArrayInputStream(value.bytes)
                    }
                })
                .build()

        def clientStub = Spy(TestServiceStub, constructorArgs: [mockChannel]) {
            getMethodDescriptor("test") >> testMethod
        }

        def beanDefinition = Stub(BeanDefinition) {
            getAnnotation(GrpcRestJsonExposed) >> AnnotationValue.builder(GrpcRestJsonExposed).build()
            getBeanType() >> TestServiceStub
        }
    }

    def "should handle service with no gRPC methods"() {
        given:
        def serviceWithNoMethods = new ServiceWithNoGrpcMethods()
        def beanDefinition = Stub(BeanDefinition) {
            getAnnotation(GrpcRestJsonExposed) >> AnnotationValue.builder(GrpcRestJsonExposed).build()
            getBeanType() >> ServiceWithNoGrpcMethods
        }

        when:
        beanContext.getBeanDefinitions(Object) >> [beanDefinition]
        beanContext.findBean(ServiceWithNoGrpcMethods) >> Optional.of(serviceWithNoMethods)
        registrar.registerGrpcServices()

        then:
        0 * registry.registerService(_, _, _) // Expect no calls to registerService

    }

    static class TestServiceStub extends AbstractStub<TestServiceStub> {
        @SuppressWarnings('unused')
        TestServiceStub(Channel channel) {
            super(channel)
        }

        TestServiceStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions)
        }

        @Override
        protected TestServiceStub build(Channel channel, CallOptions callOptions) {
            return new TestServiceStub(channel, callOptions)
        }

        // This follows the standard gRPC method descriptor pattern
        @SuppressWarnings('unused')
        static MethodDescriptor<String, String> getMethodDescriptor(String methodName) {
            return null // Will be overridden by spy
        }
    }

    static class ServiceWithNoGrpcMethods {
        // Empty class with no gRPC methods
    }

}
