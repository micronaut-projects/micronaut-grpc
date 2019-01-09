/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts Protocol buffer messages to Netty {@link ByteBuf}
 *
 * @author graemerocher
 * @author luistrigueiros
 */
@Singleton
@Requires(classes = {Message.class, ByteBuf.class})
public class ProtoMessageToByteBufConverter implements TypeConverter<Message, ByteBuf> {
    private final ConversionService<?> conversionService;

    /**
     * @param conversionService The conversion service
     */
    public ProtoMessageToByteBufConverter(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<ByteBuf> convert(Message object, Class<ByteBuf> targetType, ConversionContext context) {
        return conversionService.convert(object.toByteArray(), targetType, context);
    }
}
