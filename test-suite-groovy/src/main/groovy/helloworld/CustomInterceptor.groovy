package helloworld

// tag::imports[]
import groovy.transform.CompileStatic
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton
// end::imports[]
import io.micronaut.context.annotation.Requires

import java.util.concurrent.CopyOnWriteArrayList

@Requires(property = "spec.name", value = "ServerInterceptorTest")
// tag::clazz[]
@CompileStatic
@Singleton // <1>
class CustomInterceptor implements ServerInterceptor, Ordered { // <2>
    // end::clazz[]

    static final List<String> INTERCEPTED = new CopyOnWriteArrayList<>()

    // tag::clazz[]
    @Override
    <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                          Metadata headers,
                                                          ServerCallHandler<ReqT, RespT> next) {
        // end::clazz[]
        INTERCEPTED.add(call.methodDescriptor.fullMethodName)
        // tag::clazz[]
        next.startCall(call, headers)
    }

    @Override
    int getOrder() {
        10 // <3>
    }
}
// end::clazz[]
