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
package io.micronaut.protobuf;

import com.google.protobuf.Message;
import io.micronaut.core.annotation.UsedByGeneratedCode;

/**
 * Creates builders for one Protocol Buffers message type without reflection.
 *
 * <p>Implementations are generated at compile time by {@code micronaut-protobuff-processor} for every message class
 * produced by {@code protoc}, and are found through {@link ProtobufMessageBuilders}.</p>
 *
 * @param <T> The message type
 * @since 5.3.0
 */
@UsedByGeneratedCode
public interface ProtobufMessageFactory<T extends Message> {

    /**
     * @return The message type this factory creates builders for
     */
    Class<T> getMessageType();

    /**
     * @return A new builder, as returned by the message's static {@code newBuilder()} method
     */
    Message.Builder newBuilder();
}
