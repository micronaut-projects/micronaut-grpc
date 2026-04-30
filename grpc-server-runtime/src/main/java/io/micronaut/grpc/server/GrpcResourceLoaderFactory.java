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
package io.micronaut.grpc.server;

import java.util.List;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.value.Base64ResourceLoader;
import io.micronaut.core.io.value.StringResourceLoader;

import jakarta.inject.Singleton;

/**
 * Provides core resource loading support when the HTTP module is not present.
 *
 * @since 5.0.0
 */
@Factory
public class GrpcResourceLoaderFactory {

    private final ClassLoader classLoader;

    /**
     * @param environment The environment
     */
    public GrpcResourceLoaderFactory(Environment environment) {
        this.classLoader = environment.getClassLoader();
    }

    /**
     * @return The classpath resource loader
     */
    @Singleton
    @Requires(missingBeans = ClassPathResourceLoader.class)
    protected ClassPathResourceLoader getClassPathResourceLoader() {
        return ClassPathResourceLoader.defaultLoader(classLoader);
    }

    /**
     * @return The filesystem resource loader
     */
    @Singleton
    @Requires(missingBeans = FileSystemResourceLoader.class)
    protected FileSystemResourceLoader fileSystemResourceLoader() {
        return FileSystemResourceLoader.defaultLoader();
    }

    /**
     * @return The string resource loader
     */
    @Singleton
    @Requires(missingBeans = StringResourceLoader.class)
    protected StringResourceLoader getStringResourceLoader() {
        return (StringResourceLoader) StringResourceLoader.getInstance();
    }

    /**
     * @return The base64 resource loader
     */
    @Singleton
    @Requires(missingBeans = Base64ResourceLoader.class)
    protected Base64ResourceLoader getBase64ResourceLoader() {
        return (Base64ResourceLoader) Base64ResourceLoader.getInstance();
    }

    /**
     * @param resourceLoaders The resource loaders
     * @return The resource resolver
     */
    @Singleton
    @Requires(missingBeans = ResourceResolver.class)
    protected ResourceResolver resourceResolver(List<ResourceLoader> resourceLoaders) {
        return new ResourceResolver(resourceLoaders);
    }
}
