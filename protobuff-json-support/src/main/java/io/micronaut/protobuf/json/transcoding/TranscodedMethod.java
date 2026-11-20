/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.protobuf.json.transcoding;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.MethodDescriptor;
import io.micronaut.core.annotation.Internal;

/**
 * One HTTP binding of a unary gRPC method: an HTTP method and path template, with the request and response body
 * selectors of its {@code google.api.HttpRule}.
 *
 * @param httpMethod The HTTP method name, upper case (also a custom verb)
 * @param template The compiled path template
 * @param body The request body selector: empty for no body, {@code *} for the whole request, or a field path
 * @param responseBody The response body selector: empty for the whole response, or a field name
 * @param grpcMethod The gRPC method descriptor, with {@link DynamicMessage} marshallers
 * @param protoMethod The protobuf method descriptor
 * @since 5.3.0
 */
@Internal
record TranscodedMethod(String httpMethod,
                        HttpRuleTemplate template,
                        String body,
                        String responseBody,
                        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
                        Descriptors.MethodDescriptor protoMethod) {
}
