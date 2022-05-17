/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.grpc.server.health;

import io.grpc.protobuf.services.HealthStatusManager;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.grpc.server.GrpcServerConfiguration;
import jakarta.inject.Singleton;

/**
 * @since 3.3.0
 */
@Factory
public class GrpcHealthFactory {

    /**
     * Creates a {@link HealthStatusManager} bean if GRPC health is enabled.
     *
     * @return The Singleton{@link HealthStatusManager} bean.
     */
    @Singleton
    @Requires(property = GrpcServerConfiguration.HEALTH_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(classes = HealthStatusManager.class)
    public HealthStatusManager healthStatusManager() {
        return new HealthStatusManager();
    }
}
