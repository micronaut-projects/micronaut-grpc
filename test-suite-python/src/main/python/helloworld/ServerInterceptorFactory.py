# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Factory
from micronaut.grpc.server.interceptor import OrderedServerInterceptor

from .CustomInterceptor import CustomInterceptor

try:
    from io.grpc import ServerInterceptor
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from grpc import ServerInterceptor
# end::imports[]
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="ServerInterceptorTest")
# tag::clazz[]
@Factory  # <1>
class ServerInterceptorFactory:

    @Bean  # <2>
    @Singleton
    def custom_server_interceptor(self) -> ServerInterceptor:
        return OrderedServerInterceptor(CustomInterceptor(), 10)  # <3>
# end::clazz[]
