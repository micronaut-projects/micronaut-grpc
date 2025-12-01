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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NonNull;
import io.micronaut.http.HttpStatus;
import io.micronaut.protobuf.json.exception.MalformedGrpcJsonException;
import io.micronaut.protobuf.json.exception.ProtobufTranscodingException;
import jakarta.inject.Singleton;

/**
 * Utility class for handling Protobuf message serialization and deserialization to/from JSON.
 */
@Singleton
@Experimental
public class ProtobufJsonTranscoder {
    private final JsonFormat.Printer jsonPrinter;
    private final JsonFormat.Parser jsonParser;

    /**
     * Constructs a new instance of {@code ProtobufJsonTranscoder}.
     * <br/>
     * This constructor initializes the JSON printer and parser for handling
     * serialization and deserialization of Protobuf messages to and from JSON.
     * The JSON printer is configured to include default value fields in the
     * resulting JSON and to sort map keys for deterministic output.
     */
    public ProtobufJsonTranscoder() {
        this.jsonPrinter = JsonFormat.printer()
                .includingDefaultValueFields()
                .sortingMapKeys();
        this.jsonParser = JsonFormat.parser();
    }

    /**
     * Converts a Protobuf message to JSON string.
     *
     * @param message The Protobuf message to convert
     * @return JSON string representation
     * @throws ProtobufTranscodingException if serialization fails
     */
    @NonNull
    public String toJson(@NonNull Message message) {
        try {
            return jsonPrinter.print(message);
        } catch (Exception e) {
            throw new ProtobufTranscodingException("Failed to serialize Protobuf message to JSON", e);
        }
    }

    /**
     * Creates a Protobuf message from JSON string.
     *
     * @param jsonBody The JSON string to parse
     * @param messageType The Protobuf message class
     * @param <T> The type of Protobuf message
     * @return The constructed Protobuf message
     * @throws ProtobufTranscodingException if deserialization fails
     */
    @NonNull
    public <T extends Message> T fromJson(@NonNull String jsonBody, @NonNull Class<T> messageType) {
        try {
            Message.Builder builder = (Message.Builder) messageType
                    .getMethod("newBuilder")
                    .invoke(null);
            jsonParser.merge(jsonBody, builder);
            @SuppressWarnings("unchecked")
            T message = (T) builder.build();
            return message;
        } catch (InvalidProtocolBufferException ipbe) {
            throw new MalformedGrpcJsonException(HttpStatus.BAD_REQUEST,
                    String.format("Failed to deserialize JSON to %s.  " +
                            "JSON sent: " +
                            "[%s] ",
                        messageType.getSimpleName(), jsonBody));
        } catch (Exception e) {
            throw new ProtobufTranscodingException(
                    String.format("Failed to deserialize JSON to %s", messageType.getSimpleName()), e);
        }
    }
}
