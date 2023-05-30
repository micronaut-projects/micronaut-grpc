/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.protobuf.handler;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.protobuf.codec.ProtobufferCodec;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Message body handler for protobuf {@link Message}s.
 *
 * @param <T> The body type
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Singleton
@Produces({ProtobufferCodec.PROTOBUFFER_ENCODED, ProtobufferCodec.PROTOBUFFER_ENCODED2})
@Consumes({ProtobufferCodec.PROTOBUFFER_ENCODED, ProtobufferCodec.PROTOBUFFER_ENCODED2})
public final class ProtobufBodyHandler<T extends Message> implements MessageBodyHandler<T> {
    private final ProtobufferCodec codec;
    private final ExtensionRegistry extensionRegistry;

    public ProtobufBodyHandler(ProtobufferCodec codec, ExtensionRegistry extensionRegistry) {
        this.codec = codec;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public T read(Argument<T> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        Message.Builder builder = getBuilder(type)
            .orElseThrow(() -> new CodecException("Unable to create builder"));
        if (type.hasTypeVariables()) {
            throw new IllegalStateException("Generic type arguments are not supported");
        } else {
            try {
                builder.mergeFrom(inputStream, extensionRegistry);
            } catch (IOException e) {
                throw new CodecException("Failed to read protobuf", e);
            }
            return type.getType().cast(builder.build());
        }
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : ProtobufferCodec.PROTOBUFFER_ENCODED_TYPE);
        try {
            object.writeTo(outputStream);
        } catch (IOException e) {
            throw new CodecException("Failed to write protobuf", e);
        }
    }

    private Optional<Message.Builder> getBuilder(Argument<T> type) {
        return codec.getMessageBuilder(type.getType());
    }
}
