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
package io.micronaut.grpc.server.propagation;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Carries the Micronaut {@link PropagatedContext} that outer server interceptors make current when they start a call
 * into every later event of that call, so that the service method sees it.
 *
 * <p>gRPC delivers a call's events ({@code onMessage}, {@code onHalfClose}, {@code onCancel}, {@code onComplete} and
 * {@code onReady}) as separate tasks on the server executor, possibly on different threads. A unary service method
 * runs in {@code onHalfClose}, so a context an interceptor propagates around {@code next.startCall(..)}, for example
 * with {@code context.plus(element).propagate(() -> next.startCall(call, headers))}, would otherwise be lost before the
 * method runs.</p>
 *
 * <p>The interceptor runs last, closest to the service, so it captures the context of interceptors registered as beans.
 * Interceptors applied to a single service with {@code ServerInterceptors.intercept(..)} run inside it and are not
 * captured. The snapshot is only a fallback: an event that already runs inside a propagated context, for example one an
 * outer interceptor set around that event, keeps it. The interceptor does nothing when no context is propagated.
 * Enabled by default; disable with {@code grpc.server.context-propagation.enabled=false}.</p>
 *
 * @since 5.3.0
 */
@Singleton
@Requires(property = GrpcServerContextPropagationInterceptor.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class GrpcServerContextPropagationInterceptor implements ServerInterceptor, Ordered {

    /**
     * The property that enables server context propagation.
     */
    public static final String ENABLED = "grpc.server.context-propagation.enabled";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        @Nullable PropagatedContext context = PropagatedContext.find().orElse(null);
        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
        if (context == null || context.isEmpty()) {
            return listener;
        }
        return new PropagatingListener<>(listener, context);
    }

    /**
     * Runs last, so that the context set by every other interceptor is captured.
     *
     * @return The order
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Runs each event inside the context captured when the call started, unless a context is already current.
     *
     * @param <ReqT> The request type
     */
    private static final class PropagatingListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final PropagatedContext context;

        PropagatingListener(ServerCall.Listener<ReqT> delegate, PropagatedContext context) {
            super(delegate);
            this.context = context;
        }

        @Override
        public void onMessage(ReqT message) {
            run(() -> super.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            run(super::onHalfClose);
        }

        @Override
        public void onCancel() {
            run(super::onCancel);
        }

        @Override
        public void onComplete() {
            run(super::onComplete);
        }

        @Override
        public void onReady() {
            run(super::onReady);
        }

        private void run(Runnable event) {
            if (PropagatedContext.exists()) {
                // An outer interceptor made a context current for this event: it takes precedence over the startup snapshot.
                event.run();
            } else {
                // propagate(Runnable) also works in the scoped-value propagation mode, where Scope-based propagation is not supported.
                context.propagate(event);
            }
        }
    }
}
