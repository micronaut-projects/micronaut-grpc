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

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.netty.NettyServerBuilder;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.grpc.server.health.HealthStatusManagerContainer;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Keeps the gRPC server in step with the code in development mode. It exists only in development mode, so nothing of
 * it is on the path of a call outside it.
 *
 * <p><b>Across a restart.</b> The server is kept by {@link DevelopmentGrpcServer}, with its port, its connections and
 * its event loops. As the server builder of a generation is configured, this bean decides whether the generation uses
 * it: the builder then receives the indirections of the kept server in place of the services, interceptors and
 * executor of the generation, and the server built from it the first time is the one every following generation uses.
 * As the generation's server starts, its services, with its interceptors applied to each in the order the builder
 * would have applied them, are made the ones the kept server serves; as it stops, its calls are drained. The server is
 * not kept, and each generation builds and binds its own as outside development mode, when the application defines a
 * {@link ServerTransportFilter}, which would see the connections of one generation open and those of another close, or
 * a {@link BeanCreatedEventListener} of the server builder, which could leave on the kept server what belongs to the
 * generation that built it.</p>
 *
 * <p><b>In place.</b> A definition of a {@link BindableService}, a {@link ServerInterceptor} or a
 * {@link ServerServiceDefinition} registered or removed makes the services of the context, as they are now, the ones
 * served. A class of one of these beans changed in place recreates the bean, then makes the services of the context
 * the ones served; a class change in place that retires a classloader recreates them all. The server builder and the
 * embedded server receive these beans, so they are recreated with them, and the embedded server is created and
 * started again on the kept server. The services replaced keep serving until then, and the calls in flight on them
 * finish on them, or are closed with those of the generation when it stops.</p>
 *
 * <p>A change of configuration under {@value GrpcServerConfiguration#PREFIX} restarts the application while the server
 * is kept, so that the server is built again from it.</p>
 *
 * <p>Each bean is recreated through {@link WatchableBeanContext#recreate(Object)}; a context that does not track bean
 * dependencies recreates nothing. The watches run after those of other modules, so that a bean another module recreates
 * for the same change is in place first.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentGrpcReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentGrpcReloader.class);
    private static final List<Class<?>> SERVED_TYPES = List.of(BindableService.class, ServerInterceptor.class, ServerServiceDefinition.class);

    private final BeanContext beanContext;
    private final DevelopmentGrpcServer server;
    private final List<BeanWatch> watches = new ArrayList<>();
    /**
     * Whether the server of this generation is the kept one.
     */
    private volatile boolean adopted;
    /**
     * The builder prepared for the kept server.
     */
    private @Nullable ServerBuilder<?> adoptedBuilder;
    private @Nullable Executor executor;
    private volatile DevelopmentGrpcServer.@Nullable Generation generation;
    /**
     * Whether beans are being recreated in place: the embedded server stopped with them is created again at once.
     */
    private volatile boolean replacingInPlace;

    /**
     * @param beanContext The context, watched when it can be
     * @param server The kept server
     */
    DevelopmentGrpcReloader(BeanContext beanContext, DevelopmentGrpcServer server) {
        this.beanContext = beanContext;
        this.server = server;
        if (beanContext instanceof WatchableBeanContext watchable) {
            for (Class<?> type : SERVED_TYPES) {
                watches.add(watchable.watchDefinitions(type, null, new DefinitionsWatcher<>()));
            }
            watches.add(watchable.watchClassChanges(new ClassWatcher()));
            watches.add(watchable.watchConfiguration(GrpcServerConfiguration.PREFIX, change -> {
                if (change.initial() || change.all() || !adopted) {
                    return ConfigurationWatcher.Outcome.IGNORED;
                }
                // the kept server was built from the previous values: the restart releases it
                return ConfigurationWatcher.Outcome.REQUIRES_RESTART;
            }));
        }
    }

    /**
     * Decides whether the generation uses the kept server, and if so prepares its builder for it, in place of adding
     * the services, interceptors and executor of the generation to it.
     *
     * @param builder The builder of the generation
     * @param configuration The configuration
     * @param transportFilters The transport filters of the generation
     * @return Whether the generation uses the kept server: the builder is then prepared, and must be given nothing more
     */
    boolean adopt(ServerBuilder<?> builder, GrpcServerConfiguration configuration, @Nullable List<ServerTransportFilter> transportFilters) {
        String refusal = refusal(transportFilters);
        if (refusal != null) {
            LOG.debug("The gRPC server is not retained across restarts: {}", refusal);
            adopted = false;
            // a server kept from before would hold the port this generation binds
            server.release();
            return false;
        }
        executor = configuration.getExecutor()
            .map(name -> beanContext.findBean(Executor.class, Qualifiers.byName(name))
                .orElseThrow(() -> new ConfigurationException("No executor bean named [" + name + "] is available")))
            .orElseGet(() -> beanContext.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)));
        server.prepare(builder, configuration.getAwaitTermination());
        adoptedBuilder = builder;
        adopted = true;
        return true;
    }

    /**
     * The server of the generation: the kept one, or the one built from the builder.
     *
     * @param builder The builder
     * @return The server
     */
    Server build(ServerBuilder<?> builder) {
        return adopted && builder == adoptedBuilder ? server.server(builder) : builder.build();
    }

    /**
     * Starts the server of the generation: the kept one is started once, and serves the services of the generation
     * from now on.
     *
     * @param generationServer The server
     * @throws IOException When the server cannot bind
     */
    void start(Server generationServer) throws IOException {
        if (!adopted || !server.holds(generationServer)) {
            generationServer.start();
            return;
        }
        server.start();
        activate();
    }

    /**
     * Stops the server of the generation: the kept one stops serving its services, and its calls are drained.
     *
     * @param generationServer The server
     * @return Whether it was the kept one, which is left running
     */
    boolean stop(Server generationServer) {
        if (!adopted || !server.holds(generationServer)) {
            return false;
        }
        DevelopmentGrpcServer.Generation stopping = generation;
        generation = null;
        if (stopping != null && !replacingInPlace) {
            server.deactivate(stopping);
        }
        // replaced in place, the generation serves on until the embedded server created again activates the next one,
        // and its calls in flight finish on it
        return true;
    }

    /**
     * Closes the watches; a server no generation used is shut down.
     */
    @PreDestroy
    void close() {
        watches.forEach(BeanWatch::close);
        watches.clear();
        server.generationEnded();
    }

    private void activate() {
        Executor generationExecutor = executor;
        if (generationExecutor == null) {
            return;
        }
        DevelopmentGrpcServer.Generation next = new DevelopmentGrpcServer.Generation(services(), interceptors(), generationExecutor);
        generation = next;
        server.activate(next);
    }

    /**
     * The services as the server builder adds them: the health service, the bindable services, then the service
     * definitions.
     */
    private List<ServerServiceDefinition> services() {
        List<ServerServiceDefinition> services = new ArrayList<>();
        beanContext.findBean(HealthStatusManagerContainer.class)
            .ifPresent(container -> services.add(container.getHealthStatusManager().getHealthService().bindService()));
        for (BindableService service : beanContext.getBeansOfType(BindableService.class)) {
            services.add(service.bindService());
        }
        services.addAll(beanContext.getBeansOfType(ServerServiceDefinition.class));
        return services;
    }

    /**
     * The interceptors in the order the server builder is given them.
     */
    private List<ServerInterceptor> interceptors() {
        List<ServerInterceptor> interceptors = new ArrayList<>(beanContext.getBeansOfType(ServerInterceptor.class));
        OrderUtil.reverseSort(interceptors);
        return interceptors;
    }

    /**
     * Why the server cannot be kept for the generation, if it cannot.
     */
    private @Nullable String refusal(@Nullable List<ServerTransportFilter> transportFilters) {
        if (transportFilters != null && !transportFilters.isEmpty()) {
            return "the application defines transport filters " + transportFilters;
        }
        for (BeanDefinition<BeanCreatedEventListener> definition : beanContext.getBeanDefinitions(BeanCreatedEventListener.class)) {
            List<Argument<?>> arguments = definition.getTypeArguments(BeanCreatedEventListener.class);
            if (!arguments.isEmpty() && arguments.get(0).getType().isAssignableFrom(NettyServerBuilder.class)
                && isApplicationClass(definition.getBeanType())) {
                return "the application listens to the creation of the server builder in " + definition.getBeanType().getName();
            }
        }
        return null;
    }

    /**
     * Whether a class was loaded by neither the classloader of this module nor one of its parents.
     */
    private static boolean isApplicationClass(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return false;
        }
        for (ClassLoader module = DevelopmentGrpcReloader.class.getClassLoader(); module != null; module = module.getParent()) {
            if (module == loader) {
                return false;
            }
        }
        return true;
    }

    private void onClassChange(ClassChangeEvent change) {
        if (change.strategy() == ReloadStrategy.RESTART) {
            // the next generation serves its own services on the kept server
            return;
        }
        boolean retired = !change.retiredLoaders().isEmpty();
        Set<String> changed = new HashSet<>();
        for (ClassChange classChange : change.changes()) {
            changed.add(classChange.className());
        }
        List<Object> beans = new ArrayList<>();
        for (Class<?> type : SERVED_TYPES) {
            for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(type)) {
                BeanDefinition<?> definition = registration.getBeanDefinition();
                boolean stale = retired || changed.contains(definition.getBeanType().getName())
                    || definition.getDeclaringType().map(declaring -> changed.contains(declaring.getName())).orElse(false);
                if (stale && !containsSame(beans, registration.bean())) {
                    beans.add(registration.bean());
                }
            }
        }
        if (beans.isEmpty()) {
            return;
        }
        LOG.debug("Recreating the gRPC services and interceptors of the changed classes {}", changed);
        replacingInPlace = true;
        try {
            if (beanContext instanceof WatchableBeanContext context) {
                for (Object bean : beans) {
                    // false for a bean destroyed with one recreated before it: the context creates it again on request
                    context.recreate(bean);
                }
            }
            serveCurrent();
        } finally {
            replacingInPlace = false;
        }
    }

    /**
     * Makes the services of the context, as they are now, the ones served: the embedded server, recreated with the
     * beans it received, is created again and started, or the running one serves them.
     */
    private void serveCurrent() {
        if (!adopted) {
            return;
        }
        if (generation == null) {
            // the embedded server was destroyed with a bean it received: created and started again on the kept server
            beanContext.findBean(GrpcEmbeddedServer.class).ifPresent(GrpcEmbeddedServer::start);
        } else {
            activate();
        }
    }

    private static boolean containsSame(Collection<Object> beans, Object bean) {
        for (Object taken : beans) {
            if (taken == bean) {
                return true;
            }
        }
        return false;
    }

    /**
     * Follows the definitions of the services, the interceptors and the service definitions. The first batch is what
     * the context started with.
     *
     * @param <T> The watched type
     */
    private final class DefinitionsWatcher<T> implements BeanDefinitionWatcher<T>, Ordered {
        @Override
        public void onChange(BeanDefinitionChange<T> change) {
            if (change.initial() || change.added().isEmpty() && change.removed().isEmpty()) {
                return;
            }
            LOG.debug("The gRPC services or interceptors changed: {}", change);
            serveCurrent();
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    /**
     * Follows a class change applied in place.
     */
    private final class ClassWatcher implements ClassChangeWatcher, Ordered {
        @Override
        public void onChange(ClassChangeEvent change) {
            onClassChange(change);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
