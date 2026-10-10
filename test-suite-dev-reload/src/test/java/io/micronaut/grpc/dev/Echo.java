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
package io.micronaut.grpc.dev;

import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The methods of a small gRPC service of strings, which the application under test implements without generated
 * stubs. It is on the test classpath, the parent tier, as generated stubs are on an application's runtime classpath.
 */
public final class Echo {

    public static final String SERVICE = "dev.Echo";

    private static final MethodDescriptor.Marshaller<String> STRING = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    /**
     * Answers a string with a string.
     */
    public static final MethodDescriptor<String, String> UNARY = method(MethodDescriptor.MethodType.UNARY, "Unary");

    /**
     * Answers a string with a stream of strings that the service keeps open.
     */
    public static final MethodDescriptor<String, String> STREAM = method(MethodDescriptor.MethodType.SERVER_STREAMING, "Stream");

    private Echo() {
    }

    private static MethodDescriptor<String, String> method(MethodDescriptor.MethodType type, String name) {
        return MethodDescriptor.<String, String>newBuilder()
            .setType(type)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, name))
            .setRequestMarshaller(STRING)
            .setResponseMarshaller(STRING)
            .build();
    }
}
