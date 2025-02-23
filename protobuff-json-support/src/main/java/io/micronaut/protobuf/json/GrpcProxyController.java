package io.micronaut.protobuf.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

@Controller("/api")
@Requires(bean = GrpcServiceRegistrar.class)
public class GrpcProxyController {
    private static final Logger log = getLogger(GrpcProxyController.class);

    private final ObjectMapper objectMapper;
    private final GrpcServiceRegistry registry;

    public GrpcProxyController(ObjectMapper objectMapper, GrpcServiceRegistry registry) {
        this.objectMapper = checkNotNull(objectMapper, "ObjectMapper must not be null");
        this.registry = checkNotNull(registry, "Registry must not be null");
        log.info("GrpcProxyController initialized.");
    }

    @Post("/{serviceName}/{methodName}")
    public HttpResponse<String> handlePost(String serviceName, String methodName, @Body String jsonBody) {

        log.debug("Received request for gRPC service: [{}], method: [{}]", serviceName, methodName);
        // Lookup the service
        var serviceDef = registry.getService(serviceName)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        Method method = serviceDef.methods.get(methodName.toLowerCase());
        if (method == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Method not found");
        }

        try {
            Class<?> requestType = method.getParameterTypes()[0];
            Object requestMessage = objectMapper.readValue(jsonBody, requestType);

            SimpleStreamObserver<?> observer = new SimpleStreamObserver<>();
            method.invoke(serviceDef.serviceBean, requestMessage, observer);

            Object responseMsg = observer.getResponse();
            String jsonResponse = objectMapper.writeValueAsString(responseMsg);
            return HttpResponse.ok(jsonResponse);
        } catch (Exception e) {
            String errorJson = null;
            try {
                errorJson = objectMapper.writeValueAsString(Map.of("error", e.getMessage()));
            } catch (JsonProcessingException ex) {
                //this will never happen unless e.getMessage() throws an exception
                log.error("Failed to serialize error response", ex);
            }
            return HttpResponse.serverError(errorJson);
        }
    }

    private static class SimpleStreamObserver<T> implements StreamObserver<T> {
        private T response;
        private Throwable error;

        @Override
        public void onNext(T value) {
            response = value;
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onCompleted() {
            // Nothing needed here
        }

        public T getResponse() {
            if (error != null) {
                throw new RuntimeException(error);
            }
            return response;
        }
    }

}
