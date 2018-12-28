package io.micronaut.grpc.annotation;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be used to inject a GRPC client.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Singleton
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface GrpcService {
}
