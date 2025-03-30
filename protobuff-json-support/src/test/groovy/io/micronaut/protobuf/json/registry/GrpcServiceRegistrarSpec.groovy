package io.micronaut.protobuf.json.registry

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.protobuf.grpc.AnotherGrpcService
import io.micronaut.protobuf.grpc.GreeterService
import spock.lang.Specification
import spock.lang.Subject

class GrpcServiceRegistrarSpec extends Specification {

    GrpcServiceRegistry registry = Mock()

    @Subject
    GrpcServiceRegistrar registrar = new GrpcServiceRegistrar(registry)

    def "process method annotated with GrpcRestJsonExposed"() {
        given:
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> GreeterService
        }
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'sayHello'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        1 * registry.register(GreeterService, 'sayHello', executableMethod)
    }

    def "should register method from another service"() {
        given:
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> AnotherGrpcService
        }
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'anotherMethod'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        1 * registry.register(AnotherGrpcService, 'anotherMethod', executableMethod)
    }
}
