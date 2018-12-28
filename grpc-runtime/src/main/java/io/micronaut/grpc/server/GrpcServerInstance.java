package io.micronaut.grpc.server;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class GrpcServerInstance implements EmbeddedServerInstance {

    private final String id;
    private final URI uri;
    private final ConvertibleValues<String> metadata;
    private final EmbeddedServer embeddedServer;

    GrpcServerInstance(
            EmbeddedServer embeddedServer,
            String id,
            URI uri,
            @Nullable Map<String, String> metadata,
            @javax.annotation.Nullable List<ServiceInstanceMetadataContributor> metadataContributors) {
        this.embeddedServer = embeddedServer;
        this.id = id;
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
}
