package io.micronaut.grpc.annotation;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A meta annotation for annotation GRPC services. Note that annotation is more
 * for documentation purposes and not strictly necessary.
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
