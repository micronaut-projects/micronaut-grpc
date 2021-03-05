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

import io.grpc.Status;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.grpc.server.GrpcServerConfiguration;

/**
 * gRPC Server Security Configuration.
 *
 * @since 2.4.0
 * @author Brian Wyka
 */
@Requires(configuration = "io.micronaut.security")
@ConfigurationProperties(GrpcServerSecurityConfiguration.PREFIX)
public interface GrpcServerSecurityConfiguration extends Toggleable {

    String PREFIX = GrpcServerConfiguration.PREFIX + ".security";

    /**
     * The {@link Status} returned by the interceptor when there is no {@link io.micronaut.security.authentication.Authentication} present.
     * The default value is {@link Status.Code#UNAUTHENTICATED}
     *
     * @return the status
     */
    @Bindable(defaultValue = "UNAUTHENTICATED")
    Status.Code getMissingAuthenticationStatus();

    /**
     * The {@link Status} returned by the interceptor when authorization fails. The
     * default value is {@link Status.Code#PERMISSION_DENIED}
     *
     * @return the status
     */
    @Bindable(defaultValue = "PERMISSION_DENIED")
    Status.Code getFailedAuthorizationStatus();

}
