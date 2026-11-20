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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A compiled {@code google.api.HttpRule} path template, such as {@code /v1/{name=shelves/*}/books:archive}.
 *
 * <p>Grammar, from {@code google/api/http.proto}:</p>
 * <pre>
 * Template = "/" Segments [ Verb ] ;
 * Segments = Segment { "/" Segment } ;
 * Segment  = "*" | "**" | LITERAL | Variable ;
 * Variable = "{" FieldPath [ "=" Segments ] "}" ;
 * Verb     = ":" LITERAL ;
 * </pre>
 *
 * @since 5.3.0
 */
@Internal
final class HttpRuleTemplate {

    private final String template;
    private final Pattern pattern;
    private final List<String> variables;
    private final int literalLength;

    private HttpRuleTemplate(String template, Pattern pattern, List<String> variables, int literalLength) {
        this.template = template;
        this.pattern = pattern;
        this.variables = variables;
        this.literalLength = literalLength;
    }

    /**
     * Parses a path template.
     *
     * @param template The template
     * @return The compiled template
     * @throws IllegalArgumentException if the template is invalid
     */
    static HttpRuleTemplate parse(String template) {
        if (!template.startsWith("/")) {
            throw new IllegalArgumentException("HTTP rule template must start with '/': " + template);
        }
        String path = template;
        String verb = null;
        int verbIndex = verbIndex(template);
        if (verbIndex >= 0) {
            path = template.substring(0, verbIndex);
            verb = template.substring(verbIndex + 1);
            if (verb.isEmpty()) {
                throw new IllegalArgumentException("Empty verb in HTTP rule template: " + template);
            }
        }
        Builder builder = new Builder(template);
        StringBuilder regex = new StringBuilder("^/");
        regex.append(builder.segments(path.substring(1), false));
        if (verb != null) {
            regex.append(Pattern.quote(":" + verb));
            builder.literalLength += verb.length() + 1;
        }
        regex.append('$');
        return new HttpRuleTemplate(template, Pattern.compile(regex.toString()), List.copyOf(builder.variables), builder.literalLength);
    }

    /**
     * @return The template text
     */
    String template() {
        return template;
    }

    /**
     * @return The field paths bound by the template's variables, in order
     */
    List<String> variables() {
        return variables;
    }

    /**
     * @return The number of literal characters, used to prefer the more specific template when several match
     */
    int literalLength() {
        return literalLength;
    }

    /**
     * Matches a raw (still percent-encoded) request path.
     *
     * @param rawPath The raw path, without the query string
     * @return The decoded variable values by field path, or {@code null} if the path doesn't match
     */
    @Nullable Map<String, String> match(String rawPath) {
        Matcher matcher = pattern.matcher(rawPath);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            values.put(variables.get(i), percentDecode(matcher.group(i + 1)));
        }
        return values;
    }

    private static int verbIndex(String template) {
        int depth = 0;
        int lastSlash = -1;
        int colon = -1;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0 && c == '/') {
                lastSlash = i;
                colon = -1;
            } else if (depth == 0 && c == ':' && i > lastSlash) {
                colon = i;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced braces in HTTP rule template: " + template);
        }
        return colon;
    }

    static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(bytes, 0, bytes.length);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static final class Builder {
        private final String template;
        private final List<String> variables = new ArrayList<>();
        private int literalLength;

        Builder(String template) {
            this.template = template;
        }

        String segments(String segments, boolean insideVariable) {
            List<String> parts = split(segments);
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                String segment = parts.get(i);
                if (i > 0) {
                    regex.append('/');
                }
                if (segment.isEmpty()) {
                    throw new IllegalArgumentException("Empty segment in HTTP rule template: " + template);
                } else if (segment.equals("*")) {
                    regex.append("[^/]+");
                } else if (segment.equals("**")) {
                    if (i != parts.size() - 1) {
                        throw new IllegalArgumentException("'**' must be the last segment in HTTP rule template: " + template);
                    }
                    regex.append(".*");
                } else if (segment.startsWith("{")) {
                    if (insideVariable || !segment.endsWith("}")) {
                        throw new IllegalArgumentException("Invalid variable in HTTP rule template: " + template);
                    }
                    String content = segment.substring(1, segment.length() - 1);
                    int equals = content.indexOf('=');
                    String fieldPath = equals < 0 ? content : content.substring(0, equals);
                    String inner = equals < 0 ? "*" : content.substring(equals + 1);
                    if (fieldPath.isEmpty() || inner.isEmpty()) {
                        throw new IllegalArgumentException("Invalid variable in HTTP rule template: " + template);
                    }
                    variables.add(fieldPath);
                    regex.append('(').append(segments(inner, true)).append(')');
                } else {
                    if (segment.contains("{") || segment.contains("}")) {
                        throw new IllegalArgumentException("Invalid segment '" + segment + "' in HTTP rule template: " + template);
                    }
                    literalLength += segment.length() + 1;
                    regex.append(Pattern.quote(segment));
                }
            }
            return regex.toString();
        }

        private static List<String> split(String segments) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < segments.length(); i++) {
                char c = segments.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                } else if (c == '/' && depth == 0) {
                    parts.add(segments.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(segments.substring(start));
            return parts;
        }
    }
}
