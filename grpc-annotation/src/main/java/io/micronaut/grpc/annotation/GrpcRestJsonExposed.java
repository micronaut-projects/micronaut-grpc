package io.micronaut.grpc.annotation;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Executable
public @interface GrpcRestJsonExposed {
}
