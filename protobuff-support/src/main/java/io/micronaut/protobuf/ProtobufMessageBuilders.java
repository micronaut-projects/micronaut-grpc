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
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Creates Protocol Buffers message builders.
 *
 * <p>Uses the {@link ProtobufMessageFactory} generated at compile time by {@code micronaut-protobuff-processor} for the
 * message type. Factories are looked up through the message type's own class loader, and the result is cached per
 * message class, so nothing keeps a class loader alive after its classes are unloaded.</p>
 *
 * <p>For compatibility only, a message compiled without the processor, for example one from a shared library, falls
 * back to calling its static {@code newBuilder()} method by reflection.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class ProtobufMessageBuilders {

    /**
     * The suffix of generated factory class names.
     */
    static final String FACTORY_SUFFIX = "$ProtobufMessageFactory";

    private static final Logger LOG = LoggerFactory.getLogger(ProtobufMessageBuilders.class);

    private static final ClassValue<Optional<ProtobufMessageFactory<?>>> FACTORIES = new ClassValue<>() {
        @Override
        protected Optional<ProtobufMessageFactory<?>> computeValue(Class<?> type) {
            return findFactory(type);
        }
    };

    private static final ClassValue<Method> REFLECTIVE_FACTORIES = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            LOG.debug("No generated ProtobufMessageFactory for {}; using reflection. Add micronaut-protobuff-processor to the annotation processor path to avoid it.", type.getName());
            try {
                return type.getMethod("newBuilder");
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Type " + type.getName() + " is not a generated Protocol Buffers message: it has no static newBuilder() method", e);
            }
        }
    };

    private ProtobufMessageBuilders() {
    }

    /**
     * Creates a builder for a message type.
     *
     * @param type The message type
     * @return A new builder
     * @throws IllegalArgumentException if the type has no static {@code newBuilder()} method
     */
    public static Message.Builder newBuilder(Class<? extends Message> type) {
        Optional<ProtobufMessageFactory<?>> factory = FACTORIES.get(type);
        if (factory.isPresent()) {
            return factory.get().newBuilder();
        }
        return newBuilderReflectively(type);
    }

    /**
     * Whether a builder for the type is created without reflection.
     *
     * @param type The message type
     * @return True if a generated {@link ProtobufMessageFactory} exists for the type
     */
    public static boolean hasGeneratedFactory(Class<? extends Message> type) {
        return FACTORIES.get(type).isPresent();
    }

    /**
     * Computes the name of the factory class generated for a message class. The nested part of the binary name is encoded
     * so that distinct nestings never map to the same name: {@code _} becomes {@code _1}, then {@code $} becomes
     * {@code _}. It must stay in sync with {@code ProtobufMessageFactoryVisitor}.
     *
     * @param binaryName The binary name of the message class
     * @param packageName The package of the message class
     * @return The binary name of the generated factory class
     */
    static String factoryName(String binaryName, String packageName) {
        String nestedName = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1);
        String simpleName = nestedName.replace("_", "_1").replace('$', '_') + FACTORY_SUFFIX;
        return packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;
    }

    @SuppressWarnings("rawtypes")
    private static Optional<ProtobufMessageFactory<?>> findFactory(Class<?> type) {
        String expectedName = factoryName(type.getName(), type.getPackageName());
        @Nullable ClassLoader classLoader = type.getClassLoader();
        SoftServiceLoader<ProtobufMessageFactory> loader = SoftServiceLoader.load(ProtobufMessageFactory.class, classLoader, expectedName::equals);
        for (ServiceDefinition<ProtobufMessageFactory> definition : loader) {
            if (definition.isPresent()) {
                ProtobufMessageFactory<?> factory = definition.load();
                if (factory.getMessageType() == type) {
                    return Optional.of(factory);
                }
            }
        }
        return Optional.empty();
    }

    private static Message.Builder newBuilderReflectively(Class<? extends Message> type) {
        Method method = REFLECTIVE_FACTORIES.get(type);
        try {
            return (Message.Builder) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create a builder for " + type.getName(), e);
        }
    }
}
