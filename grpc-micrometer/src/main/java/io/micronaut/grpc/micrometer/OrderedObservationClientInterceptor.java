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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;

/**
 * Gives Micrometer's client observation interceptor an order, so that it runs ahead of interceptors with the default
 * order.
 *
 * @since 5.3.0
 */
@Internal
final class OrderedObservationClientInterceptor implements ClientInterceptor, Ordered {

    private final ClientInterceptor delegate;

    OrderedObservationClientInterceptor(ClientInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return delegate.interceptCall(method, callOptions, next);
    }

    @Override
    public int getOrder() {
        return GrpcObservationFactory.ORDER;
    }
}
