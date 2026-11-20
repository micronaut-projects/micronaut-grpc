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

    void "factory names match the processor's encoding"() {
        expect:
        ProtobufMessageBuilders.factoryName(binaryName, packageName) == expected

        where:
        binaryName                         | packageName        | expected
        'com.example.wire.Example$GeoPoint' | 'com.example.wire' | 'com.example.wire.Example_GeoPoint$ProtobufMessageFactory'
        'p.A_B$C'                           | 'p'                | 'p.A_1B_C$ProtobufMessageFactory'
        'p.A$B_C'                           | 'p'                | 'p.A_B_1C$ProtobufMessageFactory'
        'Top'                               | ''                 | 'Top$ProtobufMessageFactory'
    }

    void "the lookup result is cached per message class"() {
        expect:
        ProtobufMessageBuilders.newBuilder(Example.GeoPoint).getClass() == ProtobufMessageBuilders.newBuilder(Example.GeoPoint).getClass()
        ProtobufMessageBuilders.newBuilder(Timestamp).getClass() == Timestamp.Builder
    }

    void "a type without newBuilder() is rejected"() {
        when:
        ProtobufMessageBuilders.newBuilder(Message)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('has no static newBuilder() method')
    }
}
