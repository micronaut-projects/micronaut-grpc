/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.protobuf.json;

import com.google.protobuf.Message;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.protobuf.json.exception.GrpcInvocationException;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.grpc.GrpcProxyService;
import jakarta.inject.Singleton;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * HTTP/1.1 bridge that exposes annotated gRPC services via the gRPC-Web wire protocol.
 */
@Experimental
@Singleton
@Controller("/${micronaut.grpc.web.path:`grpc-web`}")
public final class GrpcWebController {
    static final String GRPC_WEB_PROTO = "application/grpc-web+proto";
    static final String GRPC_WEB_TEXT_PROTO = "application/grpc-web-text+proto";
    private static final byte TRAILER_FRAME = (byte) 0x80;
    private static final byte COMPRESSED_FRAME = 0x01;

    private final GrpcProxyService grpcProxyService;

    public GrpcWebController(GrpcProxyService grpcProxyService) {
        this.grpcProxyService = grpcProxyService;
    }

    @Post(
        uri = "/{serviceName}/{methodName}",
        consumes = {GRPC_WEB_PROTO, GRPC_WEB_TEXT_PROTO},
        produces = {GRPC_WEB_PROTO, GRPC_WEB_TEXT_PROTO}
    )
    public HttpResponse<byte[]> invokeMethod(@PathVariable String serviceName,
                                             @PathVariable String methodName,
                                             @Body byte[] requestBody,
                                             @Header(HttpHeaders.CONTENT_TYPE) String contentType) {
        boolean textEncoded = isTextEncoded(contentType);
        try {
            byte[] grpcPayload = decodeRequestBody(requestBody, textEncoded);
            Message requestMessage = grpcProxyService.parseRequestMessage(serviceName, methodName, extractMessage(grpcPayload));
            List<Message> responses = grpcProxyService.invokeGrpcMethod(serviceName, methodName, requestMessage);
            return successResponse(responses, textEncoded);
        } catch (MethodNotFoundException | ServiceNotFoundException e) {
            return errorResponse(textEncoded, 12, e.getMessage());
        } catch (GrpcInvocationException e) {
            return errorResponse(textEncoded, 13, e.getMessage());
        } catch (HttpStatusException e) {
            return errorResponse(textEncoded, 3, e.getMessage());
        }
    }

    private MutableHttpResponse<byte[]> successResponse(List<Message> responses, boolean textEncoded) {
        byte[] responseBody = encodeResponseBody(responses, 0, null, textEncoded);
        return baseResponse(responseBody, textEncoded)
            .header("grpc-status", "0");
    }

    private MutableHttpResponse<byte[]> errorResponse(boolean textEncoded, int grpcStatus, String grpcMessage) {
        return baseResponse(encodeResponseBody(List.of(), grpcStatus, grpcMessage, textEncoded), textEncoded)
            .header("grpc-status", Integer.toString(grpcStatus))
            .header("grpc-message", percentEncodeMessage(grpcMessage));
    }

    private MutableHttpResponse<byte[]> baseResponse(byte[] body, boolean textEncoded) {
        return HttpResponse.ok(body)
            .contentType(MediaType.of(textEncoded ? GRPC_WEB_TEXT_PROTO : GRPC_WEB_PROTO))
            .header("x-grpc-web", "1")
            .header("access-control-expose-headers", "grpc-status,grpc-message")
            .header("trailer", "grpc-status,grpc-message");
    }

    private byte[] decodeRequestBody(byte[] requestBody, boolean textEncoded) {
        if (!textEncoded) {
            return requestBody;
        }
        try {
            return Base64.getDecoder().decode(requestBody);
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Malformed base64 gRPC-Web request");
        }
    }

    private byte[] extractMessage(byte[] grpcPayload) {
        if (grpcPayload.length < 5) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "gRPC-Web request body is too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(grpcPayload);
        byte flags = buffer.get();
        if ((flags & TRAILER_FRAME) != 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "gRPC-Web request must start with a data frame");
        }
        if ((flags & COMPRESSED_FRAME) != 0) {
            throw new HttpStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Compressed gRPC-Web requests are not supported");
        }
        int length = buffer.getInt();
        if (length < 0 || buffer.remaining() != length) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Malformed gRPC-Web frame length");
        }
        byte[] message = new byte[length];
        buffer.get(message);
        return message;
    }

    private byte[] encodeResponseBody(List<Message> messages, int grpcStatus, String grpcMessage, boolean textEncoded) {
        byte[] binaryBody = encodeBinaryResponse(messages, grpcStatus, grpcMessage);
        if (!textEncoded) {
            return binaryBody;
        }
        return Base64.getEncoder().encode(binaryBody);
    }

    private byte[] encodeBinaryResponse(List<Message> messages, int grpcStatus, String grpcMessage) {
        byte[] trailers = trailerFrame(grpcStatus, grpcMessage);
        int totalLength = trailers.length;
        for (Message message : messages) {
            totalLength += 5 + message.getSerializedSize();
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        for (Message message : messages) {
            byte[] payload = message.toByteArray();
            buffer.put((byte) 0);
            buffer.putInt(payload.length);
            buffer.put(payload);
        }
        buffer.put(trailers);
        return buffer.array();
    }

    private byte[] trailerFrame(int grpcStatus, String grpcMessage) {
        StringBuilder trailers = new StringBuilder("grpc-status:").append(grpcStatus).append("\r\n");
        if (grpcMessage != null && !grpcMessage.isBlank()) {
            trailers.append("grpc-message:").append(percentEncodeMessage(grpcMessage)).append("\r\n");
        }
        byte[] payload = trailers.toString().getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(5 + payload.length);
        buffer.put(TRAILER_FRAME);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private boolean isTextEncoded(String contentType) {
        return contentType != null && contentType.startsWith(GRPC_WEB_TEXT_PROTO);
    }

    private static String percentEncodeMessage(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
