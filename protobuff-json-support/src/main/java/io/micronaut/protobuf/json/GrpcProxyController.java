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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.lang.reflect.Method;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The GrpcProxyController class provides a mechanism for dynamically handling gRPC requests via HTTP POST
 * endpoints. It enables JSON-based interaction with gRPC services by translating JSON payloads to Protobuf
 * messages and vice versa. This controller relies on a service registry for dynamic lookups and invocation
 * of gRPC service methods.
 *
 * Annotations:
 * - {@code @Experimental}: Indicates that this feature is experimental and may be subject to change.
 * - {@code @Singleton}: Ensures a single instance of this controller throughout the application's lifecycle.
 * - {@code @Controller}: Defines this class as a Micronaut HTTP controller and maps its routes to the
 *   specified base path, which can be overridden with the `micronaut.grpc.proxy.path` configuration property.
 *
 * Dependencies:
 * - GrpcServiceRegistry: Manages the available gRPC services and their respective methods for dynamic invocation.
 * - GrpcServiceRegistrar: Responsible for registering available gRPC services at initialization.
 * - ProtobufJsonTranscoder: Handles the conversion between Protobuf and JSON formats for request and response payloads.
 *
 * Responsibilities:
 * - Provides an HTTP POST endpoint mapped to gRPC services and methods.
 **/
@Experimental
@Singleton
@Controller("/${micronaut.grpc.proxy.path:`grpc-json`}")
public final class GrpcProxyController {
    private static final Logger LOG = getLogger(GrpcProxyController.class);

    private final GrpcServiceRegistry registry;
    private final ProtobufJsonTranscoder transcoder;

    /**
     * Constructs a new instance of GrpcProxyController, initializing its dependencies and registering gRPC services.
     *
     * @param registry The {@link GrpcServiceRegistry} instance used to manage gRPC service definitions.
     *                 This allows retrieval of service and method definitions for dynamic invocation.
     * @param registrar The {@link GrpcServiceRegistrar} responsible for registering gRPC services with this controller
     *                  on initialization.
     * @param transcoder The {@link ProtobufJsonTranscoder} used for converting between Protobuf messages and JSON
     *                   representations, enabling JSON-based gRPC service interaction.
     */
    public GrpcProxyController(
            @NonNull GrpcServiceRegistry registry,
            @NonNull GrpcServiceRegistrar registrar,
            @NonNull ProtobufJsonTranscoder transcoder) {
        this.registry = registry;
        this.transcoder = transcoder;

        registrar.registerGrpcServices();
        LOG.info("GrpcProxyController initialized and services registered");
    }

    /**
     * Handles a gRPC POST request by invoking the specified service and method, using the provided JSON body as the request message.
     * Converts the incoming JSON payload into a Protobuf message, dynamically invokes the corresponding gRPC method,
     * and then converts the response back to JSON for the HTTP response.
     *
     * @param serviceName The name of the gRPC service to invoke. Must not be null.
     * @param methodName The name of the method within the specified service to invoke. Must not be null.
     * @param jsonBody The JSON-encoded payload to be used as the method's input. Must not be null.
     * @return An {@link HttpResponse} containing the JSON-encoded result of the gRPC method invocation.
     *         If the service or method cannot be found, or if there is an issue with the payload, an appropriate HTTP error is returned.
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
            throw new MethodNotFoundException(methodName);
        }

        return invokeGrpcMethod(method, serviceDef.serviceBean, jsonBody);
    }

    private HttpResponse<String> invokeGrpcMethod(
            @NonNull Method method,
            @NonNull Object serviceBean,
            @NonNull String jsonBody) {
        try {
            Class<?> requestType = method.getParameterTypes()[0];
            @SuppressWarnings("unchecked")
            Message requestMessage = transcoder.fromJson(jsonBody, (Class<? extends Message>) requestType);
            SimpleStreamObserver<Message> observer = new SimpleStreamObserver<>();
            method.invoke(serviceBean, requestMessage, observer);
            String jsonResponse = transcoder.toJson(observer.getResponse());
            return HttpResponse.ok(jsonResponse);
        } catch (ProtobufJsonTranscoder.ProtobufTranscodingException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid request format");
        } catch (Exception e) {
            throw new GrpcInvocationException("Failed to process gRPC request", e);
        }
    }
}
