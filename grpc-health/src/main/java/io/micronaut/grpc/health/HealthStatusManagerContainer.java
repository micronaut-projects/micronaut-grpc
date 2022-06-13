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
package io.micronaut.grpc.health;

import io.grpc.protobuf.services.HealthStatusManager;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

/**
 * A container for the {@link HealthStatusManager}.
 *
 * @since 3.3.0
 */
@Singleton
@Requires(classes = HealthStatusManager.class)
@Requires(property = GrpcHealthFactory.HEALTH_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class HealthStatusManagerContainer {

    private final HealthStatusManager healthStatusManager;

    public HealthStatusManagerContainer(HealthStatusManager healthStatusManager) {
        this.healthStatusManager = healthStatusManager;
    }

    /**
     * @return The {@link HealthStatusManager}
     */
    public HealthStatusManager getHealthStatusManager() {
        return healthStatusManager;
    }
}
