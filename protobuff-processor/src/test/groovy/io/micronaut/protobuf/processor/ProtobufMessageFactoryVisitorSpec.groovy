package io.micronaut.protobuf.processor

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.GeneratedFile
import spock.lang.Specification

class ProtobufMessageFactoryVisitorSpec extends Specification {

    ProtobufMessageFactoryVisitor visitor = new ProtobufMessageFactoryVisitor()

    void "the visitor targets protoc-generated classes and is isolating"() {
        expect:
        visitor.supportedAnnotationNames == ['com.google.protobuf.Generated'] as Set
        visitor.visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
    }

    void "a factory is generated and registered for a nested message"() {
        given:
        StringWriter source = new StringWriter()
        ClassElement message = message('com.example.wire', 'com.example.wire.Example$GeoPoint')
        VisitorContext context = Mock()

        when:
        visitor.visitClass(message, context)

        then:
        1 * context.getClassElement(ProtobufMessageFactoryVisitor.FACTORY_TYPE) >> Optional.of(Mock(ClassElement))
        1 * context.visitGeneratedSourceFile('com.example.wire', 'Example_GeoPoint$ProtobufMessageFactory', message) >> Optional.of(file(source))
        1 * context.visitServiceDescriptor(ProtobufMessageFactoryVisitor.FACTORY_TYPE, 'com.example.wire.Example_GeoPoint$ProtobufMessageFactory', message)
        0 * context.fail(_, _)

        and:
        String code = source.toString()
        code.startsWith('package com.example.wire;')
        code.contains('public final class Example_GeoPoint$ProtobufMessageFactory implements io.micronaut.protobuf.ProtobufMessageFactory<com.example.wire.Example.GeoPoint>')
        code.contains('return com.example.wire.Example.GeoPoint.class;')
        code.contains('return com.example.wire.Example.GeoPoint.newBuilder();')
    }

    void "a top-level message in the default package gets a factory without a package declaration"() {
        given:
        StringWriter source = new StringWriter()
        ClassElement message = message('', 'HelloRequest')
        VisitorContext context = Mock()

        when:
        visitor.visitClass(message, context)

        then:
        1 * context.getClassElement(_) >> Optional.of(Mock(ClassElement))
        1 * context.visitGeneratedSourceFile('', 'HelloRequest$ProtobufMessageFactory', message) >> Optional.of(file(source))
        1 * context.visitServiceDescriptor(ProtobufMessageFactoryVisitor.FACTORY_TYPE, 'HelloRequest$ProtobufMessageFactory', message)

        and:
        !source.toString().contains('package ')
        source.toString().contains('return HelloRequest.newBuilder();')
    }

    void "classes that are not concrete public messages are skipped"() {
        given:
        ClassElement element = Mock {
            isAbstract() >> abstractType
            isInterface() >> interfaceType
            isPublic() >> publicType
            isAssignable(ProtobufMessageFactoryVisitor.MESSAGE_TYPE) >> messageType
        }
        VisitorContext context = Mock()

        when:
        visitor.visitClass(element, context)

        then:
        0 * context.visitGeneratedSourceFile(*_)
        0 * context.visitServiceDescriptor(*_)

        where:
        abstractType | interfaceType | publicType | messageType
        true         | false         | true       | true
        false        | true          | true       | true
        false        | false         | false      | true
        false        | false         | true       | false
    }

    void "nothing is generated when micronaut-protobuff-support is not on the classpath"() {
        given:
        VisitorContext context = Mock()

        when:
        visitor.visitClass(message('com.example', 'com.example.Hello'), context)

        then:
        1 * context.getClassElement(ProtobufMessageFactoryVisitor.FACTORY_TYPE) >> Optional.empty()
        0 * context.visitGeneratedSourceFile(*_)
        0 * context.visitServiceDescriptor(*_)
    }

    void "a message without a static newBuilder() is skipped"() {
        given:
        ClassElement element = Mock {
            isPublic() >> true
            isAssignable(ProtobufMessageFactoryVisitor.MESSAGE_TYPE) >> true
            getEnclosedElement(_) >> Optional.empty()
        }
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
        }

        when:
        visitor.visitClass(element, context)

        then:
        0 * context.visitGeneratedSourceFile(*_)
    }

    void "a write failure is reported and the factory is not registered"() {
        given:
        ClassElement message = message('com.example', 'com.example.Hello')
        GeneratedFile broken = Mock {
            openWriter() >> { throw new IOException('disk full') }
        }
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
            visitGeneratedSourceFile(*_) >> Optional.of(broken)
        }

        when:
        visitor.visitClass(message, context)

        then:
        1 * context.fail({ it.contains('disk full') }, message)
        0 * context.visitServiceDescriptor(*_)
    }

    void "nothing is registered when the source file cannot be created"() {
        given:
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
            visitGeneratedSourceFile(*_) >> Optional.empty()
        }

        when:
        visitor.visitClass(message('com.example', 'com.example.Hello'), context)

        then:
        0 * context.visitServiceDescriptor(*_)
    }

    private ClassElement message(String packageName, String binaryName) {
        Mock(ClassElement) {
            isPublic() >> true
            isAssignable(ProtobufMessageFactoryVisitor.MESSAGE_TYPE) >> true
            getEnclosedElement(_) >> Optional.of(Mock(MethodElement))
            getPackageName() >> packageName
            getName() >> binaryName
        }
    }

    private GeneratedFile file(StringWriter writer) {
        Mock(GeneratedFile) {
            openWriter() >> writer
        }
    }
}
