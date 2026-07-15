/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.protobuf.json;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;

/**
 * Configuration properties for enabling and configuring the gRPC JSON proxy feature in a Micronaut application.
 * <br/>
 * This configuration class enables support for exposing gRPC services via JSON endpoints
 * by leveraging the {@link GrpcProxyController}. The configuration is activated only when the
 * {@link GrpcProxyController} class is available in the classpath.
 * <br/>
 * This class is marked as experimental and may change in future versions of the framework.
 */
@ConfigurationProperties(GrpcJsonConfiguration.PREFIX)
@Requires(classes = {GrpcProxyController.class})
@Experimental
public class GrpcJsonConfiguration {
    public static final String PREFIX = "micronaut.grpc.json";

    private int maxResponseMessages = 10_000;
    private long maxResponseBytes = 16L * 1024 * 1024;

    /**
     * @return The maximum number of messages collected from a streaming response
     * @since 5.1.0
     */
    public int getMaxResponseMessages() {
        return maxResponseMessages;
    }

    /**
     * Sets the maximum number of messages collected from a streaming response.
     * @param maxResponseMessages The maximum message count
     * @since 5.1.0
     */
    public void setMaxResponseMessages(int maxResponseMessages) {
        if (maxResponseMessages < 1) {
            throw new IllegalArgumentException("maxResponseMessages must be greater than zero");
        }
        this.maxResponseMessages = maxResponseMessages;
    }

    /**
     * @return The maximum estimated serialized response size in bytes
     * @since 5.1.0
     */
    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    /**
     * Sets the maximum estimated serialized response size in bytes.
     * @param maxResponseBytes The maximum response size
     * @since 5.1.0
     */
    public void setMaxResponseBytes(long maxResponseBytes) {
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be greater than zero");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

}
