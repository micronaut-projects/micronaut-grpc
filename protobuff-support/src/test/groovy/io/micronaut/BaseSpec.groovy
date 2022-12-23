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

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.Message
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.protobuf.codec.ProtobufferCodec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class BaseSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(
            HttpClient,
            embeddedServer.getURL()
    )

    @Shared
    @AutoCleanup
    WebSocketClient webSocketClient = embeddedServer.applicationContext.createBean(
            WebSocketClient,
            embeddedServer.getURL()
    )

    byte[] getMessage(String url, Class aClass, String mediaType) {
        return httpClient.toBlocking().retrieve(
                HttpRequest.GET(url)
                        .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, aClass.name)
                        .header(HttpHeaders.ACCEPT, mediaType),
                byte[].class
        )
    }

    byte[] postMessage(String url, Message message, String mediaType) {
        return httpClient.toBlocking().retrieve(
                HttpRequest.POST(url, message)
                        .header(HttpHeaders.CONTENT_TYPE, mediaType)
                        .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, message.class.name)
                        .header(HttpHeaders.ACCEPT, mediaType),
                byte[].class
        )
    }

    byte[] postMessage(String url, byte[] message, String mediaType) {
        return httpClient.toBlocking().retrieve(
                HttpRequest.POST(url, message)
                        .header(HttpHeaders.CONTENT_TYPE, mediaType)
                        .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, message.class.name)
                        .header(HttpHeaders.ACCEPT, mediaType),
                byte[].class
        )
    }

    @Factory
    static class SetCutomHeadersConfig {

        @Singleton
        @Replaces(ProtobufferCodec.class)
        ProtobufferCodec init(ExtensionRegistry registry) {
            def codec = new ProtobufferCodec(registry)
            codec.setMediaTypes([ProtobufferCodec.PROTOBUFFER_ENCODED_TYPE, ProtobufferCodec.PROTOBUFFER_ENCODED_TYPE2, SampleController.MY_PROTO_ENCODED_TYPE])
            return codec;
        }
    }
}

