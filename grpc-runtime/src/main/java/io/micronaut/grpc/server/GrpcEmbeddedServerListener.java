package io.micronaut.grpc.server;

import io.micronaut.context.BeanContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Internal
@Singleton
class GrpcEmbeddedServerListener implements ApplicationEventListener<ServerStartupEvent>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcEmbeddedServerListener.class);

    private final BeanContext beanContext;
    private GrpcEmbeddedServer grpcServer;

    GrpcEmbeddedServerListener(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        final EmbeddedServer server = event.getSource();
        if ( !(server instanceof GrpcEmbeddedServer)) {
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
