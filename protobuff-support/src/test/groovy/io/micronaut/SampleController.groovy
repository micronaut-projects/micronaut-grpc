package io.micronaut

import com.example.wire.Example
import groovy.transform.CompileStatic
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.codec.ProtobufferCodec

@Controller
@CompileStatic
class SampleController {
    public static Example.GeoPoint DUBLIN = Example.GeoPoint.newBuilder()
            .setLat(53.350140D)
            .setLng(-6.266155D)
            .build()

    @Get(value = "/city", processes = ProtobufferCodec.PROTOBUFFER_ENCODED)
    Example.GeoPoint city() {
        DUBLIN
    }

    @Post(value = "/nearby", processes = ProtobufferCodec.PROTOBUFFER_ENCODED)
    Example.GeoPoint suggestVisitNearBy(@Body Example.GeoPoint point) {
        DUBLIN
    }
}
