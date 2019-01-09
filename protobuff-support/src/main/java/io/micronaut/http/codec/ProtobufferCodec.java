package io.micronaut.http.codec;

import com.google.protobuf.Message;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
     * Protobuffer encoded data: application/x-www-form-urlencoded.
     */
    public static final MediaType PROTOBUFFER_ENCODED_TYPE = new MediaType(PROTOBUFFER_ENCODED);

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
                throw new IllegalStateException("Not implemented yet!");
            } else {
                builder.mergeFrom(inputStream, ProtobufferBuilderCreator.extensionRegistry);
                return type.getType().cast(builder.build());
            }
        } catch (Exception e) {
            throw new CodecException("Error decoding Protobuff stream for type [" + type.getName() + "]: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<Message.Builder> getBuilder(Argument<T> type) {
        Class<? extends Message> clazz = (Class<? extends Message>) type.getType();
        return ProtobufferBuilderCreator.getMessageBuilder(clazz);
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
        }
        return new byte[0];
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        return allocator.copiedBuffer(encode(object));
    }
}
