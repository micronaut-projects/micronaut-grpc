package io.micronaut.grpc.processor

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.GeneratedFile
import spock.lang.Specification

class GrpcStubFactoryVisitorSpec extends Specification {

    GrpcStubFactoryVisitor visitor = new GrpcStubFactoryVisitor()

    void "the visitor targets grpc-java generated classes and is isolating"() {
        expect:
        visitor.supportedAnnotationNames == ['io.grpc.stub.annotations.GrpcGenerated'] as Set
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
    }

    void "a factory method is generated for every stub factory method"() {
        given:
        StringWriter source = new StringWriter()
        ClassElement service = service('com.example', 'com.example.GreeterGrpc', [
                stubMethod('newStub', 'com.example.GreeterGrpc$GreeterStub'),
                stubMethod('newBlockingStub', 'com.example.GreeterGrpc$GreeterBlockingStub'),
                stubMethod('newFutureStub', 'com.example.GreeterGrpc$GreeterFutureStub'),
        ])
        VisitorContext context = Mock()

        when:
        visitor.visitClass(service, context)

        then:
        1 * context.getClassElement(GrpcStubFactoryVisitor.CHANNEL_FACTORY_TYPE) >> Optional.of(Mock(ClassElement))
        1 * context.visitGeneratedSourceFile('com.example', 'GreeterGrpc$StubFactory', service) >> Optional.of(file(source))
        0 * context.fail(_, _)

        and:
        String code = source.toString()
        code.startsWith('package com.example;')
        code.contains('@io.micronaut.context.annotation.Factory')
        !code.contains('@io.micronaut.core.annotation.Generated')
        code.contains('public final class GreeterGrpc$StubFactory {')
        code.count('@io.micronaut.context.annotation.Prototype') == 3
        code.count('@io.micronaut.context.annotation.Secondary') == 3
        code.contains('public com.example.GreeterGrpc.GreeterBlockingStub newBlockingStub(io.micronaut.inject.InjectionPoint<?> injectionPoint, io.micronaut.grpc.channels.GrpcManagedChannelFactory channels)')
        code.contains('return com.example.GreeterGrpc.newBlockingStub(channels.stubChannel(injectionPoint));')
        code.contains('return com.example.GreeterGrpc.newStub(channels.stubChannel(injectionPoint));')
        code.contains('return com.example.GreeterGrpc.newFutureStub(channels.stubChannel(injectionPoint));')
    }

    void "methods that are not stub factories are ignored"() {
        given:
        StringWriter source = new StringWriter()
        List<MethodElement> methods = [
                stubMethod('newBlockingStub', 'com.example.GreeterGrpc$GreeterBlockingStub'),
                stubMethod('getServiceDescriptor', 'io.grpc.ServiceDescriptor'),
                stubMethod('newStub', 'com.example.GreeterGrpc$GreeterStub', false),
                stubMethod('newBlockingStub', 'com.example.GreeterGrpc$GreeterBlockingStub', true, true, 'java.lang.String'),
                stubMethod('newHelperStub', 'com.example.Helper', true, true, 'io.grpc.Channel', false),
                stubMethod('newPrivateStub', 'com.example.GreeterGrpc$PrivateStub', true, true, 'io.grpc.Channel', true, false),
                stubMethod('newInstanceStub', 'com.example.GreeterGrpc$InstanceStub', true, false),
        ]
        ClassElement service = service('com.example', 'com.example.GreeterGrpc', methods)
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
            visitGeneratedSourceFile(*_) >> Optional.of(file(source))
        }

        when:
        visitor.visitClass(service, context)

        then:
        source.toString().count('@io.micronaut.context.annotation.Prototype') == 1
        source.toString().contains(' newBlockingStub(')
    }

    void "a service without stub factory methods gets no factory"() {
        given:
        ClassElement service = service('com.example', 'com.example.GreeterGrpc', [stubMethod('getServiceDescriptor', 'io.grpc.ServiceDescriptor')])
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
        }

        when:
        visitor.visitClass(service, context)

        then:
        0 * context.visitGeneratedSourceFile(*_)
    }

    void "classes that can't get a factory are skipped"() {
        given:
        ClassElement element = Mock {
            getPackageName() >> packageName
            isInner() >> inner
            isPublic() >> publicType
        }
        VisitorContext context = Mock()

        when:
        visitor.visitClass(element, context)

        then:
        0 * context.visitGeneratedSourceFile(*_)

        where:
        packageName   | inner | publicType
        ''            | false | true
        'com.example' | true  | true
        'com.example' | false | false
    }

    void "nothing is generated when micronaut-grpc-client-runtime is not on the classpath"() {
        given:
        VisitorContext context = Mock()

        when:
        visitor.visitClass(service('com.example', 'com.example.GreeterGrpc', [stubMethod('newStub', 'com.example.GreeterGrpc$GreeterStub')]), context)

        then:
        1 * context.getClassElement(GrpcStubFactoryVisitor.CHANNEL_FACTORY_TYPE) >> Optional.empty()
        0 * context.visitGeneratedSourceFile(*_)
    }

    void "a write failure is reported"() {
        given:
        ClassElement service = service('com.example', 'com.example.GreeterGrpc', [stubMethod('newStub', 'com.example.GreeterGrpc$GreeterStub')])
        GeneratedFile broken = Mock {
            openWriter() >> { throw new IOException('disk full') }
        }
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
            visitGeneratedSourceFile(*_) >> Optional.of(broken)
        }

        when:
        visitor.visitClass(service, context)

        then:
        1 * context.fail({ it.contains('disk full') }, service)
    }

    void "nothing happens when the source file cannot be created"() {
        given:
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
            visitGeneratedSourceFile(*_) >> Optional.empty()
        }

        when:
        visitor.visitClass(service('com.example', 'com.example.GreeterGrpc', [stubMethod('newStub', 'com.example.GreeterGrpc$GreeterStub')]), context)

        then:
        0 * context.fail(*_)
    }

    private ClassElement service(String packageName, String binaryName, List<MethodElement> methods) {
        Mock(ClassElement) {
            getPackageName() >> packageName
            getName() >> binaryName
            getSimpleName() >> binaryName.substring(binaryName.lastIndexOf('.') + 1)
            isInner() >> false
            isPublic() >> true
            getEnclosedElements(_) >> methods
        }
    }

    private MethodElement stubMethod(String name, String returnType, boolean publicMethod = true, boolean staticMethod = true,
                                     String parameterType = 'io.grpc.Channel', boolean stubType = true, boolean publicStub = true) {
        ClassElement parameterClass = Mock {
            getName() >> parameterType
        }
        ParameterElement parameter = Mock {
            getType() >> parameterClass
        }
        ClassElement returnClass = Mock {
            getName() >> returnType
            isPublic() >> publicStub
            isAssignable(GrpcStubFactoryVisitor.ABSTRACT_STUB_TYPE) >> stubType
        }
        Mock(MethodElement) {
            getName() >> name
            isPublic() >> publicMethod
            isStatic() >> staticMethod
            getParameters() >> ([parameter] as ParameterElement[])
            getReturnType() >> returnClass
        }
    }

    private GeneratedFile file(StringWriter writer) {
        Mock(GeneratedFile) {
            openWriter() >> writer
        }
    }
}
