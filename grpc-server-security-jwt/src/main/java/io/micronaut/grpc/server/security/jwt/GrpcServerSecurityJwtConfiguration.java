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
package io.micronaut.grpc.server.security.jwt;

import io.grpc.Status;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.grpc.server.GrpcServerConfiguration;

import javax.validation.constraints.NotBlank;

/**
 * gRPC Security JWT configuration.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@ConfigurationProperties(GrpcServerSecurityJwtConfiguration.PREFIX)
@Requires(configuration = "io.micronaut.security")
@Requires(configuration = "io.micronaut.security.token.jwt")
@Requires(property = GrpcServerSecurityJwtConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
public interface GrpcServerSecurityJwtConfiguration extends Toggleable {

    /**
     * The configuration prefix.
     */
    String PREFIX = GrpcServerConfiguration.PREFIX + ".security.token.jwt";

    /**
     * The default name for the JWT metadata key.
     */
    String DEFAULT_METADATA_KEY_NAME = "JWT";

    /**
     * The order to be applied to the server interceptor in the interceptor chain.  Defaults
     * to {@value io.micronaut.core.order.Ordered#HIGHEST_PRECEDENCE} if not configured.
     *
     * @return the order
     */
    @Bindable(defaultValue = "" + Ordered.HIGHEST_PRECEDENCE)
    int getInterceptorOrder();

    /**
     * The {@link Status} returned by the interceptor when JWT is missing from metadata.
     * The default value is {@link Status.Code#UNAUTHENTICATED}
     *
     * @return the status
     */
    @Bindable(defaultValue = "UNAUTHENTICATED")
    Status.Code getMissingTokenStatus();

    /**
     * The {@link Status} returned by the interceptor when JWT validation fails. The
     * default value is {@link Status.Code#PERMISSION_DENIED}
     *
     * @return the status
     */
    @Bindable(defaultValue = "PERMISSION_DENIED")
    Status.Code getFailedValidationTokenStatus();

    /**
     * The name of the metadata key which holds the JWT.  Defaults
     * to {@value #DEFAULT_METADATA_KEY_NAME} if not configured.
     *
     * @return the metadata key name
     */
    @NotBlank
    @Bindable(defaultValue = DEFAULT_METADATA_KEY_NAME)
    String getMetadataKeyName();

}
