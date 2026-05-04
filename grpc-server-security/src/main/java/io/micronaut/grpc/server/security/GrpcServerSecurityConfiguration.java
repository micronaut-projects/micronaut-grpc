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

import io.grpc.Status;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.grpc.server.GrpcServerConfiguration;

/**
 * gRPC server security configuration.
 *
 * @since 5.0.0
 */
@Requires(configuration = "io.micronaut.security")
@Requires(property = GrpcServerSecurityConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.TRUE)
@ConfigurationProperties(GrpcServerSecurityConfiguration.PREFIX)
public interface GrpcServerSecurityConfiguration extends Toggleable {

    /**
     * The configuration prefix.
     */
    String PREFIX = GrpcServerConfiguration.PREFIX + ".security";

    @Override
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isEnabled();

    /**
     * @return The gRPC status returned when authentication is missing.
     */
    @Bindable(defaultValue = "UNAUTHENTICATED")
    Status.Code getMissingAuthenticationStatus();

    /**
     * @return The gRPC status returned when authorization fails.
     */
    @Bindable(defaultValue = "PERMISSION_DENIED")
    Status.Code getFailedAuthorizationStatus();
}
