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

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.HandlerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The gRPC server kept across the restarts of the application in development mode, with its port, its connections
 * and its event loops. It is built once, from the server builder of the first generation that uses it, with two
 * indirections in place of what the application provides, so that it holds nothing of a generation:
 *
 * <ul>
 *     <li>its handler registry is a fallback registry that looks the methods up in the registry of the running
 *     generation, made of that generation's services, with that generation's interceptors applied to each;</li>
 *     <li>its executor runs each task on the executor of the running generation.</li>
 * </ul>
 *
 * <p>While one generation stops and the next starts, a call that arrives waits for the next generation, for as long as
 * the stopping generation may wait for its calls, the server's await termination, and 30 seconds more, rather than
 * failing: the method is looked up, on a thread of this bean, once the next generation is running. A call of the
 * stopping generation, or of services it replaced in place, that is still running once the await termination
 * elapsed, such as a stream left open, is closed with {@code UNAVAILABLE}, so that nothing of that generation stays
 * reachable.</p>
 *
 * <p>Retained across restarts until a change under {@value GrpcServerConfiguration#PREFIX}, which configures the
 * server: the next generation then builds its own server, and this one is shut down with the stopped context. It
 * copies the await termination of the configuration and keeps no configuration bean. A generation that does not use
 * it, because the server is disabled or would hold what the application provides, shuts its server down first.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Retain(invalidatedBy = GrpcServerConfiguration.PREFIX)
@DevelopmentActive
final class DevelopmentGrpcServer {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentGrpcServer.class);
    /**
     * How long a call waits for the next generation to start, beyond the time the stopping one waits for its calls.
     */
    private static final Duration NEXT_GENERATION_START = Duration.ofSeconds(30);
    private static final MethodDescriptor.Marshaller<InputStream> RAW = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(InputStream value) {
            return value;
        }

        @Override
        public InputStream parse(InputStream stream) {
            return stream;
        }
    };

    private final Object lock = new Object();
    private final GenerationRegistry registry = new GenerationRegistry();
    private final GenerationExecutor executor = new GenerationExecutor();
    private final AtomicInteger holdThreads = new AtomicInteger();
    private @Nullable Server server;
    private boolean started;
    private volatile @Nullable Generation current;
    /**
     * The generations replaced in place while calls of theirs were running, drained with the one that stops.
     */
    private final List<Generation> replacedGenerations = new ArrayList<>();
    /**
     * Whether a generation used the server since the last one ended.
     */
    private boolean claimed;
    private boolean closed;
    /**
     * How long a call waits for the next generation, and a stopping generation for its calls.
     */
    private volatile Duration timeout = GrpcServerConfiguration.DEFAULT_AWAIT_TERMINATION;
    /**
     * Runs the tasks of the server while no generation runs, made on first use.
     */
    private @Nullable ExecutorService holdExecutor;

    /**
     * Created once, as the first generation starts, and kept from then on.
     */
    DevelopmentGrpcServer() {
        initializeChannelReferences();
    }

    /**
     * Sets the indirections on the builder of a generation, in place of the services, interceptors and executor of
     * the generation, which the generation activates as it starts.
     *
     * @param builder The builder
     * @param awaitTermination How long a stopping generation waits for its calls
     */
    void prepare(ServerBuilder<?> builder, Duration awaitTermination) {
        timeout = awaitTermination;
        builder.fallbackHandlerRegistry(registry);
        builder.executor(executor);
    }

    /**
     * The server: the one kept, or one built from the builder, which {@link #prepare} prepared, for the next ones.
     *
     * @param builder The builder of the generation
     * @return The server
     */
    Server server(ServerBuilder<?> builder) {
        synchronized (lock) {
            claimed = true;
            Server kept = server;
            if (kept != null && !kept.isShutdown()) {
                LOG.debug("gRPC server on {} retained across the restart", kept.getListenSockets());
                return kept;
            }
            Server built = builder.build();
            server = built;
            started = false;
            return built;
        }
    }

    /**
     * Starts the server unless it runs already.
     *
     * @throws IOException When it cannot bind
     */
    void start() throws IOException {
        synchronized (lock) {
            Server kept = server;
            if (kept != null && !started) {
                kept.start();
                started = true;
            }
        }
    }

    /**
     * Whether the given server is this one's.
     *
     * @param candidate The server
     * @return True if kept here
     */
    boolean holds(Server candidate) {
        synchronized (lock) {
            return server == candidate;
        }
    }

    /**
     * Makes the services of a generation the ones the server serves, at once: a call looks its method up in the
     * registry of one generation or the next, never in none.
     *
     * @param generation The generation
     */
    void activate(Generation generation) {
        synchronized (lock) {
            Generation replaced = current;
            if (replaced != null && replaced != generation) {
                // replaced in place: its calls in flight finish on it, and are drained with the generation that stops
                replacedGenerations.removeIf(Generation::isIdle);
                if (!replaced.isIdle()) {
                    replacedGenerations.add(replaced);
                }
            }
            current = generation;
            lock.notifyAll();
        }
    }

    /**
     * The generation stops: calls that arrive from now on wait for the next one, and those in flight on it may finish
     * within the await termination, after which they are closed.
     *
     * @param generation The generation
     */
    void deactivate(Generation generation) {
        List<Generation> stopping = new ArrayList<>();
        synchronized (lock) {
            if (current == generation) {
                current = null;
            }
            stopping.addAll(replacedGenerations);
            replacedGenerations.clear();
        }
        stopping.add(generation);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !stopping.stream().allMatch(Generation::isIdle)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        for (Generation stopped : stopping) {
            stopped.retireCalls(timeout);
        }
    }

    /**
     * Shuts the server down: the generation running does not use it, and builds its own.
     */
    void release() {
        Server released;
        synchronized (lock) {
            released = server;
            server = null;
            started = false;
            current = null;
            lock.notifyAll();
        }
        shutdown(released);
    }

    /**
     * Called as a generation stops: a server no generation used while it ran is shut down, the application no longer
     * serving it.
     */
    void generationEnded() {
        boolean unclaimed;
        synchronized (lock) {
            unclaimed = !claimed && server != null;
            claimed = false;
        }
        if (unclaimed) {
            LOG.debug("No generation used the retained gRPC server: it is shut down");
            release();
        }
    }

    /**
     * Shuts the server down: a change released it, or the development runtime closes.
     */
    @PreDestroy
    void close() {
        ExecutorService hold;
        synchronized (lock) {
            closed = true;
            hold = holdExecutor;
            holdExecutor = null;
        }
        release();
        if (hold != null) {
            hold.shutdown();
        }
    }

    private void shutdown(@Nullable Server released) {
        if (released == null) {
            return;
        }
        released.shutdown();
        try {
            if (!released.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                released.shutdownNow();
            }
        } catch (InterruptedException e) {
            released.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private @Nullable Generation awaitGeneration() {
        Generation running = current;
        if (running != null) {
            return running;
        }
        // the stopping generation may wait for its calls for the whole await termination before the next one starts
        long deadline = System.nanoTime() + timeout.toNanos() + NEXT_GENERATION_START.toNanos();
        synchronized (lock) {
            while (current == null && !closed && server != null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return current;
        }
    }

    private Executor holdExecutor() {
        synchronized (lock) {
            ExecutorService hold = holdExecutor;
            if (hold == null || closed) {
                hold = Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "grpc-dev-hold-" + holdThreads.incrementAndGet());
                    thread.setDaemon(true);
                    // not the loader of the generation that happens to start the thread: it would keep it reachable
                    thread.setContextClassLoader(DevelopmentGrpcServer.class.getClassLoader());
                    return thread;
                });
                if (!closed) {
                    holdExecutor = hold;
                }
            }
            return hold;
        }
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
        thread.setContextClassLoader(DevelopmentGrpcServer.class.getClassLoader());
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * What a generation serves: its services, with its interceptors applied, and the executor its calls run on. Each
     * of its calls is tracked, so that it can be waited for, or closed, as the generation stops.
     */
    static final class Generation implements ServerInterceptor {
        private final Map<String, ServerServiceDefinition> served = new LinkedHashMap<>();
        private final Map<String, ServerMethodDefinition<?, ?>> methods = new LinkedHashMap<>();
        private final Executor executor;
        private final Set<TrackedCall<?, ?>> calls = ConcurrentHashMap.newKeySet();

        /**
         * @param services The services
         * @param interceptors The interceptors, in the order the server builder would have been given them
         * @param executor The executor
         */
        Generation(List<ServerServiceDefinition> services, List<ServerInterceptor> interceptors, Executor executor) {
            this.executor = executor;
            for (ServerServiceDefinition service : services) {
                // what the server applies to every call, applied to each service: the last interceptor runs first,
                // and the tracking before them all
                // a later service of the same name replaces an earlier one, as in the server builder
                served.put(service.getServiceDescriptor().getName(), ServerInterceptors.intercept(ServerInterceptors.intercept(service, interceptors), this));
            }
            for (ServerServiceDefinition service : served.values()) {
                for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
                    methods.put(method.getMethodDescriptor().getFullMethodName(), method);
                }
            }
        }

        @Override
        public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
            TrackedCall<Q, R> tracked = new TrackedCall<>(call, calls);
            calls.add(tracked);
            ServerCall.Listener<Q> listener;
            try {
                listener = next.startCall(tracked, headers);
            } catch (RuntimeException | Error e) {
                calls.remove(tracked);
                throw e;
            }
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
                @Override
                public void onComplete() {
                    calls.remove(tracked);
                    super.onComplete();
                }

                @Override
                public void onCancel() {
                    calls.remove(tracked);
                    super.onCancel();
                }
            };
        }

        /**
         * Whether no call of the generation is running.
         */
        boolean isIdle() {
            return calls.isEmpty();
        }

        /**
         * Closes the calls still running once the generation was waited for.
         */
        void retireCalls(Duration timeout) {
            List<TrackedCall<?, ?>> remaining = new ArrayList<>(calls);
            calls.clear();
            if (!remaining.isEmpty()) {
                LOG.debug("Closing {} gRPC call(s) of the stopping generation still running after {} ms", remaining.size(), timeout.toMillis());
            }
            for (TrackedCall<?, ?> call : remaining) {
                call.retire();
            }
        }
    }

    /**
     * A call of a generation: its methods are serialized, so that the generation can close it from the thread that
     * stops it, after which what the application does with it is ignored.
     *
     * @param <Q> The request type
     * @param <R> The response type
     */
    private static final class TrackedCall<Q, R> extends ForwardingServerCall.SimpleForwardingServerCall<Q, R> {
        private final Set<TrackedCall<?, ?>> calls;
        private boolean closed;

        TrackedCall(ServerCall<Q, R> delegate, Set<TrackedCall<?, ?>> calls) {
            super(delegate);
            this.calls = calls;
        }

        @Override
        public synchronized void request(int numMessages) {
            if (!closed) {
                super.request(numMessages);
            }
        }

        @Override
        public synchronized void sendHeaders(Metadata headers) {
            if (!closed) {
                super.sendHeaders(headers);
            }
        }

        @Override
        public synchronized void sendMessage(R message) {
            if (!closed) {
                super.sendMessage(message);
            }
        }

        @Override
        public synchronized void close(Status status, Metadata trailers) {
            if (!closed) {
                closed = true;
                calls.remove(this);
                super.close(status, trailers);
            }
        }

        @Override
        public synchronized void setMessageCompression(boolean enabled) {
            if (!closed) {
                super.setMessageCompression(enabled);
            }
        }

        @Override
        public synchronized void setCompression(String compressor) {
            if (!closed) {
                super.setCompression(compressor);
            }
        }

        synchronized void retire() {
            if (!closed) {
                closed = true;
                try {
                    super.close(Status.UNAVAILABLE.withDescription("The application restarted"), new Metadata());
                } catch (RuntimeException e) {
                    LOG.debug("Cannot close a gRPC call of the stopping generation", e);
                }
            }
        }
    }

    /**
     * Looks the methods up in the registry of the running generation, waiting for the next one while none runs.
     */
    private final class GenerationRegistry extends HandlerRegistry {
        @Override
        public List<ServerServiceDefinition> getServices() {
            Generation running = current;
            return running == null ? List.of() : List.copyOf(running.served.values());
        }

        @Override
        public @Nullable ServerMethodDefinition<?, ?> lookupMethod(String methodName, @Nullable String authority) {
            Generation running = awaitGeneration();
            if (running == null) {
                return unavailable(methodName);
            }
            return running.methods.get(methodName);
        }

        private ServerMethodDefinition<InputStream, InputStream> unavailable(String methodName) {
            MethodDescriptor<InputStream, InputStream> method = MethodDescriptor.newBuilder(RAW, RAW)
                .setFullMethodName(methodName)
                .setType(MethodDescriptor.MethodType.UNKNOWN)
                .build();
            return ServerMethodDefinition.create(method, (call, headers) -> {
                call.close(Status.UNAVAILABLE.withDescription("The application is not running"), new Metadata());
                return new ServerCall.Listener<>() { };
            });
        }
    }

    /**
     * Runs the tasks of the server on the executor of the running generation, or on a thread of this bean while none
     * runs, or once that generation's executor no longer accepts them.
     */
    private final class GenerationExecutor implements Executor {
        @Override
        public void execute(Runnable task) {
            Generation running = current;
            if (running != null) {
                try {
                    running.executor.execute(task);
                    return;
                } catch (RejectedExecutionException e) {
                    // the executor of a stopping generation
                }
            }
            holdExecutor().execute(task);
        }
    }
}
