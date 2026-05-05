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

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRuleResult;
import org.reactivestreams.Publisher;

/**
 * A security rule for a gRPC server call.
 *
 * @since 5.0.0
 */
public interface GrpcServerSecurityRule extends Ordered {

    /**
     * Checks the current gRPC server call.
     *
     * @param serverCall The current gRPC call
     * @param metadata The gRPC metadata
     * @param authentication The authentication, if available
     * @param <T> The request type
     * @param <S> The response type
     * @return A publisher that emits the rule result
     */
    <T, S> Publisher<SecurityRuleResult> check(ServerCall<T, S> serverCall,
                                               Metadata metadata,
                                               @Nullable Authentication authentication);
}
