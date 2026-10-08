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
package io.micronaut.grpc.channels;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.netty.NettyChannelBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The gRPC channels kept across the restarts of the application in development mode, with their connections, so that
 * the next generation's clients call on connections that are already open. A channel is built once, by the factory of
 * the first generation that asks for it, with an executor that runs each callback on the executor of the running
 * generation, so that it holds nothing of a generation: the factory only keeps a channel here when no interceptor is
 * applied to it, nothing of the application customizes its builder, and its name resolver is not a bean of the
 * context or a class of the application.
 *
 * <p>Retained across restarts until a change under {@value GrpcDefaultManagedChannelConfiguration#PREFIX} or
 * {@value GrpcManagedChannelConfiguration#PREFIX}, which configure the channels: the next generation then builds its
 * own, and these are shut down with the stopped context. A channel no generation asked for while it ran is shut down
 * as that generation stops.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Retain(invalidatedBy = {GrpcDefaultManagedChannelConfiguration.PREFIX, GrpcManagedChannelConfiguration.PREFIX})
@DevelopmentActive
final class DevelopmentGrpcChannels {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentGrpcChannels.class);

    private final Map<String, ManagedChannel> channels = new LinkedHashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private final Executor executor = new GenerationExecutor();
    private final AtomicInteger holdThreads = new AtomicInteger();
    private volatile @Nullable Executor current;
    private @Nullable ExecutorService holdExecutor;
    private boolean closed;

    /**
     * Created once, as the first generation starts, and kept from then on.
     */
    DevelopmentGrpcChannels() {
        initializeChannelReferences();
    }

    /**
     * The channel of the running generation for a key: the one kept, or one built and kept from now on, unless what
     * the channel would be built from belongs to the generation. The context is the generation's, and is not kept.
     *
     * @param context The context of the running generation
     * @param key The key of the channel
     * @param target The target of the channel, the name of its configuration
     * @return The channel, or null when it cannot be kept: the factory then builds it as outside development mode
     */
    @Nullable ManagedChannel channel(ApplicationContext context, String key, String target) {
        ExecutorService generationExecutor = context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO));
        if (current != generationExecutor) {
            // the first channel of the generation
            activate(generationExecutor);
            watchConfiguration(context);
        }
        String refusal = refusal(context, target);
        if (refusal != null) {
            LOG.debug("gRPC channel [{}] is not retained across restarts: {}", target, refusal);
            release(key);
            return null;
        }
        ManagedChannel kept = channel(key);
        if (kept != null) {
            return kept;
        }
        return build(key, context.createBean(NettyChannelBuilder.class, target));
    }

    /**
     * A change of the configuration of the channels restarts the application while channels are kept, so that the
     * restart releases them and the next generation builds them from it. The watch belongs to the context.
     */
    private void watchConfiguration(ApplicationContext context) {
        if (!(context instanceof WatchableBeanContext watchable)) {
            return;
        }
        ConfigurationWatcher watcher = change -> {
            if (change.initial() || change.all() || isEmpty()) {
                return ConfigurationWatcher.Outcome.IGNORED;
            }
            return ConfigurationWatcher.Outcome.REQUIRES_RESTART;
        };
        watchable.watchConfiguration(GrpcDefaultManagedChannelConfiguration.PREFIX, watcher);
        watchable.watchConfiguration(GrpcManagedChannelConfiguration.PREFIX, watcher);
    }

    private synchronized boolean isEmpty() {
        return channels.isEmpty();
    }

    /**
     * Why a channel cannot be kept, if it cannot: an interceptor, which is a bean of the generation, would be applied
     * to it; a listener of the application would customize its builder; or its name resolver is a bean of the
     * generation, such as the one of service discovery, or a class of the application.
     */
    private static @Nullable String refusal(ApplicationContext context, String target) {
        if (!context.getBeansOfType(ClientInterceptor.class).isEmpty()) {
            return "client interceptors apply to it";
        }
        for (BeanDefinition<BeanCreatedEventListener> definition : context.getBeanDefinitions(BeanCreatedEventListener.class)) {
            List<Argument<?>> arguments = definition.getTypeArguments(BeanCreatedEventListener.class);
            if (!arguments.isEmpty() && arguments.get(0).getType().isAssignableFrom(NettyChannelBuilder.class)
                && isApplicationClass(definition.getBeanType())) {
                return "the application listens to the creation of the channel builder in " + definition.getBeanType().getName();
            }
        }
        if (!context.getBeansOfType(NameResolverProvider.class).isEmpty()) {
            return "the context registers a name resolver";
        }
        NameResolverProvider resolver = resolverOf(context, target);
        if (resolver != null && isApplicationClass(resolver.getClass())) {
            return "its name resolver " + resolver.getClass().getName() + " is a class of the application";
        }
        return null;
    }

    /**
     * The name resolver of the target the configuration of a channel names, from the registry the channel builder
     * resolves it with; none for a channel to a socket address.
     */
    private static @Nullable NameResolverProvider resolverOf(ApplicationContext context, String target) {
        String prefix = GrpcManagedChannelConfiguration.PREFIX + '.' + target;
        if (context.getEnvironment().containsProperty(prefix + GrpcManagedChannelConfiguration.SETTING_URL)) {
            // a host and port, resolved with the default scheme, or another socket address, resolved directly
            return NameResolverRegistry.getDefaultRegistry().getProviderForScheme(NameResolverRegistry.getDefaultRegistry().getDefaultScheme());
        }
        String uri = context.getEnvironment().getProperty(prefix + GrpcManagedChannelConfiguration.SETTING_TARGET, String.class).orElse(target);
        String scheme = null;
        try {
            scheme = new URI(uri).getScheme();
        } catch (URISyntaxException e) {
            // not a URI: resolved with the default scheme
        }
        NameResolverRegistry registry = NameResolverRegistry.getDefaultRegistry();
        NameResolverProvider provider = scheme == null ? null : registry.getProviderForScheme(scheme);
        return provider != null ? provider : registry.getProviderForScheme(registry.getDefaultScheme());
    }

    /**
     * Whether a class was loaded by neither the classloader of this module nor one of its parents.
     */
    private static boolean isApplicationClass(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return false;
        }
        for (ClassLoader module = DevelopmentGrpcChannels.class.getClassLoader(); module != null; module = module.getParent()) {
            if (module == loader) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the callbacks of the channels on the executor of a generation from now on.
     *
     * @param generationExecutor The executor of the generation
     */
    void activate(Executor generationExecutor) {
        current = generationExecutor;
    }

    /**
     * The channel kept under a key, claimed by the running generation.
     *
     * @param key The key
     * @return The channel, or null when none is kept under the key
     */
    synchronized @Nullable ManagedChannel channel(String key) {
        ManagedChannel channel = channels.get(key);
        if (channel == null) {
            return null;
        }
        if (channel.isShutdown()) {
            channels.remove(key);
            return null;
        }
        claimed.add(key);
        LOG.debug("gRPC channel [{}] retained across the restart", key);
        return channel;
    }

    /**
     * Builds a channel to keep under a key.
     *
     * @param key The key
     * @param builder The builder of the generation, given the executor of this bean
     * @return The channel
     */
    private ManagedChannel build(String key, NettyChannelBuilder builder) {
        builder.executor(executor);
        // built on a thread of this bean: gRPC keeps the stack the channel was built from, as its allocation site, for
        // as long as the channel lives, and a stack through the application's classes would keep its generation
        ManagedChannel channel = CompletableFuture.supplyAsync(builder::build, holdExecutor()).join();
        ManagedChannel previous;
        synchronized (this) {
            if (closed) {
                return channel;
            }
            previous = channels.put(key, channel);
            claimed.add(key);
        }
        if (previous != null) {
            previous.shutdown();
        }
        return channel;
    }

    /**
     * Shuts the channel kept under a key down: the running generation builds its own.
     *
     * @param key The key
     */
    synchronized void release(String key) {
        ManagedChannel released = channels.remove(key);
        if (released != null) {
            released.shutdown();
        }
    }

    /**
     * Whether a channel is kept here.
     *
     * @param channel The channel
     * @return True if kept
     */
    synchronized boolean holds(ManagedChannel channel) {
        return channels.containsValue(channel);
    }

    /**
     * Called as a generation stops: the callbacks wait for the next generation's executor, and a channel the generation
     * did not ask for is shut down.
     */
    void generationEnded() {
        current = null;
        List<ManagedChannel> unclaimed = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Map.Entry<String, ManagedChannel>> iterator = channels.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, ManagedChannel> entry = iterator.next();
                if (!claimed.contains(entry.getKey())) {
                    iterator.remove();
                    unclaimed.add(entry.getValue());
                }
            }
            claimed.clear();
        }
        unclaimed.forEach(ManagedChannel::shutdown);
    }

    /**
     * Shuts the channels down: a change released them, or the development runtime closes.
     */
    @PreDestroy
    void close() {
        List<ManagedChannel> released;
        ExecutorService hold;
        synchronized (this) {
            closed = true;
            released = new ArrayList<>(channels.values());
            channels.clear();
            hold = holdExecutor;
            holdExecutor = null;
        }
        for (ManagedChannel channel : released) {
            try {
                if (!channel.shutdown().awaitTermination(1, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (hold != null) {
            hold.shutdown();
        }
    }

    private synchronized Executor holdExecutor() {
        ExecutorService hold = holdExecutor;
        if (hold == null) {
            hold = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "grpc-dev-channel-" + holdThreads.incrementAndGet());
                thread.setDaemon(true);
                // not the loader of the generation that happens to start the thread: it would keep it reachable
                thread.setContextClassLoader(DevelopmentGrpcChannels.class.getClassLoader());
                return thread;
            });
            if (!closed) {
                holdExecutor = hold;
            }
        }
        return hold;
    }

    /**
     * Initializes the class gRPC tracks the channels that are not shut down with, on a thread of its own: as it is
     * initialized it keeps the stack of the thread that initialized it, for the life of the process, and a stack
     * through the classes of a generation, which builds the first channel, would keep that generation reachable.
     */
    private static void initializeChannelReferences() {
        Thread thread = new Thread(() -> {
            try {
                Class.forName("io.grpc.internal.ManagedChannelOrphanWrapper$ManagedChannelReference", true, ManagedChannel.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                LOG.debug("Cannot initialize the gRPC channel references", e);
            }
        }, "grpc-dev-init");
        thread.setDaemon(true);
        thread.setContextClassLoader(DevelopmentGrpcChannels.class.getClassLoader());
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs the callbacks of the channels on the executor of the running generation, or on a thread of this bean while
     * none runs, or once that generation's executor no longer accepts them.
     */
    private final class GenerationExecutor implements Executor {
        @Override
        public void execute(Runnable task) {
            Executor running = current;
            if (running != null) {
                try {
                    running.execute(task);
                    return;
                } catch (RejectedExecutionException e) {
                    // the executor of a stopping generation
                }
            }
            holdExecutor().execute(task);
        }
    }
}
