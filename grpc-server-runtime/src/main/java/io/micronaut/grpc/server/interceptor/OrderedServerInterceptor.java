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
