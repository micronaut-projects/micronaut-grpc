package io.micronaut.protobuf.json;

import io.grpc.stub.StreamObserver;
import io.micronaut.context.BeanContext;
import io.micronaut.grpc.annotation.GrpcRestJsonExposed;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class GrpcServiceRegistrar {
    private static final Logger log = getLogger(GrpcServiceRegistrar.class);

    public GrpcServiceRegistrar(BeanContext context, GrpcServiceRegistry registry) {
        log.info("GrpcServiceRegistrar initializing.  Registering gRPC service beans tagged with " +
            "{}", GrpcRestJsonExposed.class.getSimpleName());
        for (BeanDefinition<?> beanDefinition : context.getBeanDefinitions(Object.class)) {
            // Check if the bean has @GrpcService or @GrpcRestJsonExposed annotations
            if (isGrpcRelatedService(beanDefinition)) {
                registerGrpcServiceAsJson(context, registry, beanDefinition);
            }
        }
    }

    private void registerGrpcServiceAsJson(BeanContext context, GrpcServiceRegistry registry, BeanDefinition<?> beanDefinition) {
        // Attempt to resolve the bean only if necessary
        try {
            Object bean = context.findBean(beanDefinition.getBeanType()).orElse(null);
            if (bean != null) {
                String serviceName = bean.getClass().getSimpleName();
                Map<String, Method> methodMap = discoverGrpcMethods(bean);
                if (methodMap.isEmpty()) {
                    log.warn("No gRPC methods found for service: [{}]", serviceName);
                } else {
                    log.info("Registering gRPC service: [{}] with method map: [{}]",
                        serviceName, methodMapString(methodMap));
                    registry.registerService(serviceName, bean, methodMap);
                }
            }
        } catch (Exception e) {
            // Log an error or handle exceptions gracefully
            log.error("Failed to register gRPC service: [{}]", beanDefinition.getBeanType(), e);
        }
    }

    private boolean isGrpcRelatedService(BeanDefinition<?> beanDefinition) {
        // Check for @GrpcRestJsonExposed annotation without needing to instantiate
        return beanDefinition.getAnnotation(GrpcRestJsonExposed.class) != null;
    }

    private Map<String, Method> discoverGrpcMethods(Object serviceBean) {
        Map<String, Method> methods = new HashMap<>();
        for (Method method : serviceBean.getClass().getMethods()) {
            if (isGrpcMethod(method)) {
                methods.put(method.getName().toLowerCase(), method);
            }
        }
        return methods;
    }

    private boolean isGrpcMethod(Method method) {
        // Check if the method matches gRPC signature
        return method.getParameterCount() == 2 &&
                StreamObserver.class.isAssignableFrom(method.getParameterTypes()[1]);
    }

    private static String methodMapString(Map<String, Method> methodMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue().getName());
        }
        return sb.toString();
    }
}
