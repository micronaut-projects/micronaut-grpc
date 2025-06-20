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

}
