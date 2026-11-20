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
package io.micronaut.grpc.clientonly;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creates channels in an application that only has the gRPC client, without the gRPC server or HTTP modules that
 * provide a {@link ResourceResolver} bean.
 */
class ClientOnlyChannelTest {

    @Test
    void plaintextChannelWithoutResourceResolverBean() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertFalse(context.containsBean(ResourceResolver.class), "the classpath must not provide a ResourceResolver bean");
            ManagedChannel channel = assertDoesNotThrow(() -> context.createBean(NettyChannelBuilder.class, "localhost:50051").build());
            channel.shutdownNow();
        }
    }

    @Test
    void mutualTlsChannelResolvesClasspathCertificates() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "grpc.client.ssl.trust-certificates", "classpath:mtls/ca.crt",
            "grpc.client.ssl.cert-chain", "classpath:mtls/client.crt",
            "grpc.client.ssl.private-key", "classpath:mtls/client.key"
        ))) {
            assertFalse(context.containsBean(ResourceResolver.class), "the classpath must not provide a ResourceResolver bean");
            ManagedChannel channel = assertDoesNotThrow(() -> context.createBean(NettyChannelBuilder.class, "localhost:50051").build());
            channel.shutdownNow();
        }
    }

    @Test
    void missingCertificateIsReported() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "grpc.client.ssl.trust-certificates", "classpath:mtls/missing.crt"
        ))) {
            BeanInstantiationException e = assertThrows(BeanInstantiationException.class,
                () -> context.createBean(NettyChannelBuilder.class, "localhost:50051"));
            Throwable cause = e;
            while (cause != null && !(cause instanceof ConfigurationException)) {
                cause = cause.getCause();
            }
            ConfigurationException configurationException = assertInstanceOf(ConfigurationException.class, cause);
            assertTrue(configurationException.getMessage().contains("Certificate resource not found: classpath:mtls/missing.crt"));
        }
    }
}
