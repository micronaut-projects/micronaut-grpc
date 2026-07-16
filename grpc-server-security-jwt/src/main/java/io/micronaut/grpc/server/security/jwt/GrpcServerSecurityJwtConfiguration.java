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
package io.micronaut.grpc.server.security.jwt;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.grpc.server.security.GrpcServerSecurityConfiguration;
import jakarta.validation.constraints.NotBlank;

/**
 * JWT-specific gRPC server security configuration.
 *
 * @since 5.0.0
 */
@Requires(configuration = "io.micronaut.security")
@Requires(property = GrpcServerSecurityJwtConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.TRUE)
@ConfigurationProperties(GrpcServerSecurityJwtConfiguration.PREFIX)
public interface GrpcServerSecurityJwtConfiguration extends Toggleable {

    /**
     * The configuration prefix.
     */
    String PREFIX = GrpcServerSecurityConfiguration.PREFIX + ".token.jwt";

    /**
     * The default metadata key used to carry the JWT.
     */
    String DEFAULT_METADATA_KEY_NAME = "JWT";

    @Override
    @Bindable(defaultValue = StringUtils.TRUE)
    boolean isEnabled();

    /**
     * @return The metadata key name that contains the JWT value.
     */
    @NotBlank
    @Bindable(defaultValue = DEFAULT_METADATA_KEY_NAME)
    String getMetadataKeyName();
}
