package io.micronaut.protobuf.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.example.grpc.GreeterGrpc
/**
 * A factory class for creating and configuring a gRPC server instance.
 * This factory produces a {@link Server} bean that integrates with Micronaut's dependency injection.
 *
 * <p>The {@link GrpcServerFactory#grpcServer(GreeterGrpc.GreeterImplBase)} method creates a gRPC
 * server that listens on a random available port, and it registers the provided gRPC service implementation.</p>
 *
 * <p>To use this, ensure that you provide a {@link GreeterGrpc.GreeterImplBase} implementation,
 * which will be injected into the factory by Micronaut.</p>
 */
@Factory
class GrpcServerFactory {

    /**
     * Creates and starts a gRPC server instance.
     *
     * <p>This method configures the gRPC server to use a random available port (port 0)
     * and registers the provided {@code GreeterGrpc.GreeterImplBase} service implementation.</p>
     *
     * <p>The resulting {@link Server} instance is started before it is returned.</p>
     *
     * @param greeterService The gRPC service implementation to be registered with the server.
     * @return The started {@link Server} instance.
     */
    @Singleton
    static Server grpcServer(GreeterGrpc.GreeterImplBase greeterService) {
        def server = ServerBuilder.forPort(0) // Use port 0 to get random available port
                .addService(greeterService)
                .build()
        server.start()
        return server
    }
}
