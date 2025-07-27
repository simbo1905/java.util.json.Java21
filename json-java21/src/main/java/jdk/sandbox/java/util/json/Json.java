/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.sandbox.java.util.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.LinkedHashMap;

import jdk.sandbox.internal.util.json.JsonParser;
import jdk.sandbox.internal.util.json.Utils;

/// This class provides static methods for producing and manipulating a {@link JsonValue}.
/// 
/// {@link #parse(String)} and {@link #parse(char[])} produce a `JsonValue`
/// by parsing data adhering to the JSON syntax defined in RFC 8259.
/// 
/// {@link #toDisplayString(JsonValue, int)} is a formatter that produces a
/// representation of the JSON value suitable for display.
/// 
/// {@link #fromUntyped(Object)} and {@link #toUntyped(JsonValue)} provide a conversion
/// between `JsonValue` and an untyped object.
/// 
/// ## Example Usage
/// ```java
/// // Parse JSON string
/// JsonValue json = Json.parse("{\"name\":\"John\",\"age\":30}");
/// 
/// // Convert to standard Java types
/// Map<String, Object> data = (Map<String, Object>) Json.toUntyped(json);
/// 
/// // Create JSON from Java objects
/// JsonValue fromJava = Json.fromUntyped(Map.of("active", true, "score", 95));
/// ```
/// 
/// @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
///       Object Notation (JSON) Data Interchange Format
/// @since 99
public final class Json {

    /// Parses and creates a `JsonValue` from the given JSON document.
    /// If parsing succeeds, it guarantees that the input document conforms to
    /// the JSON syntax. If the document contains any JSON Object that has
    /// duplicate names, a `JsonParseException` is thrown.
    /// 
    /// `JsonValue`s created by this method produce their String and underlying
    /// value representation lazily.
    /// 
    /// `JsonObject`s preserve the order of their members declared in and parsed from
    /// the JSON document.
    /// 
    /// ## Example
    /// ```java
    /// JsonValue value = Json.parse("{\"name\":\"Alice\",\"active\":true}");
    /// if (value instanceof JsonObject obj) {
    ///     String name = ((JsonString) obj.members().get("name")).value();
    ///     boolean active = ((JsonBoolean) obj.members().get("active")).value();
    /// }
    /// ```
    ///
    /// @param in the input JSON document as `String`. Non-null.
    /// @throws JsonParseException if the input JSON document does not conform
    ///         to the JSON document format or a JSON object containing
    ///         duplicate names is encountered.
    /// @throws NullPointerException if `in` is `null`
    /// @return the parsed `JsonValue`
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return new JsonParser(in.toCharArray()).parseRoot();
    }

    /// Parses and creates a `JsonValue` from the given JSON document.
    /// If parsing succeeds, it guarantees that the input document conforms to
    /// the JSON syntax. If the document contains any JSON Object that has
    /// duplicate names, a `JsonParseException` is thrown.
    /// 
    /// `JsonValue`s created by this method produce their String and underlying
    /// value representation lazily.
    /// 
    /// `JsonObject`s preserve the order of their members declared in and parsed from
    /// the JSON document.
    ///
    /// @param in the input JSON document as `char[]`. Non-null.
    /// @throws JsonParseException if the input JSON document does not conform
    ///         to the JSON document format or a JSON object containing
    ///         duplicate names is encountered.
    /// @throws NullPointerException if `in` is `null`
    /// @return the parsed `JsonValue`
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        // Defensive copy on input. Ensure source is immutable.
        return new JsonParser(Arrays.copyOf(in, in.length)).parseRoot();
    }

    /// {@return a `JsonValue` created from the given `src` object}
    /// The mapping from an untyped `src` object to a `JsonValue`
    /// follows the table below.
    /// 
    /// | Untyped Object | JsonValue |
    /// |----------------|----------|
    /// | `List<Object>` | `JsonArray` |
    /// | `Boolean` | `JsonBoolean` |
    /// | `null` | `JsonNull` |
    /// | `Number*` | `JsonNumber` |
    /// | `Map<String, Object>` | `JsonObject` |
    /// | `String` | `JsonString` |
    ///
    /// *The supported `Number` subclasses are: `Byte`,
    /// `Short`, `Integer`, `Long`, `Float`,
    /// `Double`, `BigInteger`, and `BigDecimal`.
    ///
    /// If `src` is an instance of `JsonValue`, it is returned as is.
    /// 
    /// ## Example
    /// ```java
    /// // Convert Java collections to JSON
    /// JsonValue json = Json.fromUntyped(Map.of(
    ///     "user", Map.of(
    ///         "name", "Bob",
    ///         "age", 25
    ///     ),
    ///     "scores", List.of(85, 90, 78)
    /// ));
    /// ```
    ///
    /// @param src the data to produce the `JsonValue` from. May be null.
    /// @throws IllegalArgumentException if `src` cannot be converted
    ///         to a `JsonValue`.
    /// @see #toUntyped(JsonValue)
    public static JsonValue fromUntyped(Object src) {
        return switch (src) {
            // Structural: JSON object
            case Map<?, ?> map -> {
                Map<String, JsonValue> m = LinkedHashMap.newLinkedHashMap(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException(
                                "The key '%s' is not a String".formatted(entry.getKey()));
                    } else {
                        m.put(key, Json.fromUntyped(entry.getValue()));
                    }
                }
                // Bypasses defensive copy in JsonObject.of(m)
                yield Utils.objectOf(m);
            }
            // Structural: JSON Array
            case List<?> list -> {
                List<JsonValue> l = new ArrayList<>(list.size());
                for (Object o : list) {
                    l.add(Json.fromUntyped(o));
                }
                // Bypasses defensive copy in JsonArray.of(l)
                yield Utils.arrayOf(l);
            }
            // JSON primitives
            case String str -> JsonString.of(str);
            case Boolean bool -> JsonBoolean.of(bool);
            case Byte b -> JsonNumber.of(b);
            case Integer i -> JsonNumber.of(i);
            case Long l -> JsonNumber.of(l);
            case Short s -> JsonNumber.of(s);
            case Float f -> JsonNumber.of(f);
            case Double d -> JsonNumber.of(d);
            case BigInteger bi -> JsonNumber.of(bi);
            case BigDecimal bd -> JsonNumber.of(bd);
            case null -> JsonNull.of();
            // JsonValue
            case JsonValue jv -> jv;
            default -> throw new IllegalArgumentException(src.getClass().getSimpleName() + " is not a recognized type");
        };
    }

    /// {@return an `Object` created from the given `src` `JsonValue`} 
    /// The mapping from a `JsonValue` to an untyped `src` object follows the table below.
    /// 
    /// | JsonValue | Untyped Object |
    /// |-----------|----------------|
    /// | `JsonArray` | `List<Object>` (unmodifiable) |
    /// | `JsonBoolean` | `Boolean` |
    /// | `JsonNull` | `null` |
    /// | `JsonNumber` | `Number` |
    /// | `JsonObject` | `Map<String, Object>` (unmodifiable) |
    /// | `JsonString` | `String` |
    ///
    /// A `JsonObject` in `src` is converted to a `Map` whose
    /// entries occur in the same order as the `JsonObject`'s members.
    /// 
    /// ## Example
    /// ```java
    /// JsonValue json = Json.parse("{\"active\":true,\"count\":42}");
    /// Map<String, Object> data = (Map<String, Object>) Json.toUntyped(json);
    /// // data contains: {"active"=true, "count"=42L}
    /// ```
    ///
    /// @param src the `JsonValue` to convert to untyped. Non-null.
    /// @throws NullPointerException if `src` is `null`
    /// @see #fromUntyped(Object)
    public static Object toUntyped(JsonValue src) {
        Objects.requireNonNull(src);
        return switch (src) {
            case JsonObject jo -> jo.members().entrySet().stream()
                    .collect(LinkedHashMap::new, // Avoid Collectors.toMap, to allow `null` value
                            (m, e) -> m.put(e.getKey(), Json.toUntyped(e.getValue())),
                            HashMap::putAll);
            case JsonArray ja -> ja.values().stream()
                    .map(Json::toUntyped)
                    .toList();
            case JsonBoolean jb -> jb.value();
            case JsonNull ignored -> null;
            case JsonNumber n -> n.toNumber();
            case JsonString js -> js.value();
        };
    }

    /// {@return the String representation of the given `JsonValue` that conforms
    /// to the JSON syntax} As opposed to the compact output returned by {@link
    /// JsonValue#toString()}, this method returns a JSON string that is better
    /// suited for display.
    /// 
    /// ## Example
    /// ```java
    /// JsonValue json = Json.parse("{\"name\":\"Alice\",\"scores\":[85,90,95]}");
    /// System.out.println(Json.toDisplayString(json, 2));
    /// // Output:
    /// // {
    /// //   "name": "Alice",
    /// //   "scores": [
    /// //     85,
    /// //     90,
    /// //     95
    /// //   ]
    /// // }
    /// ```
    ///
    /// @param value the `JsonValue` to create the display string from. Non-null.
    /// @param indent the number of spaces used for the indentation. Zero or positive.
    /// @throws NullPointerException if `value` is `null`
    /// @throws IllegalArgumentException if `indent` is a negative number
    /// @see JsonValue#toString()
    public static String toDisplayString(JsonValue value, int indent) {
        Objects.requireNonNull(value);
        if (indent < 0) {
            throw new IllegalArgumentException("indent is negative");
        }
        return toDisplayString(value, 0, indent, false);
    }

    private static String toDisplayString(JsonValue jv, int col, int indent, boolean isField) {
        return switch (jv) {
            case JsonObject jo -> toDisplayString(jo, col, indent, isField);
            case JsonArray ja -> toDisplayString(ja, col, indent, isField);
            default -> " ".repeat(isField ? 1 : col) + jv;
        };
    }

    private static String toDisplayString(JsonObject jo, int col, int indent, boolean isField) {
        var prefix = " ".repeat(col);
        var s = new StringBuilder(isField ? " " : prefix);
        if (jo.members().isEmpty()) {
            s.append("{}");
        } else {
            s.append("{\n");
            jo.members().forEach((name, value) -> {
                if (value instanceof JsonValue val) {
                    s.append(prefix)
                            .append(" ".repeat(indent))
                            .append("\"")
                            .append(name)
                            .append("\":")
                            .append(Json.toDisplayString(val, col + indent, indent, true))
                            .append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            });
            s.setLength(s.length() - 2); // trim final comma
            s.append("\n").append(prefix).append("}");
        }
        return s.toString();
    }

    private static String toDisplayString(JsonArray ja, int col, int indent, boolean isField) {
        var prefix = " ".repeat(col);
        var s = new StringBuilder(isField ? " " : prefix);
        if (ja.values().isEmpty()) {
            s.append("[]");
        } else {
            s.append("[\n");
            for (JsonValue v: ja.values()) {
                if (v instanceof JsonValue jv) {
                    s.append(Json.toDisplayString(jv, col + indent, indent, false)).append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            }
            s.setLength(s.length() - 2); // trim final comma/newline
            s.append("\n").append(prefix).append("]");
        }
        return s.toString();
    }

    // no instantiation is allowed for this class
    private Json() {}
}
