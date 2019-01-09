package io.micronaut.protobuffer.convert;

import com.google.protobuf.Message;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class MessageToByteBufConverter implements TypeConverter<Message, ByteBuf> {
    private final ConversionService<?> conversionService;

    /**
     * @param conversionService The conversion service
     */
    public MessageToByteBufConverter(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<ByteBuf> convert(Message object, Class<ByteBuf> targetType, ConversionContext context) {
        return conversionService.convert(object.toByteArray(), targetType, context);
    }
}
