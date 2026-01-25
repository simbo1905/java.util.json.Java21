/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.sandbox.internal.util.json.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The interface that represents a JSON value.
///
/// Instances of `JsonValue` are immutable and thread safe.
///
/// A `JsonValue` can be produced by {@link Json#parse(String)}.
///
/// @since 99
public sealed interface JsonValue
        permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /// {@return the String representation of this `JsonValue` that conforms
    /// to the JSON syntax} For a String representation suitable for display,
    /// use {@link Json#toDisplayString(JsonValue, int)}.
    ///
    /// @see Json#toDisplayString(JsonValue, int)
    String toString();

    /// {@return the `boolean` value represented by a `JsonBoolean`}
    default boolean bool() {
        throw Utils.composeTypeError(this, "JsonBoolean");
    }

    /// {@return this `JsonValue` as a `long`}
    default long toLong() {
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /// {@return this `JsonValue` as a `double`}
    default double toDouble() {
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /// {@return the `String` value represented by a `JsonString`}
    default String string() {
        throw Utils.composeTypeError(this, "JsonString");
    }

    /// {@return an `Optional` containing this `JsonValue` if it is not a
    /// `JsonNull`, otherwise an empty `Optional`}
    default Optional<JsonValue> valueOrNull() {
        return switch (this) {
            case JsonNull ignored -> Optional.empty();
            case JsonValue ignored -> Optional.of(this);
        };
    }

    /// {@return the {@link JsonArray#elements() elements} of a `JsonArray`}
    default List<JsonValue> elements() {
        throw Utils.composeTypeError(this, "JsonArray");
    }

    /// {@return the {@link JsonObject#members() members} of a `JsonObject`}
    default Map<String, JsonValue> members() {
        throw Utils.composeTypeError(this, "JsonObject");
    }

    /// {@return the `JsonValue` associated with the given member name of a `JsonObject`}
    ///
    /// @param name the member name
    /// @throws NullPointerException if the member name is `null`
    /// @throws JsonAssertionException if this `JsonValue` is not a `JsonObject` or
    ///         there is no association with the member name
    default JsonValue get(String name) {
        Objects.requireNonNull(name);
        return switch (members().get(name)) {
            case JsonValue jv -> jv;
            case null -> throw Utils.composeError(this,
                    "JsonObject member \"%s\" does not exist.".formatted(name));
        };
    }

    /// {@return an `Optional` containing the `JsonValue` associated with the given member
    /// name of a `JsonObject`, otherwise if there is no association an empty `Optional`}
    ///
    /// @param name the member name
    /// @throws NullPointerException if the member name is `null`
    /// @throws JsonAssertionException if this `JsonValue` is not a `JsonObject`
    default Optional<JsonValue> getOrAbsent(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(members().get(name));
    }

    /// {@return the `JsonValue` associated with the given index of a `JsonArray`}
    ///
    /// @param index the index of the array
    /// @throws JsonAssertionException if this `JsonValue` is not a `JsonArray`
    ///         or the given index is outside the bounds
    default JsonValue element(int index) {
        List<JsonValue> elements = elements();
        try {
            return elements.get(index);
        } catch (IndexOutOfBoundsException ex) {
            throw Utils.composeError(this,
                    "JsonArray index %d out of bounds for length %d."
                            .formatted(index, elements.size()));
        }
    }
}
