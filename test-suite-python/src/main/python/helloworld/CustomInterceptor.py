# tag::imports[]
from io.grpc import Metadata, ServerCall, ServerCallHandler, ServerInterceptor
from jakarta.inject import Singleton
from micronaut.core.order import Ordered
# end::imports[]
from micronaut.context.annotation import Requires

INTERCEPTED: list[str] = []


@Requires(property="spec.name", value="ServerInterceptorTest")
# tag::clazz[]
@Singleton  # <1>
class CustomInterceptor(ServerInterceptor, Ordered):  # <2>

    def interceptCall[ReqT, RespT](
        self,
        call: ServerCall[ReqT, RespT],
        headers: Metadata,
        next: ServerCallHandler[ReqT, RespT],
    ) -> ServerCall.Listener[ReqT]:
        # end::clazz[]
        INTERCEPTED.append(call.getMethodDescriptor().getFullMethodName())
        # tag::clazz[]
        return next.startCall(call, headers)

    def getOrder(self) -> int:
        return 10  # <3>
# end::clazz[]
