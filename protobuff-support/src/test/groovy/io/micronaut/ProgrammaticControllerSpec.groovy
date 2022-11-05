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

class ProgrammaticControllerSpec extends BaseSpec {

    String url = embeddedServer.getURL().toString() + '/town'

    void "sample city should be dublin/using programmatic controller controller"() {
        when:'The message is requested from the sever=[#url]'
            def response = getMessage(url, Example.GeoPoint.class)
        and:'The message is parser'
            Example.GeoPoint city  = Example.GeoPoint.parseFrom(response)
        then:'Should be Dublin'
            SampleController.DUBLIN == city
    }
}
