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
package io.micronaut.grpc.dev;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.grpc.server.GrpcEmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an application with a gRPC service, a server interceptor and a client of a {@code @GrpcChannel} through the
 * development runtime and restarts it. Development mode keeps the gRPC server, with its port, its connections and its
 * event loops, and the channel across the restart; the next generation's services and interceptors answer on them, and
 * nothing of the stopped generation stays reachable. A change under {@code grpc} releases what was kept, and an
 * interceptor of the application keeps the channel from being kept.
 */
class GrpcReloadTest {

    static final String SERVICE = """
        package example;

        import io.grpc.BindableService;
        import io.grpc.ServerServiceDefinition;
        import io.grpc.stub.ServerCalls;
        import io.grpc.stub.StreamObserver;
        import io.micronaut.grpc.dev.Echo;
        import jakarta.inject.Singleton;

        import java.util.List;
        import java.util.concurrent.CopyOnWriteArrayList;

        @Singleton
        public class EchoService implements BindableService {
            private final List<StreamObserver<String>> streams = new CopyOnWriteArrayList<>();

            @Override
            public ServerServiceDefinition bindService() {
                return ServerServiceDefinition.builder(Echo.SERVICE)
                    .addMethod(Echo.UNARY, ServerCalls.asyncUnaryCall((String request, StreamObserver<String> response) -> {
                        response.onNext("%s " + request);
                        response.onCompleted();
                    }))
                    .addMethod(Echo.STREAM, ServerCalls.asyncServerStreamingCall((String request, StreamObserver<String> response) -> {
                        // left open: the stream lasts until the client or the server ends it
                        response.onNext("%1$s " + request);
                        streams.add(response);
                    }))
                    .build();
            }

            public int streams() {
                return streams.size();
            }
        }
        """;

    private static final String SERVER_INTERCEPTOR = """
        package example;

        import io.grpc.Metadata;
        import io.grpc.ServerCall;
        import io.grpc.ServerCallHandler;
        import io.grpc.ServerInterceptor;
        import jakarta.inject.Singleton;

        import java.util.List;
        import java.util.concurrent.CopyOnWriteArrayList;

        @Singleton
        public class CountingInterceptor implements ServerInterceptor {
            private final List<String> received = new CopyOnWriteArrayList<>();

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                received.add("%s " + call.getMethodDescriptor().getBareMethodName());
                return next.startCall(call, headers);
            }

            public List<String> received() {
                return received;
            }
        }
        """;

    private static final String CLIENT = """
        package example;

        import io.grpc.CallOptions;
        import io.grpc.ManagedChannel;
        import io.grpc.stub.ClientCalls;
        import io.micronaut.grpc.annotation.GrpcChannel;
        import io.micronaut.grpc.dev.Echo;
        import jakarta.inject.Singleton;

        import java.util.concurrent.TimeUnit;

        @Singleton
        public class EchoClient {
            private final ManagedChannel channel;

            public EchoClient(@GrpcChannel("echo") ManagedChannel channel) {
                this.channel = channel;
            }

            public ManagedChannel channel() {
                return channel;
            }

            public String call(String request) {
                return "%s:" + ClientCalls.blockingUnaryCall(channel, Echo.UNARY, CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), request);
            }
        }
        """;

    private static final String CLIENT_INTERCEPTOR = """
        package example;

        import io.grpc.CallOptions;
        import io.grpc.Channel;
        import io.grpc.ClientCall;
        import io.grpc.ClientInterceptor;
        import io.grpc.MethodDescriptor;
        import jakarta.inject.Singleton;

        @Singleton
        public class TaggingInterceptor implements ClientInterceptor {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return next.newCall(method, callOptions);
            }
        }
        """;

    private static final String SERVER_HOLDER = "io.micronaut.grpc.server.DevelopmentGrpcServer";
    private static final String CHANNELS_HOLDER = "io.micronaut.grpc.channels.DevelopmentGrpcChannels";

    @TempDir
    Path project;

    private ManagedChannel channel;
    private int port;

    @BeforeEach
    void choosePort() {
        port = SocketUtils.findAvailableTcpPort();
    }

    @AfterEach
    void closeChannel() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    void aRestartKeepsTheServerAndTheChannelAndTheChangedServiceAnswersOnThem() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties(harness, port, Map.of());
            harness.source("example.EchoService", SERVICE.formatted("first"));
            harness.source("example.CountingInterceptor", SERVER_INTERCEPTOR.formatted("first"));
            harness.source("example.EchoClient", CLIENT.formatted("first"));
            harness.start();
            assertReloaderPresent(harness.context());

            assertEquals("first hello", call("hello"));
            assertEquals("first:first app", callThroughApplication(harness.context(), "app"));

            ApplicationContext first = harness.context();
            Server server = first.getBean(GrpcEmbeddedServer.class).getServer();
            Object serverHolder = first.getBean(type(first, SERVER_HOLDER));
            Object channelsHolder = first.getBean(type(first, CHANNELS_HOLDER));
            ManagedChannel applicationChannel = applicationChannel(first);
            List<String> firstReceived = received(first, "example.CountingInterceptor");
            assertEquals(List.of("first Unary", "first Unary"), firstReceived);
            first = null;

            harness.source("example.EchoService", SERVICE.formatted("second"));
            harness.source("example.CountingInterceptor", SERVER_INTERCEPTOR.formatted("second"));
            harness.source("example.EchoClient", CLIENT.formatted("second"));
            harness.reload();
            assertEquals(2, harness.generation());
            ApplicationContext second = harness.context();
            assertReloaderPresent(second);

            // the server, its port and its connections are those of the first generation
            ReloadTck.assertRetained(harness, serverHolder);
            ReloadTck.assertRetained(harness, channelsHolder);
            assertSame(server, second.getBean(GrpcEmbeddedServer.class).getServer());
            assertEquals(port, second.getBean(GrpcEmbeddedServer.class).getPort());
            assertFalse(server.isShutdown());

            // the changed service answers, through the interceptor of the new generation, on the same server
            assertEquals("second hello", call("hello"));
            // the channel is the same, and the client of the new generation calls through it
            assertSame(applicationChannel, applicationChannel(second));
            assertEquals("second:second app", callThroughApplication(second, "app"));
            assertFalse(applicationChannel.isShutdown());
            assertEquals(List.of("second Unary", "second Unary"), received(second, "example.CountingInterceptor"));
            assertEquals(List.of("first Unary", "first Unary"), firstReceived, "the retired interceptor intercepted nothing more");
            second = null;
            server = null;
            applicationChannel = null;
            serverHolder = null;
            channelsHolder = null;

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aChangeUnderGrpcReleasesTheServerAndTheChannel() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties(harness, port, Map.of());
            harness.source("example.EchoService", SERVICE.formatted("first"));
            harness.source("example.EchoClient", CLIENT.formatted("first"));
            harness.start();
            assertEquals("first:first app", callThroughApplication(harness.context(), "app"));
            Server[] firstServer = {harness.context().getBean(GrpcEmbeddedServer.class).getServer()};
            ManagedChannel[] firstChannel = {applicationChannel(harness.context())};

            // the configuration changes, together with a class, so the application restarts
            harness.resource("application.properties", propertiesText(port, Map.of(
                "grpc.server.max-inbound-message-size", "1048576",
                "grpc.channels.echo.max-inbound-message-size", "1048576")));
            harness.source("example.EchoService", SERVICE.formatted("second"));
            harness.source("example.EchoClient", CLIENT.formatted("second"));
            harness.reload();
            assertEquals(2, harness.generation());

            Server secondServer = harness.context().getBean(GrpcEmbeddedServer.class).getServer();
            assertNotSame(firstServer[0], secondServer);
            assertTrue(firstServer[0].isShutdown(), "the released server is shut down");
            assertNotSame(firstChannel[0], applicationChannel(harness.context()));
            assertTrue(firstChannel[0].isShutdown(), "the released channel is shut down");
            assertEquals(port, harness.context().getBean(GrpcEmbeddedServer.class).getPort());
            assertEquals("second hello", call("hello"));
            assertEquals("second:second app", callThroughApplication(harness.context(), "app"));
            firstServer[0] = null;
            firstChannel[0] = null;
            secondServer = null;

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void anInterceptorOfTheApplicationKeepsTheChannelFromBeingRetained() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties(harness, port, Map.of());
            harness.source("example.EchoService", SERVICE.formatted("first"));
            harness.source("example.EchoClient", CLIENT.formatted("first"));
            harness.source("example.TaggingInterceptor", CLIENT_INTERCEPTOR);
            harness.start();
            assertEquals("first:first app", callThroughApplication(harness.context(), "app"));
            Server server = harness.context().getBean(GrpcEmbeddedServer.class).getServer();
            ManagedChannel[] firstChannel = {applicationChannel(harness.context())};

            harness.source("example.EchoService", SERVICE.formatted("second"));
            harness.source("example.EchoClient", CLIENT.formatted("second"));
            harness.reload();
            assertEquals(2, harness.generation());

            // the channel ran the interceptor of the stopped generation: it is not kept, the server is
            assertNotSame(firstChannel[0], applicationChannel(harness.context()));
            assertTrue(firstChannel[0].isShutdown(), "the channel that was not kept is shut down");
            assertSame(server, harness.context().getBean(GrpcEmbeddedServer.class).getServer());
            assertEquals("second:second app", callThroughApplication(harness.context(), "app"));
            firstChannel[0] = null;
            server = null;

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aStreamOfTheStoppedGenerationIsClosedAndTheNextOneServesANewStream() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            // the stream keeps the stopping generation waiting this long
            properties(harness, port, Map.of("grpc.server.await-termination", "3s"));
            harness.source("example.EchoService", SERVICE.formatted("first"));
            harness.start();
            StreamRecorder stream = openStream();
            awaitTrue("the stream is open", () -> stream.messages.contains("first hello"));

            harness.source("example.EchoService", SERVICE.formatted("second"));
            // a call made while the stopping generation waits for the stream waits for the next generation
            CompletableFuture<String> during = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return call("hello");
            });
            harness.reload();
            assertEquals(2, harness.generation());

            // the stream the stopped generation served could not finish within the drain: it is closed, so that
            // nothing of that generation stays reachable, and the client may call again
            Status status = stream.closed.get(30, TimeUnit.SECONDS);
            assertEquals(Status.Code.UNAVAILABLE, status.getCode(), status.toString());
            assertEquals("second hello", call("hello"));
            assertEquals("second hello", during.get(30, TimeUnit.SECONDS));

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aServiceChangedInPlaceAnswersOnTheSameServer() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties(harness, port, Map.of());
            harness.source("example.EchoService", SERVICE.formatted("first"));
            harness.source("example.CountingInterceptor", SERVER_INTERCEPTOR.formatted("first"));
            harness.start();
            assertEquals("first hello", call("hello"));
            Object service = bean(harness.context(), "example.EchoService");
            Object interceptor = bean(harness.context(), "example.CountingInterceptor");
            Server server = harness.context().getBean(GrpcEmbeddedServer.class).getServer();
            // a stream the replaced service keeps open
            StreamRecorder replacedStream = openStream();
            awaitTrue("the stream is open", () -> replacedStream.messages.contains("first hello"));

            changedInPlace(harness, "example.EchoService");

            assertEquals(1, harness.generation(), "the application did not restart");
            assertNotSame(service, bean(harness.context(), "example.EchoService"), "the service bean is recreated");
            assertSame(interceptor, bean(harness.context(), "example.CountingInterceptor"), "an unchanged interceptor is kept");
            assertSame(server, harness.context().getBean(GrpcEmbeddedServer.class).getServer());
            assertEquals("first hello", call("hello"));
            // the recreated service is the one registered: a stream opened now is counted by it
            StreamRecorder stream = openStream();
            awaitTrue("the recreated service serves the stream", () -> streams(bean(harness.context(), "example.EchoService")) == 1);
            assertEquals(1, streams(service), "the replaced service serves its own stream only");
            assertFalse(replacedStream.closed.isDone(), "the stream of the replaced service goes on");
            service = null;

            changedInPlace(harness, "example.CountingInterceptor");
            assertNotSame(interceptor, bean(harness.context(), "example.CountingInterceptor"), "the interceptor bean is recreated");
            assertEquals("first hello", call("hello"));
            assertEquals(List.of("first Unary"), received(harness.context(), "example.CountingInterceptor"));

            // a restart closes the streams of the generation that stops, and of the services it replaced in place
            harness.source("example.EchoService", SERVICE.formatted("second"));
            harness.reload();
            assertEquals(2, harness.generation());
            assertEquals(Status.Code.UNAVAILABLE, replacedStream.closed.get(30, TimeUnit.SECONDS).getCode());
            assertEquals(Status.Code.UNAVAILABLE, stream.closed.get(30, TimeUnit.SECONDS).getCode());
            assertEquals("second hello", call("hello"));
            interceptor = null;
            server = null;

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private StreamRecorder openStream() {
        StreamRecorder stream = new StreamRecorder();
        ClientCall<String, String> call = channel().newCall(Echo.STREAM, CallOptions.DEFAULT);
        call.start(stream, new Metadata());
        call.sendMessage("hello");
        call.halfClose();
        call.request(Integer.MAX_VALUE);
        return stream;
    }

    private String call(String request) {
        return ClientCalls.blockingUnaryCall(channel(), Echo.UNARY, CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), request);
    }

    private ManagedChannel channel() {
        if (channel == null) {
            channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        }
        return channel;
    }

    /**
     * Writes the configuration of the application: the server on a free port, and a channel to it.
     *
     * @param harness The harness
     * @return The port
     */
    static int properties(ReloadHarness harness) {
        int port = SocketUtils.findAvailableTcpPort();
        properties(harness, port, Map.of());
        return port;
    }

    private static void properties(ReloadHarness harness, int port, Map<String, String> extra) {
        configuration(port, extra).forEach(harness::property);
    }

    private static Map<String, String> configuration(int port, Map<String, String> extra) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("grpc.server.port", String.valueOf(port));
        // a stream left open delays a restart by this much at most
        properties.put("grpc.server.await-termination", "1s");
        properties.put("grpc.channels.echo.target", "dns:///localhost:" + port);
        properties.put("grpc.channels.echo.plaintext", "true");
        properties.putAll(extra);
        return properties;
    }

    private static String propertiesText(int port, Map<String, String> extra) {
        StringBuilder text = new StringBuilder();
        configuration(port, extra).forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }

    /**
     * Tells the running generation that a class was redefined in place, as the development runtime does after it
     * redefined the class. The context is not kept: a reference to it would keep the generation reachable.
     */
    private static void changedInPlace(ReloadHarness harness, String className) {
        ApplicationContext context = harness.context();
        context.publishEvent(new ClassChangeEvent(GrpcReloadTest.class, Set.of(), context.getClassLoader(),
            List.of(new ClassChange(className, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
    }

    private static void assertReloaderPresent(ApplicationContext context) {
        // the bean that follows the changes exists in development mode only
        String reloader = "io.micronaut.grpc.server.DevelopmentGrpcReloader";
        assertTrue(context.containsBean(type(context, reloader)), reloader);
    }

    private static ManagedChannel applicationChannel(ApplicationContext context) {
        return (ManagedChannel) invoke(bean(context, "example.EchoClient"), "channel");
    }

    private static String callThroughApplication(ApplicationContext context, String request) {
        Object client = bean(context, "example.EchoClient");
        try {
            return (String) client.getClass().getMethod("call", String.class).invoke(client, request);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot call through " + client, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> received(ApplicationContext context, String className) {
        return (List<String>) invoke(bean(context, className), "received");
    }

    private static int streams(Object service) {
        return (Integer) invoke(service, "streams");
    }

    private static Object bean(ApplicationContext context, String className) {
        return context.getBean(type(context, className));
    }

    private static Object invoke(Object bean, String method) {
        try {
            return bean.getClass().getMethod(method).invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke " + method + " of " + bean, e);
        }
    }

    private static Class<?> type(ApplicationContext context, String className) {
        try {
            return Class.forName(className, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not on the classpath", e);
        }
    }

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            Thread.sleep(50);
        }
    }

    /**
     * Records what a stream receives and how it closes.
     */
    private static final class StreamRecorder extends ClientCall.Listener<String> {
        final List<String> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CompletableFuture<Status> closed = new CompletableFuture<>();

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            closed.complete(status);
        }
    }
}
