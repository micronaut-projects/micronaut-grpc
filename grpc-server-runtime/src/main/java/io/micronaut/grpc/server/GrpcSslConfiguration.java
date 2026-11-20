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

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.netty.handler.ssl.ClientAuth;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for the SSL properties of GRPC.
 *
 * @author graemerocher
 * @since 1.0
 */
@ConfigurationProperties(GrpcServerConfiguration.PREFIX + ".ssl")
public class GrpcSslConfiguration {

    private String certChain;

    private String privateKey;

    private @Nullable String trustCertificates;

    private ClientAuth clientAuth = ClientAuth.NONE;

    /**
     * @return The cert chain
     */
    public Optional<String> getCertChain() {
        return Optional.ofNullable(certChain);
    }

    /**
     * @param certChain Sets the cert chain
     */
    public void setCertChain(@Nullable String certChain) {
        this.certChain = certChain;
    }

    /**
     * @return The private key
     */
    public Optional<String> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    /**
     * @param privateKey Sets the private key
     */
    public void setPrivateKey(@Nullable String privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * The location of the PEM-encoded CA certificates used to verify client certificates.
     *
     * @return The trust certificates location
     * @since 5.3.0
     */
    public Optional<String> getTrustCertificates() {
        return Optional.ofNullable(trustCertificates);
    }

    /**
     * Sets the location of the PEM-encoded CA certificates used to verify client certificates, for example
     * {@code classpath:ca.crt} or {@code file:/etc/grpc/ca.crt}. Without it, the JVM's default trust store is used.
     *
     * @param trustCertificates The trust certificates location
     * @since 5.3.0
     */
    public void setTrustCertificates(@Nullable String trustCertificates) {
        this.trustCertificates = trustCertificates;
    }

    /**
     * Whether clients must present a certificate.
     *
     * @return The client authentication mode, {@link ClientAuth#NONE} by default
     * @since 5.3.0
     */
    public ClientAuth getClientAuth() {
        return clientAuth;
    }

    /**
     * Sets whether clients must present a certificate: {@code none} (the default), {@code optional} or
     * {@code require}. Use {@code require} for mutual TLS.
     *
     * @param clientAuth The client authentication mode
     * @since 5.3.0
     */
    public void setClientAuth(@Nullable ClientAuth clientAuth) {
        this.clientAuth = clientAuth == null ? ClientAuth.NONE : clientAuth;
    }
}
