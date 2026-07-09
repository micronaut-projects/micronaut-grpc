package io.micronaut.protobuf.json.registry

import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerMethodDefinition
import io.grpc.ServerServiceDefinition
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ServerCalls
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.grpc.annotation.GrpcRestJsonExposed
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.protobuf.grpc.AnotherGrpcService
import io.micronaut.protobuf.grpc.GreeterService
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
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

    def "process method annotated with exact grpc descriptor name"() {
        given:
        def methodDescriptor = MethodDescriptor.newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("test.CustomService", "SayHello"))
                .setRequestMarshaller(ProtoUtils.marshaller(HelloRequest.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(HelloResponse.getDefaultInstance()))
                .build()
        def serviceDefinition = ServerServiceDefinition.builder("test.CustomService")
                .addMethod(ServerMethodDefinition.create(methodDescriptor, ServerCalls.asyncUnaryCall { request, responseObserver ->
                    responseObserver.onCompleted()
                }))
                .build()
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> BindableService
        }
        def service = Mock(BindableService) {
            bindService() >> serviceDefinition
        }
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'SayHello'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }
        applicationContext.getBean(BindableService) >> service

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        1 * registry.register(BindableService, 'SayHello', executableMethod, methodDescriptor)
    }

    def "throws when no grpc descriptor matches the executable method name"() {
        given:
        def beanDefinition = Mock(BeanDefinition) {
            getBeanType() >> GreeterService
        }
        def service = new GreeterService()
        def executableMethod = Mock(ExecutableMethod) {
            getMethodName() >> 'missingMethod'
            getAnnotation(GrpcRestJsonExposed) >> Optional.of(AnnotationValue.builder(GrpcRestJsonExposed).build())
        }
        applicationContext.getBean(GreeterService) >> service

        when:
        registrar.process(beanDefinition, executableMethod)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No gRPC MethodDescriptor found for GreeterService.missingMethod'
    }
}
