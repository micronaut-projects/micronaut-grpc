package io.micronaut.protobuf.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class ObjectMapperFactory {

    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new ProtobufModule());
    }
}
