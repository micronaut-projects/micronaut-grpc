package helloworld;

// tag::imports[]
import io.grpc.ServerInterceptor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.grpc.server.interceptor.OrderedServerInterceptor;
import jakarta.inject.Singleton;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "ServerInterceptorTest")
// tag::clazz[]
@Factory // <1>
public class ServerInterceptorFactory {

    @Bean // <2>
    @Singleton
    public ServerInterceptor customServerInterceptor() {
        return new OrderedServerInterceptor(new CustomInterceptor(), 10); // <3>
    }
}
// end::clazz[]
