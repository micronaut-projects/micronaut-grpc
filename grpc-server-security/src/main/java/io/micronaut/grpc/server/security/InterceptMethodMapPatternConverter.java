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
package io.micronaut.grpc.server.security;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts {@code grpc.server.security.intercept-method-map} entries to {@link InterceptMethodMapPattern}.
 *
 * @since 5.3.0
 */
@Internal
@Singleton
final class InterceptMethodMapPatternConverter implements TypeConverter<Map<String, Object>, InterceptMethodMapPattern> {

    private static final String PATTERN = "pattern";
    private static final String ACCESS = "access";

    private final ConversionService conversionService;

    InterceptMethodMapPatternConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<InterceptMethodMapPattern> convert(Map<String, Object> object,
                                                       Class<InterceptMethodMapPattern> targetType,
                                                       ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        String pattern = conversionService.convert(object.get(PATTERN), String.class)
            .orElseThrow(() -> invalid(object, PATTERN));
        Object accessValue = object.get(ACCESS);
        List<String> access = new ArrayList<>();
        if (accessValue instanceof Collection<?> values) {
            for (Object value : values) {
                conversionService.convert(value, String.class).ifPresent(access::add);
            }
        } else if (accessValue != null) {
            conversionService.convert(accessValue, String.class).ifPresent(access::add);
        }
        if (access.isEmpty()) {
            throw invalid(object, ACCESS);
        }
        return Optional.of(new InterceptMethodMapPattern(pattern, access));
    }

    private static ConfigurationException invalid(Map<String, Object> object, String key) {
        return new ConfigurationException("intercept-method-map configuration record " + object + " rejected due to missing or invalid " + key + " key.");
    }
}
