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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application event listener that will startup the {@link GrpcEmbeddedServer} as a secondary server
 * on a different port allowing Micronaut's HTTP server and GRPC to run side by side.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Singleton
@Requires(beans = GrpcEmbeddedServer.class)
class GrpcEmbeddedServerListener implements ApplicationEventListener<ServerStartupEvent>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcEmbeddedServerListener.class);

    private final BeanContext beanContext;
    private GrpcEmbeddedServer grpcServer;

    /**
     * Default constructor.
     * @param beanContext The bean context
     */
    GrpcEmbeddedServerListener(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        final EmbeddedServer server = event.getSource();
        if (!(server instanceof GrpcEmbeddedServer)) {
            this.grpcServer = beanContext.getBean(GrpcEmbeddedServer.class);
            grpcServer.start();
            if (LOG.isInfoEnabled()) {
                LOG.info("GRPC started on port {}", grpcServer.getPort());
            }
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (grpcServer != null && grpcServer.isRunning()) {
            grpcServer.stop();
        }
    }
}
