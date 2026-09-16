package helloworld

// tag::imports[]
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
@Singleton // <1>
class CustomInterceptor : ServerInterceptor, Ordered { // <2>
    // end::clazz[]

    companion object {
        val INTERCEPTED: MutableList<String> = CopyOnWriteArrayList()
    }

    // tag::clazz[]
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        // end::clazz[]
        INTERCEPTED.add(call.methodDescriptor.fullMethodName)
        // tag::clazz[]
        return next.startCall(call, headers)
    }

    override fun getOrder(): Int {
        return 10 // <3>
    }
}
// end::clazz[]
