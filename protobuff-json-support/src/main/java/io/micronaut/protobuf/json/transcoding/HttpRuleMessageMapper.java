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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the gRPC request of a transcoded call from the path variables, query parameters and body of an HTTP
 * request, and renders the gRPC response as the HTTP response body, following the {@code google.api.HttpRule}
 * semantics.
 *
 * @since 5.3.0
 */
@Internal
final class HttpRuleMessageMapper {

    private static final String WHOLE_BODY = "*";

    private final JsonFormat.Parser parser = JsonFormat.parser();
    private final JsonFormat.Printer printer = JsonFormat.printer().omittingInsignificantWhitespace();
    private final JsonFormat.Printer printerWithDefaults = printer.alwaysPrintFieldsWithNoPresence();

    /**
     * Builds the request message.
     *
     * @param method The binding
     * @param variables The decoded path variable values by field path
     * @param queryParameters The query parameters
     * @param body The request body, or {@code null} if there is none
     * @return The request
     * @throws InvalidProtocolBufferException if the body or a parameter doesn't fit the request message
     */
    DynamicMessage request(TranscodedMethod method,
                           Map<String, String> variables,
                           Map<String, List<String>> queryParameters,
                           @Nullable String body) throws InvalidProtocolBufferException {
        Descriptors.Descriptor input = method.protoMethod().getInputType();
        JsonObject json = new JsonObject();
        Set<String> bound = new HashSet<>(variables.keySet());
        String bodySelector = method.body();
        if (WHOLE_BODY.equals(bodySelector)) {
            JsonElement parsed = parse(body);
            if (parsed != null) {
                if (!parsed.isJsonObject()) {
                    throw new InvalidProtocolBufferException("The request body must be a JSON object");
                }
                json = parsed.getAsJsonObject();
            }
        } else if (!bodySelector.isEmpty()) {
            JsonElement parsed = parse(body);
            if (parsed != null) {
                set(json, bodySelector, parsed);
            }
            bound.add(bodySelector);
        }
        if (!WHOLE_BODY.equals(bodySelector)) {
            for (Map.Entry<String, List<String>> parameter : queryParameters.entrySet()) {
                String path = parameter.getKey();
                Descriptors.FieldDescriptor field = resolve(input, path);
                if (field == null || isBound(path, bound) || parameter.getValue().isEmpty()) {
                    continue;
                }
                if (field.isRepeated()) {
                    JsonArray values = new JsonArray();
                    parameter.getValue().forEach(values::add);
                    set(json, path, values);
                } else {
                    set(json, path, new JsonPrimitive(parameter.getValue().get(0)));
                }
            }
        }
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            if (resolve(input, variable.getKey()) == null) {
                throw new InvalidProtocolBufferException("Path variable '" + variable.getKey() + "' is not a field of " + input.getFullName());
            }
            set(json, variable.getKey(), new JsonPrimitive(variable.getValue()));
        }
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(input);
        parser.merge(json.toString(), builder);
        return builder.build();
    }

    /**
     * Renders the response body.
     *
     * @param method The binding
     * @param response The gRPC response
     * @return The JSON body
     * @throws InvalidProtocolBufferException if the response can't be rendered
     */
    String response(TranscodedMethod method, DynamicMessage response) throws InvalidProtocolBufferException {
        String selector = method.responseBody();
        if (selector.isEmpty()) {
            return printer.print(response);
        }
        Descriptors.FieldDescriptor field = response.getDescriptorForType().findFieldByName(selector);
        if (field == null) {
            throw new InvalidProtocolBufferException("response_body '" + selector + "' is not a field of " + response.getDescriptorForType().getFullName());
        }
        JsonObject rendered = JsonParser.parseString(printerWithDefaults.print(response)).getAsJsonObject();
        JsonElement value = rendered.get(field.getJsonName());
        return value == null ? "null" : value.toString();
    }

    private static @Nullable JsonElement parse(@Nullable String body) throws InvalidProtocolBufferException {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException e) {
            throw new InvalidProtocolBufferException("The request body is not valid JSON: " + e.getMessage());
        }
    }

    private static boolean isBound(String path, Set<String> bound) {
        for (String b : bound) {
            if (path.equals(b) || path.startsWith(b + ".") || b.startsWith(path + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a dotted field path against a message type, accepting proto and JSON field names.
     */
    static Descriptors.@Nullable FieldDescriptor resolve(Descriptors.Descriptor type, String path) {
        Descriptors.Descriptor current = type;
        Descriptors.FieldDescriptor field = null;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (current == null) {
                return null;
            }
            field = current.findFieldByName(parts[i]);
            if (field == null) {
                for (Descriptors.FieldDescriptor candidate : current.getFields()) {
                    if (candidate.getJsonName().equals(parts[i])) {
                        field = candidate;
                        break;
                    }
                }
            }
            if (field == null) {
                return null;
            }
            boolean last = i == parts.length - 1;
            if (!last) {
                if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE || field.isRepeated()) {
                    return null;
                }
                current = field.getMessageType();
            }
        }
        return field;
    }

    private static void set(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement child = current.get(parts[i]);
            if (child == null || !child.isJsonObject()) {
                JsonObject created = new JsonObject();
                current.add(parts[i], created);
                current = created;
            } else {
                current = child.getAsJsonObject();
            }
        }
        current.add(parts[parts.length - 1], value);
    }
}
