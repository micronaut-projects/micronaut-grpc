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

import com.google.protobuf.Message
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.protobuf.codec.ProtobufferCodec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.RxWebSocketClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class BaseSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    RxHttpClient rxHttpClient = embeddedServer.applicationContext.createBean(
            RxHttpClient,
            embeddedServer.getURL()
    )

    @Shared
    @AutoCleanup
    RxWebSocketClient rxWebSocketClient = embeddedServer.applicationContext.createBean(
            RxWebSocketClient,
            embeddedServer.getURL()
    )

    byte[] getMessage(String url, Class aClass) {
        return rxHttpClient.toBlocking().retrieve(
                HttpRequest.GET(url)
                    .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, aClass.name)
                    .header(HttpHeaders.ACCEPT, ProtobufferCodec.PROTOBUFFER_ENCODED),
                byte[].class
        )
    }

    byte[] postMessage(String url, Message message) {
        return rxHttpClient.toBlocking().retrieve(
                HttpRequest.POST(url, message)
                        .header(HttpHeaders.CONTENT_TYPE, ProtobufferCodec.PROTOBUFFER_ENCODED)
                        .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, message.class.name)
                        .header(HttpHeaders.ACCEPT, ProtobufferCodec.PROTOBUFFER_ENCODED),
                byte[].class
        )
    }

    byte[] postMessage(String url, byte[] message) {
        return rxHttpClient.toBlocking().retrieve(
                HttpRequest.POST(url, message)
                        .header(HttpHeaders.CONTENT_TYPE, ProtobufferCodec.PROTOBUFFER_ENCODED)
                        .header(ProtobufferCodec.X_PROTOBUF_MESSAGE_HEADER, message.class.name)
                        .header(HttpHeaders.ACCEPT, ProtobufferCodec.PROTOBUFFER_ENCODED),
                byte[].class
        )
    }
}
