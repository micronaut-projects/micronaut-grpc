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
package io.micronaut.grpc.channels;

import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.netty.handler.ssl.SslContextBuilder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Configures TLS, including mutual TLS, for a gRPC channel from {@code grpc.channels.<name>.ssl.*}, or from the
 * {@code grpc.client.ssl.*} defaults when the channel has no {@code ssl} settings of its own.
 *
 * <p>Supported keys: {@code trust-certificates} (PEM CA certificates that verify the server), and {@code cert-chain}
 * with {@code private-key} (the client certificate presented to servers that require one).</p>
 *
 * @since 5.3.0
 */
@Internal
final class GrpcChannelSslSupport {

    static final String SSL = ".ssl.";
    static final String TRUST_CERTIFICATES = "trust-certificates";
    static final String CERT_CHAIN = "cert-chain";
    static final String PRIVATE_KEY = "private-key";

    private GrpcChannelSslSupport() {
    }

    /**
     * Applies the TLS settings of a channel to its builder, if any are configured.
     *
     * @param builder The channel builder
     * @param channelName The name of the named channel configuration, or {@code null} for the default configuration
     * @param environment The environment
     * @param resourceResolverSupplier Supplies the resolver for certificate locations, only called when TLS is configured
     */
    static void apply(NettyChannelBuilder builder,
                      @Nullable String channelName,
                      Environment environment,
                      Supplier<ResourceResolver> resourceResolverSupplier) {
        String prefix = sslPrefix(channelName, environment);
        Optional<String> trust = environment.getProperty(prefix + TRUST_CERTIFICATES, String.class);
        Optional<String> certChain = environment.getProperty(prefix + CERT_CHAIN, String.class);
        Optional<String> privateKey = environment.getProperty(prefix + PRIVATE_KEY, String.class);
        if (trust.isEmpty() && certChain.isEmpty() && privateKey.isEmpty()) {
            return;
        }
        if (certChain.isPresent() != privateKey.isPresent()) {
            throw new ConfigurationException("Both '" + prefix + CERT_CHAIN + "' and '" + prefix + PRIVATE_KEY + "' must be configured for a client certificate");
        }
        ResourceResolver resourceResolver = resourceResolverSupplier.get();
        SslContextBuilder sslContext = GrpcSslContexts.forClient();
        try {
            if (trust.isPresent()) {
                try (InputStream trustStream = open(resourceResolver, trust.get())) {
                    sslContext.trustManager(trustStream);
                }
            }
            if (certChain.isPresent()) {
                try (InputStream certStream = open(resourceResolver, certChain.get());
                     InputStream keyStream = open(resourceResolver, privateKey.get())) {
                    sslContext.keyManager(certStream, keyStream);
                }
            }
            builder.sslContext(sslContext.build());
        } catch (IOException e) {
            throw new ConfigurationException("Unable to configure TLS for gRPC channel: " + e.getMessage(), e);
        }
    }

    private static String sslPrefix(@Nullable String channelName, Environment environment) {
        if (channelName != null) {
            String channelPrefix = GrpcManagedChannelConfiguration.PREFIX + '.' + channelName + ".ssl";
            if (environment.containsProperties(channelPrefix)) {
                return channelPrefix + '.';
            }
        }
        return GrpcDefaultManagedChannelConfiguration.PREFIX + SSL;
    }

    private static InputStream open(ResourceResolver resourceResolver, String location) {
        return resourceResolver.getResourceAsStream(location)
            .orElseThrow(() -> new ConfigurationException("Certificate resource not found: " + location));
    }
}
