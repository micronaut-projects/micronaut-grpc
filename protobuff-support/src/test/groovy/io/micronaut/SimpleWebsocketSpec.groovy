package io.micronaut

import com.example.wire.Example
import spock.util.concurrent.PollingConditions

class SimpleWebsocketSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/ws/echo'

    void "test json from proto generated classes websocket exchange"() {
        setup:
            Example.GeoPoint message = SampleController.DUBLIN
            SampleWebsocketClient sampleWebsocketClient = rxWebSocketClient.connect(SampleWebsocketClient, url).blockingFirst()
            PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)
        when: 'Json is sent over ws to the server=[#url]'
            sampleWebsocketClient.sendJson(message)
        then: 'Echoed message is parsed'
            conditions.eventually {
                sampleWebsocketClient.replies.contains(message)
            }
    }
}
