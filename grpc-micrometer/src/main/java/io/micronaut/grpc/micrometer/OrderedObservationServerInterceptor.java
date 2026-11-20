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
package io.micronaut.grpc.micrometer;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;

/**
 * Gives Micrometer's server observation interceptor an order. micronaut-grpc sorts interceptor instances by
 * {@link Ordered}, so a plain Micrometer interceptor would run at the default order among the application's own.
 *
 * @since 5.3.0
 */
@Internal
final class OrderedObservationServerInterceptor implements ServerInterceptor, Ordered {

    private final ServerInterceptor delegate;

    OrderedObservationServerInterceptor(ServerInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }

    @Override
    public int getOrder() {
        return GrpcObservationFactory.ORDER;
    }
}
