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
import io.grpc.stub.StreamObserver;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.protobuf.json.exception.GrpcInvocationException;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistrar;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.lang.reflect.Method;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The GrpcProxyController class provides a mechanism for dynamically handling gRPC requests via HTTP POST
 * endpoints. It enables JSON-based interaction with gRPC services by translating JSON payloads to Protobuf
 * messages and vice versa. This controller relies on a service registry for dynamic lookups and invocation
 * of gRPC service methods.
 *<br/>
 * Annotations:
 * - {@code @Experimental}: Indicates that this feature is experimental and may be subject to change.
 * - {@code @Singleton}: Ensures a single instance of this controller throughout the application's lifecycle.
 * - {@code @Controller}: Defines this class as a Micronaut HTTP controller and maps its routes to the
 *   specified base path, which can be overridden with the `micronaut.grpc.proxy.path` configuration property.
 *<br/>
 * Dependencies:
 * - GrpcServiceRegistry: Manages the available gRPC services and their respective methods for dynamic invocation.
 * - GrpcServiceRegistrar: Responsible for registering available gRPC services at initialization.
 * - ProtobufJsonTranscoder: Handles the conversion between Protobuf and JSON formats for request and response payloads.
 *<br/>
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

        Method method = serviceDef.getMethod(methodName);
        if (method == null) {
            throw new MethodNotFoundException(methodName);
        }

        return invokeGrpcMethod(method, serviceDef.getServiceBean(), jsonBody);
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

    @Error(ServiceNotFoundException.class)
    public HttpResponse<String> handleServiceNotFound(ServiceNotFoundException e) {
        return HttpResponse.notFound("Service not found: " + e.getMessage());
    }

    @Error(MethodNotFoundException.class)
    public HttpResponse<String> handleMethodNotFound(MethodNotFoundException e) {
        return HttpResponse.notFound("Method not found: " + e.getMessage());
    }

    @Error(HttpStatusException.class)
    public HttpResponse<String> handleBadRequest(HttpStatusException e) {
        return HttpResponse.status(e.getStatus()).body(e.getMessage());
    }

    @Error(GrpcInvocationException.class)
    public HttpResponse<String> handleGrpcError(GrpcInvocationException e) {
        return HttpResponse.serverError("GRPC invocation error: " + e.getMessage());
    }

    /**
     * Simple StreamObserver implementation for handling gRPC responses.
     * @param <T> the type of response values this observer handles
     */
    @Experimental
    public static class SimpleStreamObserver<T> implements StreamObserver<T> {
        private T response;
        private Throwable error;

        /**
         * Processes the next value from the stream and stores it as the response.
         *
         * This method is part of the {@code StreamObserver} lifecycle and is called
         * each time a new value is emitted by the gRPC stream. The received value
         * is stored and can be accessed later.
         *
         * @param value the value received from the gRPC stream
         */
        @Override
        public void onNext(T value) {
            response = value;
        }

        /**
         * Handles errors that occur during gRPC calls.
         *
         * This method is invoked when an error is encountered during the execution
         * of a gRPC call. The error is captured and stored for later retrieval or
         * processing.
         *
         * @param t the {@code Throwable} representing the error that occurred
         */
        @Override
        public void onError(Throwable t) {
            error = t;
        }

        /**
         * Notifies that the gRPC call has been completed successfully.
         *
         * This method is invoked when the server has successfully completed sending
         * all responses. It is part of the {@code StreamObserver} lifecycle and indicates
         * that no more data will be received.
         *
         * No action is needed in this implementation.
         */
        @Override
        public void onCompleted() {
            // No action needed
        }

        /**
         * Retrieves the response of a gRPC call if available.
         * If an error occurred during the gRPC call, a {@link GrpcInvocationException}
         * is thrown with the corresponding error details.
         *
         * @return the response of the gRPC call
         * @throws GrpcInvocationException if the gRPC call resulted in an error
         */
        public T getResponse() {
            if (error != null) {
                throw new GrpcInvocationException("gRPC call failed", error);
            }
            return response;
        }
    }

}
