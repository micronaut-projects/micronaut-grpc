/*
 * Copyright 2017-2019 original authors
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

import java.util.Map;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.AsyncSingleResultPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.runtime.server.EmbeddedServer;

import org.reactivestreams.Publisher;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.micronaut.core.util.CollectionUtils.mapOf;

/**
 * A {@link HealthIndicator} for Grpc server.
 *
 * @author Moe Haydar
 * @since 2.1.0
 */
@Singleton
@Requires(property = GrpcHealthFactory.HEALTH_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(beans = HealthEndpoint.class)
@Requires(beans = EmbeddedServer.class)
public class GrpcServerHealthIndicator implements HealthIndicator {

    private static final String ID = "grpc-server";

    private final EmbeddedServer server;

    /**
     * Default constructor.
     *
     * @param server The grpc embedded server
     */
    public GrpcServerHealthIndicator(@Named("grpc.server") EmbeddedServer server) {
        this.server = server;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return new AsyncSingleResultPublisher<>(this::getHealthResult);
    }

    /**
     * Checks if grpc is running and return status UP otherwise return status DOWN.
     *
     * @return Result with server address in the details and status UP or DOWN.
     */
    private HealthResult getHealthResult() {
        final HealthStatus healthStatus = server.isRunning() ? HealthStatus.UP : HealthStatus.DOWN;
        final String serverHost = server.getHost();
        try {

            int serverPort = server.getPort();
            final Map<?, ?> details = mapOf("host", serverHost, "port", serverPort);

            return HealthResult
                .builder(ID, healthStatus)
                .details(details)
                .build();
        } catch (IllegalStateException e) {
            /**
             * BUGFIX: it avoids to call the server.getPort() method when the gRPC-Server is DOWN because
             * it throws an unexpected exception that breaks the /health endpoint
             */

            final Map<?, ?> details = mapOf("host", serverHost, "port", "N/A");
            return HealthResult
                .builder(ID, healthStatus)
                .details(details)
                .build();
        }
    }
}
