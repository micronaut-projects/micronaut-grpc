package io.micronaut.http.codec;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ProtobufferBuilderCreator {

    public final static ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();

    private static final ConcurrentHashMap<Class<?>, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * Create a new {@code Message.Builder} instance for the given class.
     * <p>This method uses a ConcurrentHashMap for caching method lookups.
     */
    public static Optional<Message.Builder> getMessageBuilder(Class<? extends Message> clazz) {
        try {
            return createBuilder(clazz);
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    private static Optional<Message.Builder> createBuilder(Class<? extends Message> clazz) throws Exception {
        return Optional.of ((Message.Builder) getMethod(clazz).invoke(clazz));
    }

    private static Method getMethod(Class<? extends Message> clazz) throws NoSuchMethodException {
        Method method = methodCache.get(clazz);
        if (method == null) {
            method = clazz.getMethod("newBuilder");
            methodCache.put(clazz, method);
        }
        return method;
    }

}
