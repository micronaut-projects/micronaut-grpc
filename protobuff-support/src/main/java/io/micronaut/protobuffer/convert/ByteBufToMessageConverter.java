package io.micronaut.protobuffer.convert;

import com.google.protobuf.Message;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.codec.ProtobufferBuilderCreator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class ByteBufToMessageConverter implements TypeConverter<ByteBuf, Message> {
    @Override
    public Optional<Message> convert(ByteBuf object, Class<Message> targetType, ConversionContext context) {
        return ProtobufferBuilderCreator
                .getMessageBuilder(targetType)
                .flatMap(builder -> rehydrate(object, builder));
    }

    private Optional<Message> rehydrate(ByteBuf object, Message.Builder builder) {
        try {
            builder.mergeFrom(new ByteBufInputStream(object), ProtobufferBuilderCreator.extensionRegistry);
            return Optional.of(builder.build());
        } catch (IOException e) {
            throw new IllegalStateException("Error parsing: " + e.getMessage());
        }
    }
}
