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
package io.micronaut.grpc.server;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.EmbeddedServerInstance;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.runtime.server.EmbeddedServer;

/**
 * Implementation of the {@link EmbeddedServerInstance} interface for GRPC.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.0
 */
class GrpcServerInstance implements EmbeddedServerInstance {

    private final String id;
    private final URI uri;
    private final ConvertibleValues<String> metadata;
    private final EmbeddedServer embeddedServer;

    /**
     * Default constructor.
     *
     * @param embeddedServer The embedded server
     * @param id The ID
     * @param uri The URI
     * @param metadata The metadata
     * @param metadataContributors The metadata contributors
     * @param grpcConfiguration The GRPC Configuration
     */
    GrpcServerInstance(
        EmbeddedServer embeddedServer,
        String id,
        URI uri,
        @Nullable Map<String, String> metadata,
        @Nullable List<ServiceInstanceMetadataContributor> metadataContributors,
        GrpcServerConfiguration grpcConfiguration) {
        this.embeddedServer = embeddedServer;
        this.id = calculateInstanceId(id, grpcConfiguration);
        this.uri = uri;
        if (metadata == null) {
            metadata = new LinkedHashMap<>(5);
        }

        if (CollectionUtils.isNotEmpty(metadataContributors)) {
            for (ServiceInstanceMetadataContributor contributor : metadataContributors) {
                contributor.contribute(this, metadata);
            }
        }

        this.metadata = ConvertibleValues.of(metadata);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        return metadata;
    }

    @Override
    public EmbeddedServer getEmbeddedServer() {
        return embeddedServer;
    }

    private String calculateInstanceId(String id, @Nullable GrpcServerConfiguration grpcConfiguration) {
        if (grpcConfiguration != null && StringUtils.isNotEmpty(grpcConfiguration.getInstanceId())) {
            return grpcConfiguration.getInstanceId();
        } else {
            return id;
        }
    }
}
