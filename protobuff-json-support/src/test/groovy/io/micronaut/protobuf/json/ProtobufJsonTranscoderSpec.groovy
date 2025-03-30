package io.micronaut.protobuf.json

import io.micronaut.protobuf.json.exception.MalformedGrpcJsonException
import org.example.grpc.HelloRequest
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ProtobufJsonTranscoderSpec extends Specification {
    @Subject
    ProtobufJsonTranscoder transcoder = new ProtobufJsonTranscoder()

    @Unroll
    def "should convert json '#json' to protobuf message with name=#expectedName"() {
        when:
        def message = transcoder.fromJson(json, HelloRequest.class)

        then:
        message.name == expectedName

        where:
        json              | expectedName
        '{"name":"John"}' | "John"
        '{"name":""}'     | ""
        '{}'              | ""
    }

    @Unroll
    def "should throw exception for invalid json: '#invalidJson'"() {
        when:
        transcoder.fromJson(invalidJson, HelloRequest.class)

        then:
        thrown(MalformedGrpcJsonException)

        where:
        invalidJson          << [
                '{"name":"John",',
                'invalid json',
                '{"invalid": true}'
        ]
    }
}
