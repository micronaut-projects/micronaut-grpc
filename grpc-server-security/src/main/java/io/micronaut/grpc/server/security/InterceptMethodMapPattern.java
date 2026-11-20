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
package io.micronaut.grpc.server.security;

import java.util.List;

/**
 * A gRPC authorization rule configured under {@code grpc.server.security.intercept-method-map}.
 *
 * <p>The {@code pattern} is a regular expression matched against the fully qualified gRPC method name,
 * for example {@code helloworld.Greeter/SayHello}.</p>
 *
 * @param pattern The regular expression matched against the full gRPC method name
 * @param access The access rules, for example {@code isAuthenticated()} or role names
 * @since 5.3.0
 */
public record InterceptMethodMapPattern(String pattern, List<String> access) {

    /**
     * @param pattern The regular expression matched against the full gRPC method name
     * @param access The access rules
     */
    public InterceptMethodMapPattern {
        access = List.copyOf(access);
    }
}
