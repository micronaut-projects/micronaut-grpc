/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.grpc.server.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.micronaut.core.order.Ordered;

/**
 * A {@link ServerInterceptor} implementation which allows ordering and simply passes all cals to a delegate
 *
 * @author Brian Wyka
 */
public class OrderedServerInterceptor implements ServerInterceptor, Ordered {

    private final ServerInterceptor delegate;
    private final int order;

    /**
     * Constructs an instance of this interceptor with the provided delegate inteceptor and order
     *
     * @param delegate the interceptor to delegate to
     * @param order the order number
     */
    public OrderedServerInterceptor(final ServerInterceptor delegate, final int order) {
        this.delegate = delegate;
        this.order = order;
    }

    /**
     * Delegates intercetor logic to {@link #delegate}
     *
     * {@inheritDoc}
     */
    @Override
    public <T, S> ServerCall.Listener<T> interceptCall(final ServerCall<T, S> call, final Metadata headers, final ServerCallHandler<T, S> next) {
        return delegate.interceptCall(call, headers, next);
    }

    /**
     * Get the order in which this interceptor should execute in the interceptor chain
     *
     * @return the order
     */
    @Override
    public int getOrder() {
        return order;
    }

}
