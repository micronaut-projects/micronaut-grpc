# tag::imports[]
try:
    from io.grpc import Metadata, ServerCall, ServerCallHandler
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import Metadata, ServerCall, ServerCallHandler
# end::imports[]

INTERCEPTED: list[str] = []


# TODO(python): a Python class cannot implement `io.grpc.ServerInterceptor` (the stub generated for the generic
# `<ReqT, RespT> interceptCall(...)` method erases the type variables), so the interceptor is a plain Python object
# registered with the `OrderedServerInterceptor` of `ServerInterceptorFactory`.
# tag::clazz[]
class CustomInterceptor:

    def interceptCall(self, call: ServerCall, headers: Metadata, next: ServerCallHandler) -> ServerCall.Listener:
        # end::clazz[]
        INTERCEPTED.append(call.getMethodDescriptor().getFullMethodName())
        # tag::clazz[]
        return next.startCall(call, headers)
# end::clazz[]
