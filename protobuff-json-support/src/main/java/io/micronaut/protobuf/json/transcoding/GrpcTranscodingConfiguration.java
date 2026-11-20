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
package io.micronaut.protobuf.json.transcoding;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;

import java.time.Duration;

/**
 * Configuration of REST transcoding for gRPC methods that declare {@code google.api.http} rules.
 *
 * @since 5.3.0
 */
@Experimental
@ConfigurationProperties(GrpcTranscodingConfiguration.PREFIX)
public class GrpcTranscodingConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "grpc.rest.transcoding";

    /**
     * The property that enables REST transcoding.
     */
    public static final String ENABLED = PREFIX + ".enabled";

    /**
     * The default deadline of a transcoded call.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private boolean transcodingEnabled;
    private Duration timeout = DEFAULT_TIMEOUT;

    /**
     * @return Whether REST transcoding is enabled. Defaults to {@code false}.
     */
    public boolean isEnabled() {
        return transcodingEnabled;
    }

    /**
     * @param enabled Whether REST transcoding is enabled
     */
    public void setEnabled(boolean enabled) {
        this.transcodingEnabled = enabled;
    }

    /**
     * @return The deadline applied to each transcoded gRPC call
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * @param timeout The deadline applied to each transcoded gRPC call. Must be positive.
     */
    public void setTimeout(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("grpc.rest.transcoding.timeout must be greater than zero");
        }
        this.timeout = timeout;
    }
}
