/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.protobuf.codec;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol buffers codec.
 *
 * @author graemerocher
 * @author luistrigueiros
 */
@Singleton
public class ProtobufferCodec implements MediaTypeCodec {
    /**
     * Protobuffer encoded data: application/x-protobuf.
     */
    public static final String PROTOBUFFER_ENCODED = "application/x-protobuf";
    /**
     * This Header is to say the fully qualified name of the message builder to use.
     * This is needed when the request is untyped
     */
    public static final String X_PROTOBUF_MESSAGE_HEADER = "X-Protobuf-Message";
    /**
     * Protobuffer encoded data: application/x-protobuf.
     */
    public static final MediaType PROTOBUFFER_ENCODED_TYPE = new MediaType(PROTOBUFFER_ENCODED);

    private final ConcurrentHashMap<Class<?>, Method> methodCache = new ConcurrentHashMap<>();

    private final ExtensionRegistry extensionRegistry;

    /**
     * Default constructor.
     * @param extensionRegistry The extension registry
     */
    public ProtobufferCodec(ExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return Message.class.isAssignableFrom(type);
    }

    @Override
    public Collection<MediaType> getMediaTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(PROTOBUFFER_ENCODED_TYPE);
        return mediaTypes;
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try {
            Message.Builder builder = getBuilder(type)
                    .orElseThrow(() -> new CodecException("Unable to create builder"));
            if (type.hasTypeVariables()) {
                throw new IllegalStateException("Generic type arguments are not supported");
            } else {
                builder.mergeFrom(inputStream, extensionRegistry);
                return type.getType().cast(builder.build());
            }
        } catch (Exception e) {
            throw new CodecException("Error decoding Protobuff stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if (type.getType() == byte[].class) {
                return (T) buffer.toByteArray();
            } else {
                Message.Builder builder = getBuilder(type)
                        .orElseThrow(() -> new CodecException("Unable to create builder"));
                if (type.hasTypeVariables()) {
                    throw new IllegalStateException("Generic type arguments are not supported");
                } else {
                    builder.mergeFrom(buffer.toByteArray(), extensionRegistry);
                    return type.getType().cast(builder.build());
                }
            }
        } catch (Exception e) {
            throw new CodecException("Error decoding Protobuff bytes for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, byte[] bytes) throws CodecException {
        try {
            if (type.getType() == byte[].class) {
                return (T) bytes;
            } else {
                Message.Builder builder = getBuilder(type)
                        .orElseThrow(() -> new CodecException("Unable to create builder"));
                if (type.hasTypeVariables()) {
                    throw new IllegalStateException("Generic type arguments are not supported");
                } else {
                    builder.mergeFrom(bytes, extensionRegistry);
                    return type.getType().cast(builder.build());
                }
            }
        } catch (Exception e) {
            throw new CodecException("Error decoding Protobuff bytes for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<Message.Builder> getBuilder(Argument<T> type) {
        Class<? extends Message> clazz = (Class<? extends Message>) type.getType();
        return getMessageBuilder(clazz);
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            if (object instanceof Message) {
                ((Message) object).writeTo(outputStream);
            }
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to OutputStream:" + e.getMessage());
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        if (object instanceof Message) {
            return ((Message) object).toByteArray();
        } else if (object instanceof byte[]) {
            return ((byte[]) object);
        }
        return new byte[0];
    }

    @Override
    public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
        return allocator.copiedBuffer(encode(object));
    }

    /**
     * @return The extension registry
     */
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    /**
     * Create a new {@code Message.Builder} instance for the given class.
     * <p>This method uses a ConcurrentHashMap for caching method lookups.
     *
     * @param clazz The class.
     * @return The message builder
     */
    public Optional<Message.Builder> getMessageBuilder(Class<? extends Message> clazz) {
        try {
            return createBuilder(clazz);
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    private Optional<Message.Builder> createBuilder(Class<? extends Message> clazz) throws Exception {
        return Optional.of ((Message.Builder) getMethod(clazz).invoke(clazz));
    }

    private Method getMethod(Class<? extends Message> clazz) throws NoSuchMethodException {
        Method method = methodCache.get(clazz);
        if (method == null) {
            method = clazz.getMethod("newBuilder");
            methodCache.put(clazz, method);
        }
        return method;
    }
}
