package io.micronaut

import com.google.protobuf.Message
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.protobuf.codec.ProtobufferCodec
import io.micronaut.runtime.server.EmbeddedServer
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
}
