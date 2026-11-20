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
import io.grpc.Status;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.grpc.GrpcProxyService;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * HTTP/1.1 bridge that exposes annotated gRPC services via the gRPC-Web wire protocol.
 */
@Experimental
@Singleton
// The proxy call waits for the service to complete its response observer, so it must not run on the event loop.
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/${micronaut.grpc.web.path:`grpc-web`}")
public final class GrpcWebController {
    static final String GRPC_WEB = "application/grpc-web";
    static final String GRPC_WEB_PROTO = "application/grpc-web+proto";
    static final String GRPC_WEB_TEXT = "application/grpc-web-text";
    static final String GRPC_WEB_TEXT_PROTO = "application/grpc-web-text+proto";
    /**
     * The description sent for failures that carry no gRPC status, so that internal error details are not exposed.
     */
    static final String INTERNAL_ERROR_MESSAGE = "gRPC method invocation failed";
    private static final Logger LOG = LoggerFactory.getLogger(GrpcWebController.class);
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of(GRPC_WEB, GRPC_WEB_PROTO, GRPC_WEB_TEXT, GRPC_WEB_TEXT_PROTO);
    private static final byte TRAILER_FRAME = (byte) 0x80;
    private static final byte COMPRESSED_FRAME = 0x01;

    private final GrpcProxyService grpcProxyService;

    public GrpcWebController(GrpcProxyService grpcProxyService) {
        this.grpcProxyService = grpcProxyService;
    }

    @Post(
        uri = "/{serviceName}/{methodName}",
        consumes = {GRPC_WEB, GRPC_WEB_PROTO, GRPC_WEB_TEXT, GRPC_WEB_TEXT_PROTO},
        produces = {GRPC_WEB, GRPC_WEB_PROTO, GRPC_WEB_TEXT, GRPC_WEB_TEXT_PROTO}
    )
    public HttpResponse<byte[]> invokeMethod(@PathVariable String serviceName,
                                             @PathVariable String methodName,
                                             @Body byte[] requestBody,
                                             @Header(HttpHeaders.CONTENT_TYPE) String contentType) {
        String responseContentType = responseContentType(contentType);
        boolean textEncoded = responseContentType.startsWith(GRPC_WEB_TEXT);
        try {
            byte[] grpcPayload = decodeRequestBody(requestBody, textEncoded);
            Message requestMessage = grpcProxyService.parseRequestMessage(serviceName, methodName, extractMessage(grpcPayload));
            List<Message> responses = grpcProxyService.invokeGrpcMethod(serviceName, methodName, requestMessage);
            return response(responses, Status.Code.OK.value(), null, responseContentType);
        } catch (MethodNotFoundException | ServiceNotFoundException e) {
            return response(List.of(), Status.Code.UNIMPLEMENTED.value(), e.getMessage(), responseContentType);
        } catch (MalformedRequestException e) {
            return response(List.of(), Status.Code.INVALID_ARGUMENT.value(), e.getMessage(), responseContentType);
        } catch (HttpStatusException e) {
            if (e.getStatus().getCode() < HttpStatus.INTERNAL_SERVER_ERROR.getCode()) {
                return response(List.of(), Status.Code.INVALID_ARGUMENT.value(), e.getMessage(), responseContentType);
            }
            return failureResponse(e, responseContentType);
        } catch (RuntimeException e) {
            return failureResponse(e, responseContentType);
        }
    }

    /**
     * Answers a failed invocation in gRPC-Web framing. A gRPC status raised by the service, either passed to
     * {@code onError} or thrown, is sent as is. Any other failure is sent as {@code INTERNAL} without its message.
     */
    private MutableHttpResponse<byte[]> failureResponse(RuntimeException failure, String responseContentType) {
        Status status = Status.fromThrowable(failure);
        if (status.getCode() == Status.Code.UNKNOWN && status.getCause() == failure) {
            // No gRPC status anywhere in the cause chain.
            LOG.error("gRPC-Web invocation failed", failure);
            return response(List.of(), Status.Code.INTERNAL.value(), INTERNAL_ERROR_MESSAGE, responseContentType);
        }
        return response(List.of(), status.getCode().value(), status.getDescription(), responseContentType);
    }

    /**
     * Builds a response whose body holds the data frames followed by the trailer frame. The status is only sent in the
     * trailer frame: status headers would mark a trailers-only response, which must not have a body.
     */
    private MutableHttpResponse<byte[]> response(List<Message> messages,
                                                 int grpcStatus,
                                                 @Nullable String grpcMessage,
                                                 String responseContentType) {
        byte[] body = encodeResponseBody(messages, grpcStatus, grpcMessage, responseContentType.startsWith(GRPC_WEB_TEXT));
        return baseResponse(body, responseContentType);
    }

    private MutableHttpResponse<byte[]> baseResponse(byte[] body, String responseContentType) {
        return HttpResponse.ok(body)
            .contentType(MediaType.of(responseContentType))
            .header("x-grpc-web", "1");
    }

    private byte[] decodeRequestBody(byte[] requestBody, boolean textEncoded) {
        if (!textEncoded) {
            return requestBody;
        }
        try {
            return Base64.getDecoder().decode(requestBody);
        } catch (IllegalArgumentException e) {
            throw new MalformedRequestException("Malformed base64 gRPC-Web request");
        }
    }

    private byte[] extractMessage(byte[] grpcPayload) {
        if (grpcPayload.length < 5) {
            throw new MalformedRequestException("gRPC-Web request body is too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(grpcPayload);
        byte flags = buffer.get();
        if ((flags & TRAILER_FRAME) != 0) {
            throw new MalformedRequestException("gRPC-Web request must start with a data frame");
        }
        if ((flags & COMPRESSED_FRAME) != 0) {
            throw new MalformedRequestException("Compressed gRPC-Web requests are not supported");
        }
        int length = buffer.getInt();
        if (length < 0 || buffer.remaining() != length) {
            throw new MalformedRequestException("Malformed gRPC-Web frame length");
        }
        byte[] message = new byte[length];
        buffer.get(message);
        return message;
    }

    private byte[] encodeResponseBody(List<Message> messages, int grpcStatus, @Nullable String grpcMessage, boolean textEncoded) {
        byte[] binaryBody = encodeBinaryResponse(messages, grpcStatus, grpcMessage);
        if (!textEncoded) {
            return binaryBody;
        }
        return Base64.getEncoder().encode(binaryBody);
    }

    private byte[] encodeBinaryResponse(List<Message> messages, int grpcStatus, @Nullable String grpcMessage) {
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

    private byte[] trailerFrame(int grpcStatus, @Nullable String grpcMessage) {
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

    /**
     * Answers with the media type of the request, ignoring case and parameters, as gRPC-Web clients expect.
     */
    private static String responseContentType(@Nullable String contentType) {
        if (contentType != null) {
            String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED_CONTENT_TYPES.contains(mediaType)) {
                return mediaType;
            }
        }
        return GRPC_WEB_PROTO;
    }

    private static String percentEncodeMessage(String value) {
        // gRPC grpc-message percent-encoding per the gRPC HTTP/2 spec:
        // allow printable ASCII 0x20–0x7E except '%' (0x25) unencoded; encode everything else.
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        var sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c >= 0x20 && c <= 0x7E && c != '%') {
                sb.append((char) c);
            } else {
                sb.append(String.format("%%%02X", c));
            }
        }
        return sb.toString();
    }

    /**
     * A request that is not a valid gRPC-Web request, answered with {@code INVALID_ARGUMENT}.
     */
    private static final class MalformedRequestException extends RuntimeException {
        MalformedRequestException(String message) {
            super(message, null, false, false);
        }
    }
}
