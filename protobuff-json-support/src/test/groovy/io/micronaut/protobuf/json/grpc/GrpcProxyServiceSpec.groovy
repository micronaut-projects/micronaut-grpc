package io.micronaut.protobuf.json.grpc

import io.grpc.MethodDescriptor
import io.grpc.protobuf.ProtoUtils
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.ExecutableMethod
import io.micronaut.protobuf.json.ProtobufJsonTranscoder
import io.micronaut.protobuf.json.exception.MethodNotFoundException
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import spock.lang.Specification

class GrpcProxyServiceSpec extends Specification {

    private static final MethodDescriptor<?, ?> TEST_DESCRIPTOR = MethodDescriptor.newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("test.GreeterService", "sayHello"))
            .setRequestMarshaller(ProtoUtils.marshaller(HelloRequest.getDefaultInstance()))
            .setResponseMarshaller(ProtoUtils.marshaller(HelloResponse.getDefaultInstance()))
            .build()

    GrpcServiceRegistry registry = Mock()
    ApplicationContext applicationContext = Mock()
    ProtobufJsonTranscoder transcoder = Mock()
    GrpcProxyService grpcProxyService = new GrpcProxyService(registry, applicationContext, transcoder)

    def "parses protobuf request bytes with the registered method descriptor"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Micronaut").build()
        ExecutableMethod executableMethod = Mock()
        def registeredMethod = new GrpcServiceRegistry.RegisteredMethod(executableMethod, TEST_DESCRIPTOR)
        registry.getRegisteredMethod("GreeterService", "sayHello") >> Optional.of(registeredMethod)

        when:
        def message = grpcProxyService.parseRequestMessage("GreeterService", "sayHello", request.toByteArray())

        then:
        message == request
    }

    def "throws MethodNotFoundException when parsing bytes for an unregistered method"() {
        given:
        registry.getRegisteredMethod("GreeterService", "sayHello") >> Optional.empty()

        when:
        grpcProxyService.parseRequestMessage("GreeterService", "sayHello", HelloRequest.getDefaultInstance().toByteArray())

        then:
        def e = thrown(MethodNotFoundException)
        e.message == "Method 'sayHello' not found"
    }
}
