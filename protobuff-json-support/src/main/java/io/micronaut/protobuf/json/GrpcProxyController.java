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
import com.google.protobuf.util.JsonFormat;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A controller for handling gRPC requests via JSON proxies. This enables invoking gRPC services using
 * HTTP POST requests with JSON payloads. Each request specifies the target gRPC service and method,
 * with the JSON payload converted to the expected gRPC message format.
 *<br/>
 * This controller dynamically discovers and routes requests to registered gRPC service methods.
 * It is assumed the gRPC services are registered with @GrpcRestJsonExposed and managed
 * through the {@code GrpcServiceRegistry}.
 *<br/>
 * The controller accepts requests with the format:
 * POST /{serviceName}/{methodName}, where:
 * - serviceName is the name of the gRPC service
 * - methodName is the specific method to invoke
 *<br/>
 * A JSON body is expected to match the method's input message type, and the response is serialized to JSON.
 *<br/>
 * Graceful error handling and detailed logging are included to support identifying configuration or runtime issues.
 *<br/>
 * Annotations:
 * - {@code @Controller}: Configures the controller path, defaulting to `/grpc-json`.
 * - {@code @Requires}: Ensures the necessary gRPC service registrar component is available.
 */
@Requires(property = "grpc.rest.json.exposed", value = "true")
@Controller("/${micronaut.grpc.proxy.path:`grpc-json`}")
public final class GrpcProxyController {
    private static final Logger LOG = getLogger(GrpcProxyController.class);
    private final JsonFormat.Printer jsonPrinter;
    private final JsonFormat.Parser jsonParser;

    private final GrpcServiceRegistry registry;
    private final GrpcServiceRegistrar registrar;

    /**
     * Constructor for the GrpcProxyController class, responsible for initializing the controller
     * with the specified gRPC service registry and registrar. This controller facilitates the
     * handling of gRPC proxy requests, enabling gRPC service invocation through a JSON-based interface.
     *
     * @param registry An instance of {@code GrpcServiceRegistry} used for managing and retrieving
     *                 gRPC service definitions. Must not be null.
     * @param registrar An instance of {@code GrpcServiceRegistrar} responsible for registering gRPC
     *                  services in the application context. Must not be null.
     */
    public GrpcProxyController(GrpcServiceRegistry registry, GrpcServiceRegistrar registrar) {
        this.registrar = registrar;
        this.jsonPrinter = JsonFormat.printer().includingDefaultValueFields().sortingMapKeys();
        this.jsonParser = JsonFormat.parser();
        this.registry = checkNotNull(registry, "Registry must not be null");
        LOG.info("GrpcProxyController initialized.");
    }

    /**
     * Handles POST requests for invoking gRPC methods through JSON payloads.
     *
     * @param serviceName The name of the gRPC service being invoked. Must not be null or empty.
     * @param methodName  The name of the method within the gRPC service to invoke. Must not be null or empty.
     * @param jsonBody    The JSON formatted request body that will be deserialized into the expected
     *                    input type of the gRPC method. Must not be null or empty.
     * @return HttpResponse containing the JSON-encoded response from the gRPC method or an error message
     *         in JSON format if the operation fails.
     */
    @Post("/{serviceName}/{methodName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<String> handlePost(String serviceName, String methodName, @Body String jsonBody) {
        LOG.debug("Received request for gRPC service: [{}], method: [{}]", serviceName, methodName);
        // Lookup the service
        var serviceDef = registry.getService(serviceName)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        Method method = serviceDef.methods.get(methodName.toLowerCase());
        if (method == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Method not found");
        }

        try {
            // Create a new builder for the request type
            Class<?> requestType = method.getParameterTypes()[0];
            Message.Builder builder = (Message.Builder) requestType.getMethod("newBuilder").invoke(null);

            // Parse JSON into the protobuf message
            jsonParser.merge(jsonBody, builder);
            Message requestMessage = builder.build();

            SimpleStreamObserver<?> observer = new SimpleStreamObserver<>();
            method.invoke(serviceDef.serviceBean, requestMessage, observer);

            Object responseMsg = observer.getResponse();
            // Convert response protobuf message to JSON
            String jsonResponse = jsonPrinter.print((Message) responseMsg);
            return HttpResponse.ok(jsonResponse);
        } catch (Exception e) {
            // Simple manual JSON error response
            String errorJson = String.format("{\"error\":\"%s\"}",
                e.getMessage().replace("\"", "\\\""));  // Basic JSON escaping
            return HttpResponse.serverError(errorJson);
        }
    }


    /**
     * This method runs after all the gRPC services should've been registered
     * in the bean context.  Once this is run, we trigger the
     * @see GrpcServiceRegistrar to register the grpc services.
     */
    @PostConstruct
    public void registerGrpcServices() {
        registrar.registerGrpcServices();
    }

    /**
     * A simple implementation of the StreamObserver interface for handling gRPC stream events.
     * This class captures a single response or an error from a gRPC method invocation and
     * provides access to the response or raises an exception if an error occurred.
     *
     * @param <T> The type of the response object expected from the gRPC method invocation.
     */
    private static class SimpleStreamObserver<T> implements StreamObserver<T> {
        private T response;
        private Throwable error;

        @Override
        public void onNext(T value) {
            response = value;
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            // Nothing needed here
        }

        public T getResponse() {
            if (error != null) {
                throw new RuntimeException(error);
            }
            return response;
        }
    }

}
