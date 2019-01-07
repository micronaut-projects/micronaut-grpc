/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.grpc.server;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Configuration for the SSL properties of GRPC
 *
 * @author graemerocher
 * @since 1.0
 */
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
