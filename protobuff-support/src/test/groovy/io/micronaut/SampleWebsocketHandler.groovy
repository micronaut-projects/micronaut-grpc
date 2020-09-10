package io.micronaut

import com.example.wire.Example
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket

@ServerWebSocket("/ws/echo")
class SampleWebsocketHandler {

    @OnMessage
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void onMessage(WebSocketSession session, @Body Example.GeoPoint payload) {
        session.sendSync(payload)
    }
}
