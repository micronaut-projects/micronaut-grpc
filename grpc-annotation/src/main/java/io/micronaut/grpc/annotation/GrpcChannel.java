/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.grpc.annotation;

import io.micronaut.context.annotation.AliasFor;

import javax.inject.Scope;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be used to inject a GRPC {@code ManagedChannel}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Scope
public @interface GrpcChannel {
    /**
     * @return The URL or service ID of the remote service
     */
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return The ID of the client
     */
    @AliasFor(member = "value")
    String id() default "";
}
