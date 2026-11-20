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
package io.micronaut.grpc.processor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Generates a bean factory for the client stubs of every gRPC service class that {@code protoc-gen-grpc-java}
 * generates, so that stubs can be injected with {@code @GrpcChannel} without a hand-written factory.
 *
 * <p>Generated service classes are annotated with {@code @io.grpc.stub.annotations.GrpcGenerated}. For each public
 * static {@code newXxxStub(io.grpc.Channel)} method that returns a stub, the generated factory has a prototype bean
 * method that obtains the channel of the injection point from {@code GrpcManagedChannelFactory} and calls it.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class GrpcStubFactoryVisitor implements TypeElementVisitor<Object, Object> {

    static final String GRPC_GENERATED = "io.grpc.stub.annotations.GrpcGenerated";
    static final String CHANNEL_TYPE = "io.grpc.Channel";
    static final String ABSTRACT_STUB_TYPE = "io.grpc.stub.AbstractStub";
    static final String CHANNEL_FACTORY_TYPE = "io.micronaut.grpc.channels.GrpcManagedChannelFactory";
    static final String FACTORY_SUFFIX = "$StubFactory";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(GRPC_GENERATED);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        String packageName = element.getPackageName();
        if (packageName.isEmpty() || element.isInner() || !element.isPublic()) {
            // Micronaut can't write generated sources to the default package
            return;
        }
        if (context.getClassElement(CHANNEL_FACTORY_TYPE).isEmpty()) {
            // micronaut-grpc-client-runtime is not on the compile classpath
            return;
        }
        List<StubMethod> stubMethods = stubMethods(element);
        if (stubMethods.isEmpty()) {
            return;
        }
        String factoryName = element.getSimpleName() + FACTORY_SUFFIX;
        Optional<GeneratedFile> file = context.visitGeneratedSourceFile(packageName, factoryName, element);
        if (file.isEmpty()) {
            return;
        }
        try (Writer writer = file.get().openWriter()) {
            writer.write(source(packageName, factoryName, element.getName(), stubMethods));
        } catch (IOException e) {
            context.fail("Unable to generate " + packageName + '.' + factoryName + ": " + e.getMessage(), element);
        }
    }

    private static List<StubMethod> stubMethods(ClassElement element) {
        List<StubMethod> stubMethods = new ArrayList<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyStatic())) {
            String name = method.getName();
            if (!method.isStatic() || !method.isPublic() || !name.startsWith("new") || !name.endsWith("Stub")) {
                continue;
            }
            ParameterElement[] parameters = method.getParameters();
            if (parameters.length != 1 || !CHANNEL_TYPE.equals(parameters[0].getType().getName())) {
                continue;
            }
            ClassElement stubType = method.getReturnType();
            if (!stubType.isPublic() || !stubType.isAssignable(ABSTRACT_STUB_TYPE)) {
                continue;
            }
            // protoc never uses '$' in class names, so the source name is the binary name with '.' for nesting
            stubMethods.add(new StubMethod(name, stubType.getName().replace('$', '.')));
        }
        return stubMethods;
    }

    private static String source(String packageName, String factoryName, String serviceName, List<StubMethod> stubMethods) {
        StringBuilder source = new StringBuilder()
            .append("package ").append(packageName).append(";\n\n")
            .append("/**\n")
            .append(" * Creates the client stubs of {@link ").append(serviceName).append("} for {@code @GrpcChannel} injection points.\n")
            .append(" * Generated by micronaut-grpc-client-processor.\n")
            .append(" */\n")
            .append("@io.micronaut.context.annotation.Factory\n")
            .append("@io.micronaut.core.annotation.Internal\n")
            .append("@io.micronaut.context.annotation.Requires(classes = ").append(CHANNEL_FACTORY_TYPE).append(".class)\n")
            .append("public final class ").append(factoryName).append(" {\n");
        for (StubMethod stubMethod : stubMethods) {
            source.append('\n')
                .append("    @io.micronaut.context.annotation.Prototype\n")
                .append("    @io.micronaut.context.annotation.Secondary\n")
                .append("    public ").append(stubMethod.stubType()).append(' ').append(stubMethod.name())
                .append("(io.micronaut.inject.InjectionPoint<?> injectionPoint, ")
                .append(CHANNEL_FACTORY_TYPE).append(" channels) {\n")
                .append("        return ").append(serviceName).append('.').append(stubMethod.name())
                .append("(channels.stubChannel(injectionPoint));\n")
                .append("    }\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private record StubMethod(String name, String stubType) {
    }
}
