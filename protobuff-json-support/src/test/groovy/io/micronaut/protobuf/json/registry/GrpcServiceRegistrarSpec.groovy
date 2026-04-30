package io.micronaut.protobuf.json.registry

import io.grpc.BindableService
import io.micronaut.context.ApplicationContext
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
    ApplicationContext applicationContext = Mock()

    @Subject
    GrpcServiceRegistrar registrar = new GrpcServiceRegistrar(registry, applicationContext)

    def "process method annotated with GrpcRestJsonExposed"() {
        given:
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> GreeterService
        }
        def service = new GreeterService()
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'sayHello'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }
        applicationContext.getBean(GreeterService) >> service

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        1 * registry.register(GreeterService, 'sayHello', executableMethod, _)
    }

    def "should register method from another service"() {
        given:
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> AnotherGrpcService
        }
        def service = new AnotherGrpcService()
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'anotherMethod'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }
        applicationContext.getBean(AnotherGrpcService) >> service

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        1 * registry.register(AnotherGrpcService, 'anotherMethod', executableMethod, _)
    }
}
