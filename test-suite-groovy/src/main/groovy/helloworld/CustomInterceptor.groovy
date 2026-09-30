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
    <T, R> ServerCall.Listener<T> interceptCall(ServerCall<T, R> call,
                                                Metadata headers,
                                                ServerCallHandler<T, R> next) {
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
