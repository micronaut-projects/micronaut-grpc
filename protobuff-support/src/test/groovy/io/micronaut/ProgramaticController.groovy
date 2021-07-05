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
import groovy.transform.CompileStatic
import io.micronaut.http.annotation.Get
import io.micronaut.protobuf.codec.ProtobufferCodec

@CompileStatic
@jakarta.inject.Singleton
class ProgramaticController {
    public static Example.GeoPoint DUBLIN = Example.GeoPoint.newBuilder()
            .setLat(53.350140D)
            .setLng(-6.266155D)
            .build()

    @Get(processes = ProtobufferCodec.PROTOBUFFER_ENCODED)
    Example.GeoPoint city() {
        DUBLIN
    }
}
