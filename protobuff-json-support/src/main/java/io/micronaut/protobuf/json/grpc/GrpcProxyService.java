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
package io.micronaut.protobuf.json.grpc;

import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.protobuf.json.ProtobufJsonTranscoder;
import io.micronaut.protobuf.json.exception.GrpcInvocationException;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ProtobufTranscodingException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Added imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A service responsible for enabling interaction with gRPC methods through a JSON-based interface.
 * This class facilitates the invocation of gRPC methods by handling tasks such as locating the appropriate
 * service and method, converting JSON request payloads into Protobuf messages, executing the gRPC call reflectively,
 * and then transforming the Protobuf response(s) back into a JSON format for the consumer.
 * It supports unary and server-streaming responses. For streaming responses, it returns a JSON array.
 * <br/>
 * Responsibilities:
 * - Resolves the gRPC service and method metadata through the {@link GrpcServiceRegistry}.
 * - Manages the lifecycle of gRPC service beans within the application, utilizing the {@link ApplicationContext}.
 * - Coordinates JSON-to-Protobuf and Protobuf-to-JSON transformations via the {@link ProtobufJsonTranscoder}.
 * <br/>
 * This service is primarily designed to make it easier to expose gRPC methods for external clients
 * and bridge the gap between JSON REST interfaces and Protobuf-based gRPC method implementations.
 * <br/>
 * Thread Safety:
 * - Instances of this class are effectively singletons, managed by the application framework.
 * - While the class itself does not explicitly manage threading, it relies on thread-safe components
 * like {@link GrpcServiceRegistry} for service and method lookups. Asynchronous gRPC calls use internal
 * gRPC threading and synchronization primitives (like CountDownLatch) are used here.
 * <br/>
 * Exceptions:
 * - {@link MethodNotFoundException}: Thrown when a specific method cannot be found in the given service.
 * - {@link ServiceNotFoundException}: Thrown when a service is not registered or managed in the application context.
 * - {@link ProtobufTranscodingException}: Thrown for errors during JSON-Protobuf serialization or deserialization.
 * - {@link GrpcInvocationException}: Thrown when gRPC method invocation fails, or a timeout occurs.
 */
@Singleton
public class GrpcProxyService {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProxyService.class);
    // Consider making the timeout configurable
    private static final long GRPC_CALL_TIMEOUT_SECONDS = 10;

    private final GrpcServiceRegistry registry;
    private final ApplicationContext context;
    private final ProtobufJsonTranscoder transcoder;

    public GrpcProxyService(GrpcServiceRegistry registry,
                            ApplicationContext context,
                            ProtobufJsonTranscoder transcoder) {
        this.registry = registry;
        this.context = context;
        this.transcoder = transcoder;
    }

    /**
     * Invokes a specified gRPC method on a service and converts the Protobuf response(s) to a JSON string.
     * This method handles locating the gRPC method, converting the JSON request to a Protobuf message,
     * invoking the method reflectively, and transforming the Protobuf response(s) back to JSON format.
     * If the gRPC method returns a single message (unary), a single JSON object string is returned.
     * If the gRPC method returns multiple messages (server streaming), a JSON array string is returned,
     * where each element is the JSON representation of a message.
     *
     * @param serviceName The name of the gRPC service containing the method. Must not be null or empty.
     * @param methodName The name of the gRPC method to invoke within the service. Must not be null or empty.
     * @param jsonRequest A JSON string representing the request payload. Must not be null or empty.
     * @return A JSON string representation of the Protobuf response(s). For unary calls, it's a JSON object.
     * For streaming calls, it's a JSON array of objects. Never null (returns "[]" for empty streams).
     * @throws MethodNotFoundException If the specified method cannot be found in the gRPC service.
     * @throws ServiceNotFoundException If the specified service cannot be located in the context.
     * @throws ProtobufTranscodingException If there is an error in converting JSON to or from Protobuf.
     * @throws GrpcInvocationException If the invocation of the gRPC method fails or times out.
     */
    public String invokeGrpcMethod(String serviceName, String methodName, String jsonRequest) {
        // Obtain the executable method
        ExecutableMethod<?, ?> grpcMethod = registry.getExecutableMethod(serviceName, methodName)
            .orElseThrow(() -> {
                LOG.warn("Method '{}' not found in service '{}'", methodName, serviceName);
                return new MethodNotFoundException(methodName);
            });

        // Retrieve the bean instance from context
        Object beanInstance;
        try {
            beanInstance = context.getBean(grpcMethod.getDeclaringType());
        } catch (Exception e) {
            // Catching generic Exception might be too broad, consider specific Micronaut exceptions if applicable
            LOG.warn("Service '{}' not found via context for type {}", serviceName, grpcMethod.getDeclaringType().getName(), e);
            throw new ServiceNotFoundException(serviceName);
        }

        // Transform JSON to Protobuf request
        // Assuming the request is always the first argument and is a Message
        @SuppressWarnings("unchecked") Class<? extends Message> requestType = (Class<? extends Message>) grpcMethod.getArguments()[0].getType();
        Message requestMessage = transcoder.fromJson(jsonRequest, requestType);

        // Invoke method and get potentially multiple Protobuf responses
        final List<Message> responseMessages;
        try {
            //noinspection unchecked
            responseMessages = invokeGrpcMethodReflectively(beanInstance, (ExecutableMethod<Object, Object>) grpcMethod, requestMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Re-interrupt the thread
            throw new GrpcInvocationException("gRPC method invocation was interrupted: " + e.getMessage(), e);
        }

        // Transform Protobuf responses to JSON
        if (responseMessages.isEmpty()) {
            // Handle cases where the stream completes without sending messages
            // Or potentially unary methods that return void/Empty (though Protobuf usually uses google.protobuf.Empty)
            // Returning an empty JSON array is consistent for streams.
            // For unary expecting a response, this might indicate an issue, but we return "[]" for now.
            return "[]";
        } else if (responseMessages.size() == 1) {
            // If only one message was received, return it as a single JSON object
            // This preserves behavior for standard unary calls.
            return transcoder.toJson(responseMessages.get(0));
        } else {
            // If multiple messages were received (streaming), format as a JSON array
            List<String> jsonResponses = responseMessages.stream()
                .map(transcoder::toJson)
                .collect(Collectors.toList());
            // Construct the JSON array string
            return "[" + String.join(",", jsonResponses) + "]";
        }
    }

    /**
     * Invokes the gRPC method reflectively and collects all response messages.
     * Uses a StreamObserver to capture messages from unary or server-streaming calls.
     *
     * @param beanInstance The instance of the gRPC service bean.
     * @param method       The executable method representing the gRPC operation.
     * @param request      The Protobuf request message.
     * @return A List containing all Protobuf response messages received. Empty if none were received before completion/error.
     * @throws InterruptedException    If the waiting thread is interrupted.
     * @throws GrpcInvocationException If the call times out or an error occurs during execution.
     */
    private List<Message> invokeGrpcMethodReflectively(Object beanInstance,
                                                       ExecutableMethod<Object, Object> method,
                                                       Message request) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        // Use a list to hold potentially multiple response messages
        // ArrayList is sufficient here as it's only modified by the gRPC thread
        // before the latch countdown, and read by the calling thread after await().
        final List<Message> responseHolder = Collections.synchronizedList(new ArrayList<>()); // Use synchronized list for safety
        final Throwable[] errorHolder = new Throwable[1];

        // Assuming the method signature is (RequestType, StreamObserver<ResponseType>)
        // The second argument should be the StreamObserver
        method.invoke(beanInstance, request, new StreamObserver<Message>() {

            @Override
            public void onNext(Message message) {
                // Add the message to the list instead of overwriting
                responseHolder.add(message);
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("gRPC method invocation resulted in error for method {} on bean {}", method.getName(), beanInstance.getClass().getSimpleName(), t);
                errorHolder[0] = t;
                latch.countDown(); // Signal completion (with error)
            }

            @Override
            public void onCompleted() {
                LOG.debug("gRPC method invocation completed successfully for method {} on bean {}", method.getName(), beanInstance.getClass().getSimpleName());
                latch.countDown(); // Signal normal completion
            }
        });

        // Wait for the gRPC call to complete (or timeout)
        if (!latch.await(GRPC_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOG.warn("Timeout waiting for gRPC method {} on bean {}", method.getName(), beanInstance.getClass().getSimpleName());
            // Clean up potentially held resources if the observer holds references? (Depends on impl)
            // Consider cancelling the call if possible, though tricky with reflection here.
            throw new GrpcInvocationException("Timeout (" + GRPC_CALL_TIMEOUT_SECONDS + "s) waiting for gRPC method response.");
        }

        // Check if an error occurred during the gRPC call
        if (errorHolder[0] != null) {
            throw new GrpcInvocationException("Error executing gRPC method: " + errorHolder[0].getMessage(), errorHolder[0]);
        }

        // Return the list of collected messages
        return responseHolder;
    }
}
