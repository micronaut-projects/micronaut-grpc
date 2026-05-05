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
package io.micronaut.grpc.server;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.StringUtils;

/**
 * Configuration for the optional in-process gRPC transport.
 *
 * @since 5.0.0
 */
@ConfigurationProperties(GrpcServerConfiguration.PREFIX)
@Internal
public class GrpcInProcessServerConfiguration {

    @Nullable
    private String inProcessName;

    /**
     * @return The configured in-process server name
     */
    public @NonNull Optional<String> getInProcessName() {
        return Optional.ofNullable(inProcessName);
    }

    /**
     * Sets the in-process server name for test transports.
     *
     * @param inProcessName The in-process server name
     */
    public void setInProcessName(@Nullable String inProcessName) {
        this.inProcessName = StringUtils.isNotEmpty(inProcessName) ? inProcessName : null;
    }
}
