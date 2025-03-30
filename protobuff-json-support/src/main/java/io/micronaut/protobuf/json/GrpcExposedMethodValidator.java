package io.micronaut.protobuf.json;

import io.grpc.BindableService;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class GrpcExposedMethodValidator implements BeanInitializedEventListener<Object> {

    private static final Logger log = LoggerFactory.getLogger(GrpcExposedMethodValidator.class);

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

            log.debug("✅ @GrpcRestJsonExposed correctly annotated on {}", beanType.getName());
        }

        return event.getBean(); // return the bean unmodified explicitly required by Micronaut
    }
}
