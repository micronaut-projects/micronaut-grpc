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

    void "messages nested in a generated wrapper that is not a message get factories"() {
        given: 'the outer wrapper is the only class protoc annotates with the default java_multiple_files=false layout'
        ClassElement geoPoint = message('com.example.wire', 'com.example.wire.Example$GeoPoint')
        ClassElement wrapper = Mock {
            isPublic() >> true
            isAssignable(ProtobufMessageFactoryVisitor.MESSAGE_TYPE) >> false
            getName() >> 'com.example.wire.Example'
            getEnclosedElements(_) >> [geoPoint]
        }
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
        }

        when: 'the nested message is also visited directly afterwards, as non-incremental processing may do'
        visitor.visitClass(wrapper, context)
        visitor.visitClass(geoPoint, context)

        then: 'its factory is generated once'
        1 * context.visitGeneratedSourceFile('com.example.wire', 'Example_GeoPoint$ProtobufMessageFactory', geoPoint) >> Optional.of(file(new StringWriter()))
        1 * context.visitServiceDescriptor(ProtobufMessageFactoryVisitor.FACTORY_TYPE, 'com.example.wire.Example_GeoPoint$ProtobufMessageFactory', geoPoint)
        0 * context.visitGeneratedSourceFile(*_)
        0 * context.visitServiceDescriptor(*_)
    }

    void "messages in the default package are skipped"() {
        given:
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
        }

        when:
        visitor.visitClass(message('', 'HelloRequest'), context)

        then:
        0 * context.visitGeneratedSourceFile(*_)
        0 * context.visitServiceDescriptor(*_)
    }

    void "a top-level message gets a factory named after it"() {
        given:
        StringWriter source = new StringWriter()
        ClassElement message = message('org.example.grpc', 'org.example.grpc.HelloRequest')
        VisitorContext context = Mock {
            getClassElement(_) >> Optional.of(Mock(ClassElement))
        }

        when:
        visitor.visitClass(message, context)

        then:
        1 * context.visitGeneratedSourceFile('org.example.grpc', 'HelloRequest$ProtobufMessageFactory', message) >> Optional.of(file(source))
        1 * context.visitServiceDescriptor(ProtobufMessageFactoryVisitor.FACTORY_TYPE, 'org.example.grpc.HelloRequest$ProtobufMessageFactory', message)

        and:
        source.toString().contains('public java.lang.Class<org.example.grpc.HelloRequest> getMessageType()')
    }

    void "factory names encode nesting so that different nestings never collide"() {
        expect:
        ProtobufMessageFactoryVisitor.factorySimpleName(binaryName, 'p') == expected

        where:
        binaryName  | expected
        'p.A$B$C'   | 'A_B_C$ProtobufMessageFactory'
        'p.A_B$C'   | 'A_1B_C$ProtobufMessageFactory'
        'p.A$B_C'   | 'A_B_1C$ProtobufMessageFactory'
        'p.Message' | 'Message$ProtobufMessageFactory'
    }

    void "classes that are not concrete public messages are skipped"() {
        given:
        ClassElement element = Mock {
            isAbstract() >> abstractType
            isInterface() >> interfaceType
            isPublic() >> publicType
            isAssignable(ProtobufMessageFactoryVisitor.MESSAGE_TYPE) >> messageType
            getEnclosedElements(_) >> []
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
            getEnclosedElements(_) >> []
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
            getEnclosedElements(_) >> []
        }
    }

    private GeneratedFile file(StringWriter writer) {
        Mock(GeneratedFile) {
            openWriter() >> writer
        }
    }
}
