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
import io.grpc.stub.AbstractBlockingStub;
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
 * The GrpcProxyController is a controller designed to act as a bridge between HTTP-based clients
 * and gRPC services. It offers an interface to invoke gRPC methods using JSON payloads over HTTP,
 * making it easier to integrate gRPC services with systems or clients that may not natively support gRPC.
 *<br/>
 * This controller uses a combination of the gRPC service registry to discover service and method definitions,
 * and a Protobuf JSON transcoder to handle data serialization/deserialization between JSON and Protobuf formats.
 *<br/>
 * It leverages routing to dynamically map HTTP endpoints to gRPC services and provides error handling
 * mechanisms for various scenarios, such as service or method not found, invalid request formats,
 * and internal invocation errors.
 *<br/>
 * The controller is experimental and marked as {@code @Experimental}.
 *<br/>
 * Key responsibilities:
 * - Dynamically route HTTP calls to appropriate gRPC services and methods.
 * - Transcode JSON payloads into Protobuf data models and vice versa.
 * - Provide error handling for common gRPC invocation issues.
 */
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
     * Handles an HTTP POST that maps to a gRPC call.
     *
     * @param serviceName The gRPC service name (for client stubs, a composite key is expected).
     * @param methodName  The name of the method to invoke.
     * @param jsonBody    The JSON payload representing the Protobuf request.
     * @return An HTTP response with the JSON representation of the gRPC response.
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

            if (serviceBean instanceof AbstractBlockingStub) {
                // Handle blocking call
                Object response = method.invoke(serviceBean, requestMessage);
                String jsonResponse = transcoder.toJson((Message) response);
                return HttpResponse.ok(jsonResponse);
            } else {
                // Handle async call with StreamObserver
                SimpleStreamObserver<Message> observer = new SimpleStreamObserver<>();
                method.invoke(serviceBean, requestMessage, observer);
                String jsonResponse = transcoder.toJson(observer.getResponse());
                return HttpResponse.ok(jsonResponse);
            }
        } catch (ProtobufJsonTranscoder.ProtobufTranscodingException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid request format");
        } catch (Exception e) {
            throw new GrpcInvocationException("Failed to process gRPC request", e);
        }
    }

    /**
     * Handles the scenario where a requested gRPC service cannot be found.
     * This method is triggered when a {@link ServiceNotFoundException} is thrown and
     * returns an HTTP 404 response with a message indicating the missing service.
     *
     * @param e The exception containing details about the service that could not be found.
     * @return An HTTP response with a 404 status code and a message describing the missing service.
     */
    @Error(ServiceNotFoundException.class)
    public HttpResponse<String> handleServiceNotFound(ServiceNotFoundException e) {
        return HttpResponse.notFound("Service not found: " + e.getMessage());
    }

    /**
     * Handles the scenario where a specified gRPC method is not found.
     * This method is triggered when a {@link MethodNotFoundException} is thrown
     * and returns an HTTP 404 response with an error message indicating the missing method.
     *
     * @param e The {@link MethodNotFoundException} containing details about the method
     *          that could not be found, including its name.
     * @return An HTTP response with a 404 status code and a message describing
     *         the missing method.
     */
    @Error(MethodNotFoundException.class)
    public HttpResponse<String> handleMethodNotFound(MethodNotFoundException e) {
        return HttpResponse.notFound("Method not found: " + e.getMessage());
    }

    /**
     * Handles a bad request error triggered by an {@link HttpStatusException}.
     * This method captures the exception details and returns an HTTP response
     * with the corresponding status code and error message.
     *
     * @param e The HttpStatusException containing details about the error, including
     *          the HTTP status code and a descriptive message.
     * @return An HTTP response with the status code from the exception and the error message as the body.
     */
    @Error(HttpStatusException.class)
    public HttpResponse<String> handleBadRequest(HttpStatusException e) {
        return HttpResponse.status(e.getStatus()).body(e.getMessage());
    }

    /**
     * Handles errors that occur during the invocation of gRPC methods.
     * This method catches {@link GrpcInvocationException} and returns an HTTP 500
     * response with an error message describing the gRPC invocation failure.
     *
     * @param e The {@link GrpcInvocationException} that triggered the error handling. This
     *          exception provides details about the specific gRPC invocation error.
     * @return An HTTP response with a 500 status code and a message indicating the
     *         gRPC invocation error.
     */
    @Error(GrpcInvocationException.class)
    public HttpResponse<String> handleGrpcError(GrpcInvocationException e) {
        return HttpResponse.serverError("GRPC invocation error: " + e.getMessage());
    }

    /**
     * A simple StreamObserver implementation to capture a gRPC response.
     *
     * @param <T> The type of the response.
     */
    @Experimental
    public static class SimpleStreamObserver<T> implements StreamObserver<T> {
        private T response;
        private Throwable error;

        /**
         * Processes the next value from the stream and stores it as the response.
         *<br/>
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
         * <br/>
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
         * <br/>
         * This method is invoked when the server has successfully completed sending
         * all responses. It is part of the {@code StreamObserver} lifecycle and indicates
         * that no more data will be received.
         * <br/>
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
