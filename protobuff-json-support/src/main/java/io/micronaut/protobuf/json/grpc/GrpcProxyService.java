package io.micronaut.protobuf.json.grpc;

import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.protobuf.json.ProtobufJsonTranscoder;
import io.micronaut.protobuf.json.exception.GrpcInvocationException;
import io.micronaut.protobuf.json.exception.MethodNotFoundException;
import io.micronaut.protobuf.json.exception.ServiceNotFoundException;
import io.micronaut.protobuf.json.registry.GrpcServiceRegistry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Singleton
public class GrpcProxyService {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcProxyService.class);

    private final GrpcServiceRegistry registry;
    private final ApplicationContext context;
    private final ProtobufJsonTranscoder transcoder;

    public GrpcProxyService(GrpcServiceRegistry registry,
                            ApplicationContext context,
                            ProtobufJsonTranscoder transcoder) {
        this.registry = registry;
        this.context = context;
        this.transcoder = transcoder;
    }

    public String invokeGrpcMethod(String serviceName, String methodName, String jsonRequest) {
        // Obtain the executable method
        ExecutableMethod<?, ?> grpcMethod = registry.getExecutableMethod(serviceName, methodName)
            .orElseThrow(() -> {
                LOG.warn("Method '{}' not found in service '{}'", methodName, serviceName);
                return new MethodNotFoundException(methodName);
            });

        // Retrieve the bean instance from context
        Object beanInstance;
        try {
            beanInstance = context.getBean(grpcMethod.getDeclaringType());
        } catch (Exception e) {
            LOG.warn("Service '{}' not found via context", serviceName);
            throw new ServiceNotFoundException(serviceName);
        }

        // Transform JSON to Protobuf request
        @SuppressWarnings("unchecked") Class<? extends Message> requestType = (Class<? extends Message>) grpcMethod.getArguments()[0].getType();
        Message requestMessage = transcoder.fromJson(jsonRequest, requestType);

        // Invoke method and return Protobuf response
        final Message responseMessage;
        try {
            //noinspection unchecked
            responseMessage = invokeGrpcMethodReflectively(beanInstance, (ExecutableMethod<Object, Object>) grpcMethod, requestMessage);
        } catch (InterruptedException e) {
            throw new GrpcInvocationException("Failed to run grpc method: " + e.getMessage());
        }

        // Transform Protobuf response to JSON
        return transcoder.toJson(responseMessage);

    }

    // Isolated reflective invocation logic
    private Message invokeGrpcMethodReflectively(Object beanInstance,
                                                 ExecutableMethod<Object, Object> method,
                                                 Message request) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Message[] responseHolder = new Message[1];
        final Throwable[] errorHolder = new Throwable[1];

        method.invoke(beanInstance, request, new StreamObserver<Message>() {
            @Override
            public void onNext(Message message) {
                responseHolder[0] = message;
            }
            @Override
            public void onError(Throwable t) {
                errorHolder[0] = t;
                latch.countDown();
            }
            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new GrpcInvocationException("Timeout invoking gRPC method.");
        }

        if (errorHolder[0] != null) {
            throw new GrpcInvocationException("Error executing gRPC method: " + errorHolder[0].getMessage());
        }

        return responseHolder[0];
    }
}
