/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut

import com.example.wire.Example
import io.micronaut.protobuf.codec.ProtobufferCodec

class SimpleHttpPostSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/nearby'

    void "near by Dublin should be Dublin"() {
        setup:
        Example.GeoPoint message = SampleController.DUBLIN
        when: 'The message is posted to the server=[#url]'
        def response = postMessage(url, message, ProtobufferCodec.PROTOBUFFER_ENCODED)
        and: 'The message is parsed'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city

        when: 'The byte[] is posted to the server=[#url]'
        response = postMessage(url, message.toByteArray(), ProtobufferCodec.PROTOBUFFER_ENCODED)
        then: 'The message is parsed'
        Example.GeoPoint.parseFrom(response) == SampleController.DUBLIN
    }

    void "test second protobuff content type header"() {
        setup:
        Example.GeoPoint message = SampleController.DUBLIN
        when: 'The message is posted to the server=[#url]'
        def response = postMessage(url, message, ProtobufferCodec.PROTOBUFFER_ENCODED2)
        and: 'The message is parsed'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city

        when: 'The byte[] is posted to the server=[#url]'
        response = postMessage(url, message.toByteArray(), ProtobufferCodec.PROTOBUFFER_ENCODED2)
        then: 'The message is parsed'
        Example.GeoPoint.parseFrom(response) == SampleController.DUBLIN
    }

    void "test custom protobuff content type header"() {
        setup:
        Example.GeoPoint message = SampleController.DUBLIN
        when: 'The message is posted to the server=[#url]'
        def response = postMessage(url, message, SampleController.MY_PROTO_ENCODED)
        and: 'The message is parsed'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city

        when: 'The byte[] is posted to the server=[#url]'
        response = postMessage(url, message.toByteArray(), SampleController.MY_PROTO_ENCODED)
        then: 'The message is parsed'
        Example.GeoPoint.parseFrom(response) == SampleController.DUBLIN
    }
}
