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
package io.micronaut.grpc.annotation;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to expose a gRPC method for handling JSON over REST requests.
 * This annotation is intended to facilitate integration between gRPC services
 * and JSON REST-based clients by allowing gRPC service methods to be invoked
 * via REST with JSON payloads.
 * <br/>
 * Methods annotated with this annotation are processed at application startup
 * by a registrar, where they are registered for subsequent invocation via JSON
 * REST requests.
 * <br/>
 * This annotation should be applied at the method level in gRPC service classes.
 * It carries the {@link Executable} annotation with {@code processOnStartup=true},
 * ensuring that the relevant gRPC methods are processed during application startup.
 * <br/>
 * Target:
 * - Can only be applied to methods.
 * <br/>
 * Retention:
 * - Runtime, ensuring availability through reflection at runtime.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Executable(processOnStartup = true)
@Experimental
public @interface GrpcRestJsonExposed {
}
