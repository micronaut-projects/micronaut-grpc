package io.micronaut.protobuf

import com.example.wire.Example
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import spock.lang.Specification

class ProtobufMessageBuildersSpec extends Specification {

    void "a factory is generated for nested message classes"() {
        expect:
        ProtobufMessageBuilders.hasGeneratedFactory(type)
        ProtobufMessageBuilders.newBuilder(type).getDefaultInstanceForType().getClass() == type

        where:
        type << [Example.GeoPoint, Example.PhoneNumber, Example.CurrencyAmount]
    }

    void "the generated factory is registered as a service"() {
        when:
        def factory = io.micronaut.core.io.service.SoftServiceLoader.load(ProtobufMessageFactory).collectAll()
                .find { it.messageType == Example.GeoPoint }

        then:
        factory != null
        factory.class.name == 'com.example.wire.Example_GeoPoint$ProtobufMessageFactory'
        factory.newBuilder() instanceof Example.GeoPoint.Builder
    }

    void "messages compiled without the processor fall back to reflection"() {
        expect: 'Timestamp comes from the protobuf-java jar, which the processor never saw'
        !ProtobufMessageBuilders.hasGeneratedFactory(Timestamp)
        ProtobufMessageBuilders.newBuilder(Timestamp) instanceof Timestamp.Builder
    }

    void "a type without newBuilder() is rejected"() {
        when:
        ProtobufMessageBuilders.newBuilder(Message)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('has no static newBuilder() method')
    }
}
