package io.micronaut.protobuf.json

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

@MicronautTest(environments = ["test"])
class GrpcWebControllerIntegrationSpec extends Specification {

    private static final String CONTENT_TYPE = "application/grpc-web+proto"
    private static final String TEXT_CONTENT_TYPE = "application/grpc-web-text+proto"

    @Inject
    @Client("/")
    HttpClient httpClient

    def "invoke gRPC service via gRPC-Web"() {
        given:
        byte[] requestPayload = frame(HelloRequest.newBuilder()
                .setName("Micronaut Web")
                .build()
                .toByteArray())

        when:
        HttpResponse<byte[]> response = httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-web/GreeterService/sayHello", requestPayload)
                        .header("content-type", CONTENT_TYPE)
                        .header("accept", CONTENT_TYPE)
                        .header("x-grpc-web", "1"),
                byte[]
        )

        then:
        response.status.code == 200
        response.header("content-type").startsWith(CONTENT_TYPE)

        and:
        def frames = readFrames(response.body())
        frames.size() == 2
        !frames[0].trailer
        HelloResponse.parseFrom(frames[0].payload).greeting == "TEST Hello, Micronaut Web"
        frames[1].trailer
        new String(frames[1].payload, StandardCharsets.US_ASCII).contains("grpc-status:0")
    }

    def "invoke gRPC service via base64 gRPC-Web text"() {
        given:
        byte[] requestPayload = Base64.encoder.encode(frame(HelloRequest.newBuilder()
                .setName("Micronaut Text")
                .build()
                .toByteArray()))

        when:
        HttpResponse<byte[]> response = httpClient.toBlocking().exchange(
                HttpRequest.POST("/grpc-web/GreeterService/sayHello", requestPayload)
                        .header("content-type", TEXT_CONTENT_TYPE)
                        .header("accept", TEXT_CONTENT_TYPE)
                        .header("x-grpc-web", "1"),
                byte[]
        )

        then:
        response.status.code == 200
        response.header("content-type").startsWith(TEXT_CONTENT_TYPE)

        and:
        def frames = readFrames(Base64.decoder.decode(response.body()))
        HelloResponse.parseFrom(frames[0].payload).greeting == "TEST Hello, Micronaut Text"
        frames[1].trailer
        new String(frames[1].payload, StandardCharsets.US_ASCII).contains("grpc-status:0")
    }

    private static byte[] frame(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(5 + message.length)
        buffer.put((byte) 0)
        buffer.putInt(message.length)
        buffer.put(message)
        return buffer.array()
    }

    private static List<GrpcWebFrame> readFrames(byte[] payload) {
        List<GrpcWebFrame> frames = []
        ByteBuffer buffer = ByteBuffer.wrap(payload)
        while (buffer.remaining() > 0) {
            byte flags = buffer.get()
            int length = buffer.getInt()
            byte[] framePayload = new byte[length]
            buffer.get(framePayload)
            frames.add(new GrpcWebFrame((flags & 0x80) != 0, framePayload))
        }
        return frames
    }

    private static final class GrpcWebFrame {
        final boolean trailer
        final byte[] payload

        GrpcWebFrame(boolean trailer, byte[] payload) {
            this.trailer = trailer
            this.payload = payload
        }
    }
}
