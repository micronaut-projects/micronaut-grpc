package io.micronaut.grpc.server;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.annotation.Nullable;
import java.util.Optional;

@ConfigurationProperties(GrpcServerConfiguration.PREFIX + ".ssl")
public class GrpcSslConfiguration {
    private String certChain;

    private String privateKey;

    public Optional<String> getCertChain() {
        return Optional.ofNullable(certChain);
    }

    public void setCertChain(@Nullable String certChain) {
        this.certChain = certChain;
    }

    public Optional<String> getPrivateKey() {
        return Optional.ofNullable(privateKey);
    }

    public void setPrivateKey(@Nullable String privateKey) {
        this.privateKey = privateKey;
    }
}
