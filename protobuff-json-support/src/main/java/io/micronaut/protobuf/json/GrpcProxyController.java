/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.protobuf.json;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.protobuf.json.exception.*;
import io.micronaut.protobuf.json.grpc.GrpcProxyService;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

@Experimental
@Singleton
@Controller("/${micronaut.grpc.proxy.path:`grpc-json`}")
public final class GrpcProxyController {
    private static final Logger LOG = getLogger(GrpcProxyController.class);

    private final GrpcProxyService grpcProxyService;

    public GrpcProxyController(GrpcProxyService grpcProxyService) {
        this.grpcProxyService = checkNotNull(grpcProxyService, "GrpcProxyService cannot be null");
        LOG.info("GrpcProxyController initialized and services registered");
    }

    @Post("/{serviceName}/{methodName}")
    public HttpResponse<String> invokeMethod(@PathVariable String serviceName,
                                             @PathVariable String methodName,
                                             @Body String jsonRequest) {
        try {
            String jsonResponse = grpcProxyService.invokeGrpcMethod(serviceName, methodName, jsonRequest);
            return HttpResponse.ok(jsonResponse);
        } catch ( MalformedGrpcJsonException | GrpcInvocationException | ServiceNotFoundException | MethodNotFoundException e) {
            LOG.error("Requested service/method not found: {}/{}", serviceName, methodName, e);
            throw e; // Rethrow these for Micronaut's default exception handling
        } catch (Exception e) {
            LOG.error("Unexpected exception in gRPC service call {}.{}", serviceName, methodName, e);
            throw new GrpcInvocationException("Internal server error during method invocation");
        }
    }

    @Error(ServiceNotFoundException.class)
    public HttpResponse<String> handleServiceNotFound(ServiceNotFoundException e) {
        return HttpResponse.notFound("Service not found: " + e.getMessage());
    }

    @Error(MethodNotFoundException.class)
    public HttpResponse<String> handleMethodNotFound(MethodNotFoundException e) {
        return HttpResponse.notFound("Method not found: " + e.getMessage());
    }

    @Error(HttpStatusException.class)
    public HttpResponse<String> handleBadRequest(HttpStatusException e) {
        return HttpResponse.status(e.getStatus()).body(e.getMessage());
    }

    @Error(GrpcInvocationException.class)
    public HttpResponse<String> handleGrpcError(GrpcInvocationException e) {
        return HttpResponse.serverError("GRPC invocation error: " + e.getMessage());
    }

    @Error(HttpStatusException.class)
    public HttpResponse<String> handle(ProtobufTranscodingException e) {
        LOG.error("Problem transcoding JSON to Protobuf", e);
        return HttpResponse.badRequest();
    }

}
