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

import io.grpc.BindableService;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates beans that implement gRPC services and have methods annotated with {@code @GrpcRestJsonExposed}.
 * This validator ensures the annotated methods belong to classes that implement {@link BindableService}, which is
 * a requirement for defining a valid gRPC service.
 * <br/>
 * The purpose of this class is to identify and validate any misconfigured or improperly annotated gRPC service
 * classes during the initialization phase of the application's lifecycle. This includes:
 * - Checking if any methods in the bean are annotated with {@code @GrpcRestJsonExposed}.
 * - Validating that the containing class of those methods implements the {@link BindableService} interface.
 * <br/>
 * If a violation is found during validation, an {@link IllegalStateException} is thrown to prevent misconfigured
 * services from running in the application.
 * <br/>
 * This class is triggered automatically as a listener for bean initialization events, utilizing the Micronaut
 * {@link BeanInitializedEventListener}.
 * <br/>
 * Note:
 * - This validator explicitly processes beans flagged with the {@code @GrpcRestJsonExposed} annotation at the
 *   method level. Only those beans are inspected for compliance.
 * - Validation happens prior to {@link PostConstruct} or other lifecycle hooks being executed.
 * <br/>
 * Responsibilities:
 * - Enforces that methods annotated with {@code @GrpcRestJsonExposed} are correctly defined within a gRPC
 *   {@link BindableService}.
 * - Logs successful validation or throws an exception if validation fails.
 * <br/>
 * Dependency Injection:
 * This class is a Singleton and is managed by Micronaut's dependency injection.
 */
@Internal
@Singleton
public class GrpcExposedMethodValidator implements BeanInitializedEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcExposedMethodValidator.class);

    /**
     * <p>Fired when a bean is instantiated but the {@link PostConstruct} initialization hooks have not
     * yet been called and in this case of bean {@link Provider} instances the
     * {@link Provider#get()} method has not yet been invoked.</p>
     *
     * @param event The bean initializing event
     * @return The bean or a replacement bean of the same type
     */
    @Override
    public Object onInitialized(BeanInitializingEvent<Object> event) {
        Class<?> beanType = event.getBeanDefinition().getBeanType();

        // Only inspect beans that declare methods with @GrpcRestJsonExposed explicitly
        List<ExecutableMethod<?, ?>> grpcAnnotatedMethods = event.getBeanDefinition().getExecutableMethods()
            .stream()
            .filter(method -> method.hasAnnotation(GrpcRestJsonExposed.class))
            .collect(Collectors.toList());

        if (!grpcAnnotatedMethods.isEmpty()) {
            // Validate that beanType is a BindableService
            if (!BindableService.class.isAssignableFrom(beanType)) {
                String methodNames = grpcAnnotatedMethods.stream()
                    .map(ExecutableMethod::getMethodName)
                    .collect(Collectors.joining(", "));

                throw new IllegalStateException(String.format(
                    "❌ Class '%s' declares method(s) [%s] annotated with @GrpcRestJsonExposed but it is NOT a gRPC Service implementation (%s)!",
                    beanType.getName(),
                    methodNames,
                    BindableService.class.getSimpleName()
                ));
            }

            LOG.debug("✅ @GrpcRestJsonExposed correctly annotated on {}", beanType.getName());
        }

        return event.getBean(); // return the bean unmodified explicitly required by Micronaut
    }
}
