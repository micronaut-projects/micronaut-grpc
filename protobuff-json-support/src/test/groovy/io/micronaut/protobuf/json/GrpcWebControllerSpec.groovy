package io.micronaut.protobuf.json

import io.grpc.Status
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.protobuf.json.exception.GrpcInvocationException
import io.micronaut.protobuf.json.exception.MethodNotFoundException
import io.micronaut.protobuf.json.grpc.GrpcProxyService
import org.example.grpc.HelloRequest
import org.example.grpc.HelloResponse
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

class GrpcWebControllerSpec extends Specification {

    GrpcProxyService grpcProxyService = Mock()
    GrpcWebController controller = new GrpcWebController(grpcProxyService)

    def "maps service lookup failures to grpc status 12"() {
        given:
        byte[] payload = frame(HelloRequest.newBuilder().setName("Micronaut").build().toByteArray())

        when:
        HttpResponse<byte[]> response = controller.invokeMethod("MissingService", "sayHello", payload, GrpcWebController.GRPC_WEB_PROTO)

        then:
        1 * grpcProxyService.parseRequestMessage("MissingService", "sayHello", _ as byte[]) >> {
            throw new MethodNotFoundException("sayHello")
        }
        0 * grpcProxyService.invokeGrpcMethod(_, _, _)

        and: "the status is only sent in the trailer frame"
        response.header("grpc-status") == null
        response.header("grpc-message") == null
        response.header("trailer") == null
        response.header("x-grpc-web") == "1"

        and:
        def frames = readFrames(response.body())
        frames*.trailer == [true]
        new String(frames[0].payload, StandardCharsets.US_ASCII).contains("grpc-status:12")
        new String(frames[0].payload, StandardCharsets.US_ASCII).contains("grpc-message:Method 'sayHello' not found")
    }

    def "maps invocation failures without a gRPC status to INTERNAL without exposing the message"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Micronaut").build()
        byte[] payload = frame(request.toByteArray())

        when:
        HttpResponse<byte[]> response = controller.invokeMethod("GreeterService", "sayHello", payload, GrpcWebController.GRPC_WEB_PROTO)

        then:
        1 * grpcProxyService.parseRequestMessage("GreeterService", "sayHello", _ as byte[]) >> request
        1 * grpcProxyService.invokeGrpcMethod("GreeterService", "sayHello", request) >> {
            throw new GrpcInvocationException("bad\r\nmessage")
        }

        and:
        def frames = readFrames(response.body())
        frames*.trailer == [true]
        def trailers = new String(frames[0].payload, StandardCharsets.US_ASCII)
        trailers.contains("grpc-status:13")
        trailers.contains("grpc-message:" + GrpcWebController.INTERNAL_ERROR_MESSAGE)
        !trailers.contains("bad")
    }

    @Unroll
    def "sends a gRPC status raised by the service: #scenario"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Micronaut").build()

        when:
        HttpResponse<byte[]> response = controller.invokeMethod("GreeterService", "sayHello", frame(request.toByteArray()), GrpcWebController.GRPC_WEB_PROTO)

        then:
        1 * grpcProxyService.parseRequestMessage("GreeterService", "sayHello", _ as byte[]) >> request
        1 * grpcProxyService.invokeGrpcMethod("GreeterService", "sayHello", request) >> { throw failure }

        and:
        def frames = readFrames(response.body())
        frames*.trailer == [true]
        def trailers = new String(frames[0].payload, StandardCharsets.US_ASCII)
        trailers.contains("grpc-status:${code}\r\n")
        trailers.contains("grpc-message:${message}\r\n")

        where:
        scenario                       | failure                                                                                                       | code | message
        "passed to onError"            | new GrpcInvocationException("Error executing gRPC method", Status.NOT_FOUND.withDescription("gone").asRuntimeException()) | 5    | "gone"
        "thrown by the method"         | Status.PERMISSION_DENIED.withDescription("no access").asRuntimeException()                                   | 7    | "no access"
        "thrown without a status"      | new IllegalStateException("secret detail")                                                                    | 13   | GrpcWebController.INTERNAL_ERROR_MESSAGE
        "invalid protobuf request"     | new HttpStatusException(HttpStatus.BAD_REQUEST, "Failed to parse protobuf request")                           | 3    | "Failed to parse protobuf request"
    }

    @Unroll
    def "answers with the request media type #contentType"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Micronaut").build()
        HelloResponse responseMessage = HelloResponse.newBuilder().setGreeting("Hello Micronaut").build()
        byte[] payload = frame(request.toByteArray())
        boolean text = expected.startsWith(GrpcWebController.GRPC_WEB_TEXT)

        when:
        HttpResponse<byte[]> response = controller.invokeMethod("GreeterService", "sayHello", text ? Base64.encoder.encode(payload) : payload, contentType)

        then:
        1 * grpcProxyService.parseRequestMessage("GreeterService", "sayHello", _ as byte[]) >> request
        1 * grpcProxyService.invokeGrpcMethod("GreeterService", "sayHello", request) >> [responseMessage]

        and:
        response.contentType.get().toString() == expected
        def frames = readFrames(text ? Base64.decoder.decode(response.body()) : response.body())
        HelloResponse.parseFrom(frames[0].payload).greeting == "Hello Micronaut"

        where:
        contentType                                    | expected
        "application/grpc-web"                         | "application/grpc-web"
        "application/grpc-web-text"                    | "application/grpc-web-text"
        "Application/GRPC-Web-Text+proto"              | "application/grpc-web-text+proto"
        "application/grpc-web+proto; charset=utf-8"    | "application/grpc-web+proto"
    }

    @Unroll
    def "maps malformed request '#scenario' to grpc status 3"() {
        when:
        HttpResponse<byte[]> response = controller.invokeMethod("GreeterService", "sayHello", requestBody, contentType)

        then:
        0 * grpcProxyService._

        and:
        byte[] encodedBody = contentType == GrpcWebController.GRPC_WEB_TEXT_PROTO ? Base64.decoder.decode(response.body()) : response.body()
        def frames = readFrames(encodedBody)
        frames*.trailer == [true]
        response.header("grpc-status") == null
        new String(frames[0].payload, StandardCharsets.US_ASCII).contains("grpc-status:3")
        new String(frames[0].payload, StandardCharsets.US_ASCII).contains("grpc-message:${expectedMessage}")

        where:
        scenario             | contentType                            | requestBody                           | expectedMessage
        "malformed base64"   | GrpcWebController.GRPC_WEB_TEXT_PROTO  | "%%%".getBytes(StandardCharsets.US_ASCII) | "Malformed base64 gRPC-Web request"
        "short frame"        | GrpcWebController.GRPC_WEB_PROTO       | [0x00, 0x00, 0x00, 0x00] as byte[]    | "gRPC-Web request body is too short"
        "trailer frame"      | GrpcWebController.GRPC_WEB_PROTO       | frameWithFlag((byte) 0x80, new byte[0]) | "gRPC-Web request must start with a data frame"
        "compressed frame"   | GrpcWebController.GRPC_WEB_PROTO       | frameWithFlag((byte) 0x01, new byte[0]) | "Compressed gRPC-Web requests are not supported"
        "length mismatch"    | GrpcWebController.GRPC_WEB_PROTO       | [0x00, 0x00, 0x00, 0x00, 0x01] as byte[] | "Malformed gRPC-Web frame length"
    }

    def "encodes protobuf responses as grpc-web frames"() {
        given:
        HelloRequest request = HelloRequest.newBuilder().setName("Micronaut").build()
        HelloResponse responseMessage = HelloResponse.newBuilder().setGreeting("Hello Micronaut").build()
        byte[] payload = frame(request.toByteArray())

        when:
        HttpResponse<byte[]> response = controller.invokeMethod("GreeterService", "sayHello", payload, GrpcWebController.GRPC_WEB_PROTO)

        then:
        1 * grpcProxyService.parseRequestMessage("GreeterService", "sayHello", _ as byte[]) >> request
        1 * grpcProxyService.invokeGrpcMethod("GreeterService", "sayHello", request) >> [responseMessage]

        and:
        response.header("grpc-status") == null
        def frames = readFrames(response.body())
        frames*.trailer == [false, true]
        HelloResponse.parseFrom(frames[0].payload).greeting == "Hello Micronaut"
        new String(frames[1].payload, StandardCharsets.US_ASCII).contains("grpc-status:0")
    }

    private static byte[] frame(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(5 + message.length)
        buffer.put((byte) 0)
        buffer.putInt(message.length)
        buffer.put(message)
        buffer.array()
    }

    private static byte[] frameWithFlag(byte flag, byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(5 + message.length)
        buffer.put(flag)
        buffer.putInt(message.length)
        buffer.put(message)
        buffer.array()
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
        frames
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
