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
package io.micronaut.protobuf.convert;

import com.google.protobuf.Message;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.protobuf.codec.ProtobufferCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Converts Protocol buffer messages from Netty {@link ByteBuf}.
 *
 * @author graemerocher
 * @author luistrigueiros
 */
@Singleton
@Requires(classes = {Message.class, ByteBuf.class})
public class ByteBufToProtoMessageConverter implements TypeConverter<ByteBuf, Message> {

    private final ProtobufferCodec codec;

    /**
     * Default constructor.
     * @param codec The codec
     */
    public ByteBufToProtoMessageConverter(ProtobufferCodec codec) {
        this.codec = codec;
    }

    @Override
    public Optional<Message> convert(ByteBuf object, Class<Message> targetType, ConversionContext context) {
        return codec
                .getMessageBuilder(targetType)
                .flatMap(builder -> rehydrate(object, builder));
    }

    private Optional<Message> rehydrate(ByteBuf object, Message.Builder builder) {
        try {
            builder.mergeFrom(new ByteBufInputStream(object), codec.getExtensionRegistry());
            return Optional.of(builder.build());
        } catch (IOException e) {
            throw new IllegalStateException("Error parsing: " + e.getMessage());
        }
    }
}
