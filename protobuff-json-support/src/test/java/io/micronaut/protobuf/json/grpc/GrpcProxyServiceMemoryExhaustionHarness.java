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
package io.micronaut.protobuf.json.grpc;

import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.protobuf.json.ProtobufJsonTranscoder;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry;

import java.lang.reflect.Proxy;

/**
 * Standalone reproducer for the unbounded server-stream aggregation in {@link GrpcProxyService}.
 *
 * <p>This is deliberately launched by a test in a small child JVM: an {@link OutOfMemoryError}
 * must not affect the Gradle test worker.</p>
 */
public final class GrpcProxyServiceMemoryExhaustionHarness {
    private static final int MESSAGE_SIZE = 32 * 1024;
    private static final int MESSAGE_COUNT = 1_000_000;

    private GrpcProxyServiceMemoryExhaustionHarness() {
    }

    public static void main(String[] args) {
        StreamingBackend backend = new StreamingBackend();
        GrpcServiceRegistry registry = new GrpcServiceRegistry();
        registry.register(StreamingBackend.class, "stream", executableMethod(backend));

        ApplicationContext context = (ApplicationContext) Proxy.newProxyInstance(
            GrpcProxyServiceMemoryExhaustionHarness.class.getClassLoader(),
            new Class<?>[]{ApplicationContext.class},
            (proxy, method, methodArguments) -> {
                if (method.getName().equals("getBean")) {
                    return backend;
                }
                throw new UnsupportedOperationException(method.toString());
            }
        );

        new GrpcProxyService(registry, context, new ProtobufJsonTranscoder())
            .invokeGrpcMethod("StreamingBackend", "stream", "\"attacker\"");
    }

    @SuppressWarnings("unchecked")
    private static ExecutableMethod<StreamingBackend, Object> executableMethod(StreamingBackend backend) {
        return (ExecutableMethod<StreamingBackend, Object>) Proxy.newProxyInstance(
            GrpcProxyServiceMemoryExhaustionHarness.class.getClassLoader(),
            new Class<?>[]{ExecutableMethod.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getDeclaringType" -> StreamingBackend.class;
                case "getName", "getMethodName" -> "stream";
                case "getArguments" -> new Argument<?>[]{
                    Argument.of(StringValue.class),
                    Argument.of(StreamObserver.class)
                };
                case "invoke" -> {
                    Object[] invocationArguments = (Object[]) arguments[1];
                    backend.stream(
                        (StringValue) invocationArguments[0],
                        (StreamObserver<Message>) invocationArguments[1]
                    );
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.toString());
            }
        );
    }

    static final class StreamingBackend {
        void stream(StringValue request, StreamObserver<Message> observer) {
            String payload = "A".repeat(MESSAGE_SIZE);
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                observer.onNext(StringValue.of(payload));
            }
            observer.onCompleted();
        }
    }
}
