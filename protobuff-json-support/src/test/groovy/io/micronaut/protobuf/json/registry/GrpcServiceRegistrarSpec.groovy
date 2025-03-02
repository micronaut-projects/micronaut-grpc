package io.micronaut.protobuf.json.registry

import com.google.protobuf.Method
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
                greeterService,
                { Map<String, Method> methods ->
                    methods.size() == 1 &&
                            methods.containsKey('sayHello')
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

    @GrpcRestJsonExposed
    static class ServiceWithNoGrpcMethods {
        void regularMethod(String param) {}
    }
}
