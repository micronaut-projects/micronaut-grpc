/*
 * Copyright 2017-2021 original authors
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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micronaut.core.order.Ordered;
import io.micronaut.security.rules.SecurityRuleResult;

import java.util.Map;

/**
 * A security rule designed to offer gRPC server security.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
public interface GrpcServerSecurityRule extends Ordered {

    /**
     * Check the server call and claims for access to the method.
     *
     * @param serverCall the server call
     * @param metadata the metadata
     * @param claims the claims
     * @param <T> the type of the server request
     * @param <S> the type of the server response
     * @return the security rule result
     */
    <T, S> SecurityRuleResult check(final ServerCall<T, S> serverCall, final Metadata metadata, @Nullable final Map<String, Object> claims);

}
