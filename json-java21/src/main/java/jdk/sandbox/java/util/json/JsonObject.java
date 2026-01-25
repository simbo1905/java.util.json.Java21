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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.sandbox.internal.util.json.JsonObjectImpl;

/// The interface that represents JSON object.
/// 
/// A `JsonObject` can be produced by a {@link Json#parse(String)}.
/// Alternatively, {@link #of(Map)} can be used to obtain a `JsonObject`.
/// Implementations of `JsonObject` cannot be created from sources that
/// contain duplicate member names. If duplicate names appear during
/// a {@link Json#parse(String)}, a `JsonParseException` is thrown.
/// 
/// ## Example Usage
/// ```java
/// // Create from a Map
/// JsonObject obj = JsonObject.of(Map.of(
///     "name", JsonString.of("Alice"),
///     "age", JsonNumber.of(30),
///     "active", JsonBoolean.of(true)
/// ));
/// 
/// // Access members
    /// JsonString name = (JsonString) obj.members().get("name");
    /// System.out.println(name.string()); // "Alice"
/// ```
///
/// @since 99
public non-sealed interface JsonObject extends JsonValue {

    /// {@return an unmodifiable map of the `String` to `JsonValue`
    /// members in this `JsonObject`}
    Map<String, JsonValue> members();

    /// {@return the `JsonObject` created from the given
    /// map of `String` to `JsonValue`s}
    ///
    /// The `JsonObject`'s members occur in the same order as the given
    /// map's entries.
    ///
    /// @param map the map of `JsonValue`s. Non-null.
    /// @throws NullPointerException if `map` is `null`, contains
    ///         any keys that are `null`, or contains any values that are `null`.
    static JsonObject of(Map<String, ? extends JsonValue> map) {
        return new JsonObjectImpl(map.entrySet() // Implicit NPE on map
                .stream()
                .collect(Collectors.toMap(
                        e -> Objects.requireNonNull(e.getKey()), Map.Entry::getValue, // Implicit NPE on val
                        (ignored, v) -> v, LinkedHashMap::new)));
    }

    /// {@return `true` if the given object is also a `JsonObject`
    /// and the two `JsonObject`s represent the same mappings} Two
    /// `JsonObject`s `jo1` and `jo2` represent the same
    /// mappings if `jo1.members().equals(jo2.members())`.
    ///
    /// @see #members()
    @Override
    boolean equals(Object obj);

    /// {@return the hash code value for this `JsonObject`} The hash code value
    /// of a `JsonObject` is derived from the hash code of `JsonObject`'s
    /// {@link #members()}. Thus, for two `JsonObject`s `jo1` and `jo2`,
    /// `jo1.equals(jo2)` implies that `jo1.hashCode() == jo2.hashCode()`
    /// as required by the general contract of {@link Object#hashCode}.
    ///
    /// @see #members()
    @Override
    int hashCode();
}
