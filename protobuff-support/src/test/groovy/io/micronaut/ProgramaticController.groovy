package io.micronaut

import com.example.wire.Example
import groovy.transform.CompileStatic
import io.micronaut.http.annotation.Get
import io.micronaut.http.codec.ProtobufferCodec

@CompileStatic
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
