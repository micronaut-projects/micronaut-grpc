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
package io.micronaut.grpc.client.propagation;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

/**
 * Makes the Micronaut {@link PropagatedContext} of the thread that starts a client call current again in the call's
 * response callbacks ({@code onHeaders}, {@code onMessage}, {@code onClose} and {@code onReady}).
 *
 * <p>gRPC runs those callbacks on the channel's executor, so without this interceptor the MDC, tracing and other context
 * elements of the caller are lost in an async stub's {@code StreamObserver}. The interceptor does nothing when the
 * calling thread has no propagated context.</p>
 *
 * <p>Enabled by default; disable with {@code grpc.client.context-propagation.enabled=false}.</p>
 *
 * @since 5.3.0
 */
@Singleton
@Requires(property = GrpcClientContextPropagationInterceptor.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class GrpcClientContextPropagationInterceptor implements ClientInterceptor, Ordered {

    /**
     * The property that enables client context propagation.
     */
    public static final String ENABLED = "grpc.client.context-propagation.enabled";

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
        PropagatedContext context = PropagatedContext.find().orElse(null);
        if (context == null || context.isEmpty()) {
            return call;
        }
        return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new PropagatingListener<>(responseListener, context), headers);
            }
        };
    }

    /**
     * Runs first, so that the context is captured before other interceptors and restored around every callback they see.
     *
     * @return The order
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static final class PropagatingListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

        private final PropagatedContext context;

        PropagatingListener(ClientCall.Listener<RespT> delegate, PropagatedContext context) {
            super(delegate);
            this.context = context;
        }

        @Override
        public void onHeaders(Metadata headers) {
            try (PropagatedContext.Scope ignored = context.propagate()) {
                super.onHeaders(headers);
            }
        }

        @Override
        public void onMessage(RespT message) {
            try (PropagatedContext.Scope ignored = context.propagate()) {
                super.onMessage(message);
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            try (PropagatedContext.Scope ignored = context.propagate()) {
                super.onClose(status, trailers);
            }
        }

        @Override
        public void onReady() {
            try (PropagatedContext.Scope ignored = context.propagate()) {
                super.onReady();
            }
        }
    }
}
