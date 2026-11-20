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
package io.micronaut.protobuf.processor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Generates an {@code io.micronaut.protobuf.ProtobufMessageFactory} for every Protocol Buffers message class that
 * {@code protoc} generates, so that message builders are created without reflection.
 *
 * <p>Generated message classes are annotated with {@code @com.google.protobuf.Generated}. For each one that implements
 * {@code com.google.protobuf.Message}, the visitor writes a small factory class that calls the message's static
 * {@code newBuilder()} method, and registers it as a service. Messages in the default package are skipped and keep the
 * reflective fallback.</p>
 *
 * <p>{@code protoc} only annotates the classes it writes to their own file. With the default
 * {@code java_multiple_files = false} layout the messages are nested in an outer wrapper class, and only the wrapper
 * carries the annotation, so incremental processing selects just the wrapper. Every visited class is therefore
 * traversed for nested message classes as well.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class ProtobufMessageFactoryVisitor implements TypeElementVisitor<Object, Object> {

    static final String GENERATED_ANNOTATION = "com.google.protobuf.Generated";
    static final String MESSAGE_TYPE = "com.google.protobuf.Message";
    static final String FACTORY_TYPE = "io.micronaut.protobuf.ProtobufMessageFactory";
    static final String FACTORY_SUFFIX = "$ProtobufMessageFactory";

    private final Set<String> visitedClasses = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(GENERATED_ANNOTATION);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!visitedClasses.add(element.getName())) {
            // already handled while traversing its enclosing class, or visited directly before
            return;
        }
        if (isGeneratedMessage(element, context)) {
            writeFactory(element, context);
        }
        for (ClassElement nested : element.getEnclosedElements(ElementQuery.of(ClassElement.class))) {
            visitClass(nested, context);
        }
    }

    private void writeFactory(ClassElement element, VisitorContext context) {
        String packageName = element.getPackageName();
        if (packageName.isEmpty()) {
            // Generated sources need a package; messages in the default package keep the reflective fallback.
            return;
        }
        String binaryName = element.getName();
        String factorySimpleName = factorySimpleName(binaryName, packageName);
        String factoryName = packageName + '.' + factorySimpleName;
        // protoc never uses '$' in class names, so the source name is the binary name with '.' for nesting
        String messageSourceName = binaryName.replace('$', '.');

        Optional<GeneratedFile> file = context.visitGeneratedSourceFile(packageName, factorySimpleName, element);
        if (file.isEmpty()) {
            return;
        }
        try (Writer writer = file.get().openWriter()) {
            writer.write(source(packageName, factorySimpleName, messageSourceName));
        } catch (IOException e) {
            context.fail("Unable to generate " + factoryName + ": " + e.getMessage(), element);
            return;
        }
        context.visitServiceDescriptor(FACTORY_TYPE, factoryName, element);
    }

    /**
     * Computes the simple name of the factory generated for a message. The nested part of the binary name is encoded so
     * that distinct nestings never map to the same name: {@code _} becomes {@code _1}, then {@code $} becomes {@code _}.
     * It must stay in sync with {@code ProtobufMessageBuilders}.
     *
     * @param binaryName The binary name of the message class
     * @param packageName The package of the message class
     * @return The simple name of the factory class
     */
    static String factorySimpleName(String binaryName, String packageName) {
        String nestedName = packageName.isEmpty() ? binaryName : binaryName.substring(packageName.length() + 1);
        return nestedName.replace("_", "_1").replace('$', '_') + FACTORY_SUFFIX;
    }

    private static boolean isGeneratedMessage(ClassElement element, VisitorContext context) {
        if (element.isAbstract() || element.isInterface() || !element.isPublic() || !element.isAssignable(MESSAGE_TYPE)) {
            return false;
        }
        if (context.getClassElement(FACTORY_TYPE).isEmpty()) {
            // micronaut-protobuff-support is not on the compile classpath, so the factory interface can't be implemented
            return false;
        }
        return element.getEnclosedElement(ElementQuery.ALL_METHODS
                .onlyStatic()
                .named("newBuilder")
                .filter(m -> m.getParameters().length == 0 && m.isPublic()))
            .isPresent();
    }

    private static String source(String packageName, String factorySimpleName, String messageSourceName) {
        return "package " + packageName + ";\n\n"
            + "/**\n"
            + " * Creates builders for {@link " + messageSourceName + "} without reflection.\n"
            + " * Generated by micronaut-protobuff-processor.\n"
            + " */\n"
            + "@io.micronaut.core.annotation.Generated\n"
            + "public final class " + factorySimpleName
            + " implements " + FACTORY_TYPE + '<' + messageSourceName + "> {\n\n"
            + "    @Override\n"
            + "    public java.lang.Class<" + messageSourceName + "> getMessageType() {\n"
            + "        return " + messageSourceName + ".class;\n"
            + "    }\n\n"
            + "    @Override\n"
            + "    public com.google.protobuf.Message.Builder newBuilder() {\n"
            + "        return " + messageSourceName + ".newBuilder();\n"
            + "    }\n"
            + "}\n";
    }
}
