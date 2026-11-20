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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        Map<List<Descriptors.FieldDescriptor>, String> pathValues = pathValues(input, variables);
        Set<List<Descriptors.FieldDescriptor>> bound = new HashSet<>(pathValues.keySet());
        String bodySelector = method.body();
        JsonObject json;
        if (WHOLE_BODY.equals(bodySelector)) {
            json = wholeBody(body);
        } else {
            json = new JsonObject();
            if (!bodySelector.isEmpty()) {
                bound.add(bodyField(input, bodySelector, body, json));
            }
            queryParameters(input, queryParameters, bound, json);
        }
        for (Map.Entry<List<Descriptors.FieldDescriptor>, String> pathValue : pathValues.entrySet()) {
            set(json, pathValue.getKey(), new JsonPrimitive(pathValue.getValue()));
        }
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(input);
        parser.merge(json.toString(), builder);
        return builder.build();
    }

    private static Map<List<Descriptors.FieldDescriptor>, String> pathValues(Descriptors.Descriptor input,
                                                                              Map<String, String> variables) throws InvalidProtocolBufferException {
        Map<List<Descriptors.FieldDescriptor>, String> pathValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            List<Descriptors.FieldDescriptor> fields = canonicalPath(input, variable.getKey());
            if (fields == null) {
                throw new InvalidProtocolBufferException("Path variable '" + variable.getKey() + "' is not a field of " + input.getFullName());
            }
            pathValues.put(fields, variable.getValue());
        }
        return pathValues;
    }

    private static JsonObject wholeBody(@Nullable String body) throws InvalidProtocolBufferException {
        JsonElement parsed = parse(body);
        if (parsed == null) {
            return new JsonObject();
        }
        if (!parsed.isJsonObject()) {
            throw new InvalidProtocolBufferException("The request body must be a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    private static List<Descriptors.FieldDescriptor> bodyField(Descriptors.Descriptor input,
                                                               String bodySelector,
                                                               @Nullable String body,
                                                               JsonObject json) throws InvalidProtocolBufferException {
        List<Descriptors.FieldDescriptor> fields = canonicalPath(input, bodySelector);
        if (fields == null) {
            throw new InvalidProtocolBufferException("The body field '" + bodySelector + "' is not a field of " + input.getFullName());
        }
        JsonElement parsed = parse(body);
        if (parsed != null) {
            set(json, fields, parsed);
        }
        return fields;
    }

    private static void queryParameters(Descriptors.Descriptor input,
                                        Map<String, List<String>> queryParameters,
                                        Set<List<Descriptors.FieldDescriptor>> bound,
                                        JsonObject json) {
        for (Map.Entry<String, List<String>> parameter : queryParameters.entrySet()) {
            List<Descriptors.FieldDescriptor> fields = canonicalPath(input, parameter.getKey());
            List<String> values = parameter.getValue();
            if (fields == null || values.isEmpty() || isBound(fields, bound)) {
                continue;
            }
            // Several spellings of the same field set it once, the first one wins
            bound.add(fields);
            if (fields.get(fields.size() - 1).isRepeated()) {
                JsonArray array = new JsonArray();
                values.forEach(array::add);
                set(json, fields, array);
            } else {
                set(json, fields, new JsonPrimitive(values.get(0)));
            }
        }
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

    private static boolean isBound(List<Descriptors.FieldDescriptor> path, Set<List<Descriptors.FieldDescriptor>> bound) {
        for (List<Descriptors.FieldDescriptor> b : bound) {
            int common = Math.min(path.size(), b.size());
            if (path.subList(0, common).equals(b.subList(0, common))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a dotted field path against a message type, accepting proto and JSON field names.
     */
    static Descriptors.@Nullable FieldDescriptor resolve(Descriptors.Descriptor type, String path) {
        List<Descriptors.FieldDescriptor> fields = canonicalPath(type, path);
        return fields == null ? null : fields.get(fields.size() - 1);
    }

    /**
     * Resolves a dotted field path against a message type, accepting proto and JSON field names, to the descriptors
     * of the fields along the path, so that different spellings of the same field compare equal.
     */
    static @Nullable List<Descriptors.FieldDescriptor> canonicalPath(Descriptors.Descriptor type, String path) {
        Descriptors.Descriptor current = type;
        String[] parts = path.split("\\.");
        List<Descriptors.FieldDescriptor> fields = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            Descriptors.FieldDescriptor field = findField(current, parts[i]);
            if (field == null) {
                return null;
            }
            fields.add(field);
            if (i < parts.length - 1) {
                if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE || field.isRepeated()) {
                    return null;
                }
                current = field.getMessageType();
            }
        }
        return fields;
    }

    private static Descriptors.@Nullable FieldDescriptor findField(Descriptors.Descriptor type, String name) {
        Descriptors.FieldDescriptor field = type.findFieldByName(name);
        if (field != null) {
            return field;
        }
        for (Descriptors.FieldDescriptor candidate : type.getFields()) {
            if (candidate.getJsonName().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Sets a value under the proto field names of a path, replacing a value that the JSON body holds under the
     * JSON name of the same field.
     */
    private static void set(JsonObject root, List<Descriptors.FieldDescriptor> path, JsonElement value) {
        JsonObject current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            Descriptors.FieldDescriptor field = path.get(i);
            JsonElement child = take(current, field);
            JsonObject next = child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
            current.add(field.getName(), next);
            current = next;
        }
        Descriptors.FieldDescriptor field = path.get(path.size() - 1);
        take(current, field);
        current.add(field.getName(), value);
    }

    private static @Nullable JsonElement take(JsonObject object, Descriptors.FieldDescriptor field) {
        JsonElement byName = object.remove(field.getName());
        JsonElement byJsonName = field.getJsonName().equals(field.getName()) ? null : object.remove(field.getJsonName());
        return byName != null ? byName : byJsonName;
    }
}
