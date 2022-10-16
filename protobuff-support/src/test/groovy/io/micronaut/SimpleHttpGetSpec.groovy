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

class SimpleHttpGetSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/city'

    void "sample city should be dublin/using sample controller"() {
        when: 'The message is requested from the sever=[#url]'
        def response = getMessage(url, Example.GeoPoint.class, ProtobufferCodec.PROTOBUFFER_ENCODED)
        and: 'The message is parser'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city
    }

    void "test second protobuff content type header"() {
        when: 'The message is requested from the sever=[#url]'
        def response = getMessage(url, Example.GeoPoint.class, ProtobufferCodec.PROTOBUFFER_ENCODED2)
        and: 'The message is parser'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city
    }

    void "test cutom protobuff content type header"() {
        when: 'The message is requested from the sever=[#url]'
        def response = getMessage(url, Example.GeoPoint.class, SampleController.MY_PROTO_ENCODED)
        and: 'The message is parser'
        Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then: 'Should be Dublin'
        SampleController.DUBLIN == city
    }
}
