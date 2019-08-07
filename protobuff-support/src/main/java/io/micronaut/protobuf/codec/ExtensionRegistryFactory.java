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
package io.micronaut.protobuf.codec;

import com.google.protobuf.ExtensionRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * Creates the default {@link ExtensionRegistry}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = ExtensionRegistry.class)
public class ExtensionRegistryFactory {

    /**
     * Constructs the extension registry.
     * @return The extension registry
     */
    @Singleton
    protected ExtensionRegistry extensionRegistry() {
        return ExtensionRegistry.newInstance();
    }
}
