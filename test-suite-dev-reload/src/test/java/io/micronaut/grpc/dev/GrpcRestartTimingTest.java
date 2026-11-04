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
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.micronaut.dev.tck.ReloadHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Restarts an application with a gRPC server several times while a client calls it without pause, and reports how
 * long each restart took, how long the client waited at most, and how many calls failed. Every call succeeds: the
 * server is retained, and the calls that arrive while one generation stops and the next starts wait for the next.
 */
class GrpcRestartTimingTest {

    private static final int RESTARTS = 5;

    @TempDir
    Path project;

    private static String answer(ManagedChannel channel) {
        try {
            return ClientCalls.blockingUnaryCall(channel, Echo.UNARY, CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), "x");
        } catch (StatusRuntimeException e) {
            return e.getStatus().toString();
        }
    }

    @Test
    void callsDuringRestartsWaitForTheNextGeneration() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            int port = GrpcReloadTest.properties(harness);
            harness.source("example.EchoService", GrpcReloadTest.SERVICE.formatted("v0"));
            harness.start();
            ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", port).usePlaintext().build();
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger calls = new AtomicInteger();
            List<String> failures = java.util.Collections.synchronizedList(new ArrayList<>());
            AtomicLong maxLatency = new AtomicLong();
            Thread client = new Thread(() -> {
                while (running.get()) {
                    long start = System.nanoTime();
                    try {
                        ClientCalls.blockingUnaryCall(channel, Echo.UNARY, CallOptions.DEFAULT.withDeadlineAfter(30, TimeUnit.SECONDS), "x");
                        calls.incrementAndGet();
                    } catch (StatusRuntimeException e) {
                        failures.add(e.getStatus().getCode() + ": " + e.getStatus().getDescription());
                    }
                    maxLatency.accumulateAndGet(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), Math::max);
                }
            }, "grpc-timing-client");
            client.start();
            try {
                long[] reloads = new long[RESTARTS];
                long[] firstAnswers = new long[RESTARTS];
                for (int i = 1; i <= RESTARTS; i++) {
                    harness.source("example.EchoService", GrpcReloadTest.SERVICE.formatted("v" + i));
                    long start = System.nanoTime();
                    harness.reload();
                    reloads[i - 1] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    String expected = "v" + i + " x";
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                    while (!expected.equals(answer(channel))) {
                        if (System.nanoTime() > deadline) {
                            throw new AssertionError("Generation " + (i + 1) + " never answered");
                        }
                        Thread.sleep(1);
                    }
                    firstAnswers[i - 1] = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                }
                running.set(false);
                client.join();
                System.out.printf("gRPC restarts: reload ms %s, first answer of the new generation ms %s, %d calls, max call latency %d ms, %d failure(s) %s%n",
                    java.util.Arrays.toString(reloads), java.util.Arrays.toString(firstAnswers), calls.get(), maxLatency.get(), failures.size(),
                    failures.stream().distinct().toList());
                assertEquals(List.of(), failures, "no call fails across the restarts");
            } finally {
                running.set(false);
                client.join();
                channel.shutdownNow();
            }
        }
    }
}
