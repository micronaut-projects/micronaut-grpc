package io.micronaut

import com.example.wire.Example

class SimpleHttpGetSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/city'

    void "sample city should be dublin/using sample controller"() {
        when:'The message is requested from the sever=[#url]'
            def response = getMessage(url, Example.GeoPoint.class)
        and:'The message is parser'
            Example.GeoPoint city  = Example.GeoPoint.parseFrom(response)
        then:'Should be Dublin'
            SampleController.DUBLIN == city
    }
}
