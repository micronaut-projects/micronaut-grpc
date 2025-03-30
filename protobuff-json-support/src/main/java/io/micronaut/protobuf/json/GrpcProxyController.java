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
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.protobuf.json.exception.GrpcInvocationException;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private final ApplicationContext context;

    public GrpcProxyController(
            @NonNull GrpcServiceRegistry registry,
            @NonNull ApplicationContext context,
            @NonNull ProtobufJsonTranscoder transcoder) {
        this.registry = registry;
        this.transcoder = transcoder;
        this.context = context;
        LOG.info("GrpcProxyController initialized and services registered");
    }

    @Post("/{serviceName}/{methodName}")
    public HttpResponse<String> invokeMethod(@PathVariable String serviceName,
                                             @PathVariable String methodName,
                                             @Body String jsonRequest) {
        // Retrieve the executable method explicitly at runtime
        //noinspection unchecked
        ExecutableMethod<Object, Object> executableMethod = (ExecutableMethod<Object, Object>) registry
            .getExecutableMethod(serviceName, methodName)
            .orElseThrow(() -> new MethodNotFoundException("Method `" + serviceName + "." + methodName + "` not found"));

        // Get bean from context
        Object beanInstance = context.getBean(executableMethod.getDeclaringType());

        // Explicitly resolve request and response types at runtime (Protobuf messages)
        @SuppressWarnings("unchecked") Class<? extends Message> requestType =
            (Class<? extends Message>) executableMethod.getArguments()[0].getType();

        // Deserialize JSON explicitly (reflection-driven is fine here)
        Message requestMessage = transcoder.fromJson(jsonRequest, requestType);

        // Reflective invocation using a StreamObserver clearly needed explicitly
        Message responseMessage = invokeGrpcMethodReflectively(beanInstance, executableMethod, requestMessage);

        // Serialize response explicitly to JSON via Google's protobuf reflection mechanisms explicitly
        String jsonResponse = transcoder.toJson(responseMessage);
        return HttpResponse.ok(jsonResponse);
    }

    // Explicit helper to clean up reflective invocation explicitly as isolated method clearly:
    private Message invokeGrpcMethodReflectively(Object beanInstance,
                                                 ExecutableMethod<Object, Object> method,
                                                 Message requestMessage) {
        try {
            // Create a simple, explicitly correct response observer to capture the response
            SingleMessageObserver responseObserver = new SingleMessageObserver();

            // Call explicitly reflectively with exact two required parameters (request + observer)
            method.invoke(beanInstance, requestMessage, responseObserver);

            // Obtain explicitly the response synchronously from grpc response observer explicitly
            return responseObserver.getResponse();
        } catch (GrpcInvocationException e) {
          LOG.error("Problem invoking gRPC method: " + e.getMessage());
          throw e;
        } catch (Exception ex) {
            throw new GrpcInvocationException("Reflective gRPC call failed");
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

    @Error(HttpStatusException.class)
    public HttpResponse<String> handle(ProtobufJsonTranscoder.ProtobufTranscodingException e) {
        return HttpResponse.badRequest();
    }


    // Explicit observer implementation explicitly capturing single response synchronously
    private static class SingleMessageObserver implements StreamObserver<Message> {
        private Message message;
        private Throwable error;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onNext(Message value) {
            this.message = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }

        public Message getResponse() throws InterruptedException {
            boolean timeExceeded = latch.await(5, TimeUnit.SECONDS);
            if (timeExceeded) {
                LOG.warn("Response timeout exceeded for the message response");
            }
            if (error != null) {
                throw new GrpcInvocationException("Error during gRPC call");
            }
            if (message == null) {
                throw new GrpcInvocationException("No response received from " +
                    "gRPC call");
            }
            return message;
        }
    }

}
