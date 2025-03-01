/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.lang.reflect.Method;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * A controller for handling gRPC requests via JSON proxies.
 */
@Experimental
@Singleton
@Controller("/${micronaut.grpc.proxy.path:`grpc-json`}")
public final class GrpcProxyController {
    private static final Logger LOG = getLogger(GrpcProxyController.class);

    private final GrpcServiceRegistry registry;
    private final ProtobufJsonTranscoder transcoder;
    private final GrpcProxyErrorHandler errorHandler;

    /**
     * Creates a new gRPC proxy controller.
     *
     * @param registry The gRPC service registry
     * @param registrar The gRPC service registrar
     * @param transcoder The Protobuf JSON transcoder
     * @param errorHandler The error response handler
     */
    public GrpcProxyController(
            @NonNull GrpcServiceRegistry registry,
            @NonNull GrpcServiceRegistrar registrar,
            @NonNull ProtobufJsonTranscoder transcoder,
            @NonNull GrpcProxyErrorHandler errorHandler) {
        this.registry = registry;
        this.transcoder = transcoder;
        this.errorHandler = errorHandler;

        registrar.registerGrpcServices();
        LOG.info("GrpcProxyController initialized and services registered");
    }

    /**
     * Handles POST requests for invoking gRPC methods through JSON payloads.
     *
     * @param serviceName The gRPC service name
     * @param methodName The gRPC method name
     * @param jsonBody The JSON request body
     * @return HTTP response with JSON payload
     */
    @Post("/{serviceName}/{methodName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> handlePost(
            @NonNull String serviceName,
            @NonNull String methodName,
            @Body @NonNull String jsonBody) {
        LOG.debug("Handling gRPC request - service: [{}], method: [{}]", serviceName, methodName);

        var serviceDef = registry.getService(serviceName)
                .orElseThrow(() -> new ServiceNotFoundException(serviceName));

        Method method = serviceDef.methods.get(methodName.toLowerCase());
        if (method == null) {
            return errorHandler.createNotFoundResponse("Method", methodName);
        }
        try {
            return invokeGrpcMethod(method, serviceDef.serviceBean, jsonBody);
        } catch (ProtobufJsonTranscoder.ProtobufTranscodingException e) {
            return errorHandler.createBadRequestResponse("Invalid request format", e);
        } catch (Exception e) {
            return errorHandler.createServerErrorResponse("Failed to process gRPC request", e);
        }
    }

    private HttpResponse<String> invokeGrpcMethod(
            @NonNull Method method,
            @NonNull Object serviceBean,
            @NonNull String jsonBody) {
        Class<?> requestType = method.getParameterTypes()[0];
        @SuppressWarnings("unchecked")
        Message requestMessage = transcoder.fromJson(jsonBody, (Class<? extends Message>) requestType);

        SimpleStreamObserver<Message> observer = new SimpleStreamObserver<>();
        try {
            method.invoke(serviceBean, requestMessage, observer);
            String jsonResponse = transcoder.toJson(observer.getResponse());
            return HttpResponse.ok(jsonResponse);
        } catch (Exception e) {
            throw new GrpcInvocationException("Failed to invoke gRPC method", e);
        }
    }
}
