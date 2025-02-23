/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.protobuf.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * A factory class responsible for creating and configuring {@code ObjectMapper} instances.
 * The produced {@code ObjectMapper} is pre-configured with essential modules to handle
 * JSON serialization and deserialization, specifically including support for Protobuf serialization
 * through the integration of a {@code ProtobufModule}.
 *<br/>
 * This factory is essential for applications working with JSON and Protobuf, ensuring consistent
 * and reusable configuration for managing serialization needs across the application.
 *<br/>
 * Annotations:
 * - {@code @Factory}: Indicates that this class is a factory responsible for providing managed
 *   bean instances.
 * - {@code @Singleton}: Ensures that the produced {@code ObjectMapper} instance is a singleton
 *   and reused where injected.
 */
@Factory
public class ObjectMapperFactory {

    /**
     * Provides an instance of {@code ObjectMapper} configured with required modules for handling
     * JSON serialization and deserialization, including support for Protobuf serialization.
     *
     * @return An {@code ObjectMapper} instance with a registered {@code ProtobufModule}.
     */
    @Singleton
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new ProtobufModule());
    }
}
