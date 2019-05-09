package io.micronaut

import com.example.wire.Example

class SimpleHttpPostSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/nearby'

    void "near by Dublin should be Dublin"() {
        setup:
            Example.GeoPoint message = SampleController.DUBLIN
        when:'The message is posted to the server=[#url]'
            def response = postMessage(url, message)
        and:'The message is parsed'
            Example.GeoPoint city = Example.GeoPoint.parseFrom(response)
        then:'Should be Dublin'
            SampleController.DUBLIN == city

        when:'The byte[] is posted to the server=[#url]'
            response = postMessage(url, message.toByteArray())
        then:'The message is parsed'
            Example.GeoPoint.parseFrom(response) == SampleController.DUBLIN
    }
}
