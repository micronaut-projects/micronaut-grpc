package helloworld;

// tag::imports[]
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Requires(property = "spec.name", value = "ServerInterceptorTest")
// tag::clazz[]
@Singleton // <1>
public class CustomInterceptor implements ServerInterceptor, Ordered { // <2>
    // end::clazz[]

    static final List<String> INTERCEPTED = new CopyOnWriteArrayList<>();

    // tag::clazz[]
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        // end::clazz[]
        INTERCEPTED.add(call.getMethodDescriptor().getFullMethodName());
        // tag::clazz[]
        return next.startCall(call, headers);
    }

    @Override
    public int getOrder() {
        return 10; // <3>
    }
}
// end::clazz[]
