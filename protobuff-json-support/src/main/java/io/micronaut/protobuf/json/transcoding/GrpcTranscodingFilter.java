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
package io.micronaut.protobuf.json.transcoding;

import com.google.gson.JsonObject;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.CloseableAvailableByteBody;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Answers HTTP requests that match a {@code google.api.http} rule by calling the gRPC method in process, and lets
 * every other request through unchanged.
 *
 * <p>A filter is used instead of routes so that the HTTP rule path templates, which the Micronaut URI template syntax
 * can't express, are matched exactly, and so that no route of the application is shadowed. The filter runs after the
 * other filters of the application, such as security and CORS.</p>
 *
 * @since 5.3.0
 */
@Internal
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
@Requires(property = GrpcTranscodingConfiguration.ENABLED, value = StringUtils.TRUE)
public final class GrpcTranscodingFilter implements Ordered {

    /**
     * HTTP headers that are not forwarded as gRPC metadata.
     */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
        "host", "connection", "content-length", "content-type", "transfer-encoding", "te", "upgrade", "keep-alive",
        "accept-encoding", "expect", "http2-settings", "proxy-connection", "user-agent");

    private final GrpcTranscodingRegistry registry;
    private final GrpcTranscodingConfiguration configuration;
    private final HttpRuleMessageMapper mapper = new HttpRuleMessageMapper();

    GrpcTranscodingFilter(GrpcTranscodingRegistry registry, GrpcTranscodingConfiguration configuration) {
        this.registry = registry;
        this.configuration = configuration;
    }

    /**
     * Transcodes a matching request.
     *
     * @param request The request
     * @return The response, or a {@code null} result to let the request continue
     */
    @RequestFilter
    public CompletableFuture<@Nullable HttpResponse<?>> transcode(HttpRequest<?> request) {
        GrpcTranscodingRegistry.Match match = registry.find(request.getMethodName().toUpperCase(Locale.ROOT), request.getUri().getRawPath());
        if (match == null) {
            return CompletableFuture.completedFuture(null);
        }
        return body(request)
            .thenCompose(body -> call(match, request, body))
            .exceptionally(GrpcTranscodingFilter::error);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 100;
    }

    private static CompletableFuture<@Nullable String> body(HttpRequest<?> request) {
        if (request instanceof ServerHttpRequest<?> serverRequest) {
            return serverRequest.byteBody().buffer().thenApply(GrpcTranscodingFilter::read);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static @Nullable String read(CloseableAvailableByteBody body) {
        try (body) {
            return body.length() == 0 ? null : body.toString(StandardCharsets.UTF_8);
        }
    }

    private CompletableFuture<@Nullable HttpResponse<?>> call(GrpcTranscodingRegistry.Match match,
                                                             HttpRequest<?> request,
                                                             @Nullable String body) {
        TranscodedMethod method = match.method();
        DynamicMessage message;
        try {
            message = mapper.request(method, match.variables(), parameters(request), body);
        } catch (InvalidProtocolBufferException e) {
            return CompletableFuture.completedFuture(error(Status.INVALID_ARGUMENT.withDescription(e.getMessage())));
        }
        Channel channel = ClientInterceptors.intercept(registry.channel(), MetadataUtils.newAttachHeadersInterceptor(metadata(request.getHeaders())));
        CallOptions options = CallOptions.DEFAULT.withDeadlineAfter(configuration.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        CompletableFuture<@Nullable HttpResponse<?>> result = new CompletableFuture<>();
        ClientCalls.asyncUnaryCall(channel.newCall(method.grpcMethod(), options), message, new StreamObserver<>() {
            private @Nullable DynamicMessage response;

            @Override
            public void onNext(DynamicMessage value) {
                response = value;
            }

            @Override
            public void onError(Throwable t) {
                result.complete(error(t));
            }

            @Override
            public void onCompleted() {
                DynamicMessage value = response;
                if (value == null) {
                    result.complete(error(Status.INTERNAL.withDescription("No response message")));
                    return;
                }
                try {
                    result.complete(HttpResponse.ok(mapper.response(method, value)).contentType(MediaType.APPLICATION_JSON_TYPE));
                } catch (InvalidProtocolBufferException e) {
                    result.complete(error(Status.INTERNAL.withDescription(e.getMessage())));
                }
            }
        });
        return result;
    }

    private static Map<String, List<String>> parameters(HttpRequest<?> request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (String name : request.getParameters().names()) {
            parameters.put(name, request.getParameters().getAll(name));
        }
        return parameters;
    }

    private static Metadata metadata(HttpHeaders headers) {
        Metadata metadata = new Metadata();
        for (String name : headers.names()) {
            String key = name.toLowerCase(Locale.ROOT);
            if (EXCLUDED_HEADERS.contains(key) || key.startsWith(":") || key.startsWith("grpc-") || key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                continue;
            }
            Metadata.Key<String> metadataKey;
            try {
                metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (String value : headers.getAll(name)) {
                metadata.put(metadataKey, value);
            }
        }
        return metadata;
    }

    private static HttpResponse<?> error(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return error(Status.fromThrowable(cause));
    }

    private static HttpResponse<?> error(Status status) {
        Status.Code code = status.getCode();
        JsonObject error = new JsonObject();
        error.addProperty("code", GrpcHttpStatus.of(code));
        error.addProperty("message", status.getDescription() == null ? code.name() : status.getDescription());
        error.addProperty("status", code.name());
        JsonObject body = new JsonObject();
        body.add("error", error);
        MutableHttpResponse<String> response = HttpResponse.status(GrpcHttpStatus.of(code), code.name());
        return response.body(body.toString()).contentType(MediaType.APPLICATION_JSON_TYPE);
    }
}
