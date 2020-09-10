package io.micronaut

import com.example.wire.Example
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage

import java.util.concurrent.ConcurrentLinkedQueue

@ClientWebSocket("/ws/echo")
abstract class SampleWebsocketClient implements AutoCloseable {

    Collection<Example.GeoPoint> replies = new ConcurrentLinkedQueue<>()

    @OnMessage
    @Consumes(MediaType.APPLICATION_JSON)
    void onMessage(Example.GeoPoint message) {
        replies.add(message)
    }

    @Produces(MediaType.APPLICATION_JSON)
    abstract void sendJson(Example.GeoPoint geoPoint)
}
