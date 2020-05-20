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
package io.micronaut.grpc.channels;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;

import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Converter registrar for gRPC.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@Singleton
@Internal
class GrpcClientTypeConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(CharSequence.class, SocketAddress.class, charSequence -> {
            String[] parts = charSequence.toString().split(":");
            if (parts.length == 2) {
                int port = Integer.parseInt(parts[1]);
                return new InetSocketAddress(parts[0], port);
            } else {
                return null;
            }
        });
    }
}
