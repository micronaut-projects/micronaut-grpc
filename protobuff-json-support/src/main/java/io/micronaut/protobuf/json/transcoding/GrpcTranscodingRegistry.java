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

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Finds the unary gRPC methods that declare {@code google.api.http} rules and serves them through an in-process gRPC
 * server built from the same service and interceptor beans as the application's gRPC server.
 *
 * <p>The rules are read from the protobuf descriptors that {@code protoc}-generated services expose; no reflection is
 * used. Requests and responses are {@link DynamicMessage} instances, so no generated message classes are needed.</p>
 *
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(property = GrpcTranscodingConfiguration.ENABLED, value = StringUtils.TRUE)
final class GrpcTranscodingRegistry implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcTranscodingRegistry.class);
    /**
     * The custom HTTP method kind that matches any HTTP method.
     */
    private static final String ANY_METHOD = "*";

    private final List<TranscodedMethod> methods;
    private final Server server;
    private final ManagedChannel channel;

    GrpcTranscodingRegistry(List<BindableService> services,
                            List<ServerServiceDefinition> definitions,
                            List<ServerInterceptor> interceptors,
                            @Named(TaskExecutors.BLOCKING) ExecutorService executor) {
        List<ServerServiceDefinition> all = new ArrayList<>(definitions);
        for (BindableService service : services) {
            all.add(service.bindService());
        }
        List<TranscodedMethod> found = new ArrayList<>();
        for (ServerServiceDefinition definition : all) {
            collect(definition, found);
        }
        // Prefer the more specific template when several match the same request
        found.sort(Comparator.comparingInt((TranscodedMethod m) -> m.template().literalLength()).reversed());
        this.methods = List.copyOf(found);

        String name = "micronaut-grpc-transcoding-" + UUID.randomUUID();
        InProcessServerBuilder builder = InProcessServerBuilder.forName(name).executor(executor);
        List<ServerInterceptor> ordered = new ArrayList<>(interceptors);
        OrderUtil.reverseSort(ordered);
        for (ServerServiceDefinition definition : all) {
            builder.addService(ServerInterceptors.intercept(definition, ordered));
        }
        try {
            this.server = builder.build().start();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to start the in-process gRPC server for REST transcoding", e);
        }
        this.channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        if (LOG.isDebugEnabled()) {
            for (TranscodedMethod method : methods) {
                LOG.debug("Transcoding {} {} to {}", method.httpMethod(), method.template().template(), method.grpcMethod().getFullMethodName());
            }
        }
    }

    /**
     * @return The transcoded methods, the most specific templates first
     */
    List<TranscodedMethod> methods() {
        return methods;
    }

    /**
     * @return The channel to the in-process server
     */
    ManagedChannel channel() {
        return channel;
    }

    /**
     * Finds the binding that matches a request. A binding of the HTTP method wins over a custom binding of any
     * method ({@code kind: "*"}).
     *
     * @param httpMethod The HTTP method name
     * @param rawPath The raw request path
     * @return The match, or {@code null}
     */
    @Nullable Match find(String httpMethod, String rawPath) {
        Match wildcard = null;
        for (TranscodedMethod method : methods) {
            boolean exact = method.httpMethod().equals(httpMethod);
            if (exact || (wildcard == null && ANY_METHOD.equals(method.httpMethod()))) {
                Map<String, String> variables = method.template().match(rawPath);
                if (variables != null) {
                    if (exact) {
                        return new Match(method, variables);
                    }
                    wildcard = new Match(method, variables);
                }
            }
        }
        return wildcard;
    }

    @Override
    @PreDestroy
    public void close() {
        channel.shutdownNow();
        server.shutdownNow();
        try {
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void collect(ServerServiceDefinition definition, List<TranscodedMethod> found) {
        for (MethodDescriptor<?, ?> method : definition.getServiceDescriptor().getMethods()) {
            if (!(method.getSchemaDescriptor() instanceof ProtoMethodDescriptorSupplier supplier)) {
                continue;
            }
            Descriptors.MethodDescriptor protoMethod = supplier.getMethodDescriptor();
            if (!protoMethod.getOptions().hasExtension(AnnotationsProto.http)) {
                continue;
            }
            if (method.getType() != MethodDescriptor.MethodType.UNARY) {
                LOG.warn("Ignoring the google.api.http rule of {}: only unary methods are transcoded", method.getFullMethodName());
                continue;
            }
            MethodDescriptor<DynamicMessage, DynamicMessage> dynamic = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(method.getFullMethodName())
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(protoMethod.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(protoMethod.getOutputType())))
                .build();
            HttpRule rule = protoMethod.getOptions().getExtension(AnnotationsProto.http);
            add(rule, dynamic, protoMethod, found);
            for (HttpRule additional : rule.getAdditionalBindingsList()) {
                add(additional, dynamic, protoMethod, found);
            }
        }
    }

    private static void add(HttpRule rule,
                            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
                            Descriptors.MethodDescriptor protoMethod,
                            List<TranscodedMethod> found) {
        String httpMethod;
        String path;
        switch (rule.getPatternCase()) {
            case GET -> {
                httpMethod = "GET";
                path = rule.getGet();
            }
            case POST -> {
                httpMethod = "POST";
                path = rule.getPost();
            }
            case PUT -> {
                httpMethod = "PUT";
                path = rule.getPut();
            }
            case PATCH -> {
                httpMethod = "PATCH";
                path = rule.getPatch();
            }
            case DELETE -> {
                httpMethod = "DELETE";
                path = rule.getDelete();
            }
            case CUSTOM -> {
                httpMethod = rule.getCustom().getKind().toUpperCase(Locale.ROOT);
                path = rule.getCustom().getPath();
            }
            default -> {
                LOG.warn("Ignoring a google.api.http rule without a pattern on {}", grpcMethod.getFullMethodName());
                return;
            }
        }
        try {
            found.add(new TranscodedMethod(httpMethod, HttpRuleTemplate.parse(path), rule.getBody(), rule.getResponseBody(), grpcMethod, protoMethod));
        } catch (IllegalArgumentException e) {
            LOG.warn("Ignoring the google.api.http rule of {}: {}", grpcMethod.getFullMethodName(), e.getMessage());
        }
    }

    /**
     * A binding that matched a request.
     *
     * @param method The binding
     * @param variables The decoded path variable values by field path
     */
    record Match(TranscodedMethod method, Map<String, String> variables) {
    }
}
