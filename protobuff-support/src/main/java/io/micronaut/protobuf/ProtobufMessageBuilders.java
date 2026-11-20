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
package io.micronaut.protobuf;

import com.google.protobuf.Message;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.service.SoftServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates Protocol Buffers message builders.
 *
 * <p>Uses the {@link ProtobufMessageFactory} generated at compile time for the message type when one exists. A message
 * compiled without {@code micronaut-protobuff-processor}, for example one from a shared library, falls back to calling
 * its static {@code newBuilder()} method by reflection.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class ProtobufMessageBuilders {

    private static final Logger LOG = LoggerFactory.getLogger(ProtobufMessageBuilders.class);
    private static final Map<Class<?>, Method> REFLECTIVE_FACTORIES = new ConcurrentHashMap<>();

    private ProtobufMessageBuilders() {
    }

    /**
     * Creates a builder for a message type.
     *
     * @param type The message type
     * @return A new builder
     * @throws IllegalArgumentException if the type has no static {@code newBuilder()} method
     */
    @NonNull
    public static Message.Builder newBuilder(@NonNull Class<? extends Message> type) {
        ProtobufMessageFactory<?> factory = Factories.ALL.get(type);
        if (factory != null) {
            return factory.newBuilder();
        }
        return newBuilderReflectively(type);
    }

    /**
     * Whether a builder for the type is created without reflection.
     *
     * @param type The message type
     * @return True if a generated {@link ProtobufMessageFactory} exists for the type
     */
    public static boolean hasGeneratedFactory(@NonNull Class<? extends Message> type) {
        return Factories.ALL.containsKey(type);
    }

    private static Message.Builder newBuilderReflectively(Class<? extends Message> type) {
        Method method = REFLECTIVE_FACTORIES.computeIfAbsent(type, t -> {
            LOG.debug("No generated ProtobufMessageFactory for {}; using reflection. Add micronaut-protobuff-processor to the annotation processor path to avoid it.", t.getName());
            try {
                return t.getMethod("newBuilder");
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Type " + t.getName() + " is not a generated Protocol Buffers message: it has no static newBuilder() method", e);
            }
        });
        try {
            return (Message.Builder) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create a builder for " + type.getName(), e);
        }
    }

    /**
     * Holds the generated factories, loaded on first use.
     */
    private static final class Factories {
        static final Map<Class<?>, ProtobufMessageFactory<?>> ALL = load();

        @SuppressWarnings("rawtypes")
        private static Map<Class<?>, ProtobufMessageFactory<?>> load() {
            Map<Class<?>, ProtobufMessageFactory<?>> factories = new HashMap<>();
            for (ProtobufMessageFactory factory : SoftServiceLoader.load(ProtobufMessageFactory.class).collectAll()) {
                factories.put(factory.getMessageType(), factory);
            }
            return Map.copyOf(factories);
        }
    }
}
