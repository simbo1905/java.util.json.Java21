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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.sandbox.internal.util.json.JsonArrayImpl;

/// The interface that represents JSON array.
/// 
/// A `JsonArray` can be produced by {@link Json#parse(String)}.
/// Alternatively, {@link #of(List)} can be used to obtain a `JsonArray`.
/// 
/// ## Example Usage
/// ```java
/// // Create from a List
/// JsonArray arr = JsonArray.of(List.of(
///     JsonString.of("first"),
///     JsonNumber.of(42),
///     JsonBoolean.of(true)
/// ));
/// 
/// // Access elements
/// for (JsonValue value : arr.elements()) {
///     switch (value) {
///         case JsonString s -> System.out.println("String: " + s.string());
///         case JsonNumber n -> System.out.println("Number: " + n.toLong());
///         case JsonBoolean b -> System.out.println("Boolean: " + b.bool());
///         default -> System.out.println("Other: " + value);
///     }
/// }
/// ```
///
/// @since 99
public non-sealed interface JsonArray extends JsonValue {

    /// {@return an unmodifiable list of the `JsonValue` elements in
    /// this `JsonArray`}
    List<JsonValue> elements();

    /// {@return the `JsonArray` created from the given
    /// list of `JsonValue`s}
    ///
    /// @param src the list of `JsonValue`s. Non-null.
    /// @throws NullPointerException if `src` is `null`, or contains
    ///         any values that are `null`
    static JsonArray of(List<? extends JsonValue> src) {
        // Careful not to use List::contains on src for null checking which
        // throws NPE for immutable lists
        return new JsonArrayImpl(src
                .stream()
                .map(Objects::requireNonNull)
                .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    /// {@return `true` if the given object is also a `JsonArray`
    /// and the two `JsonArray`s represent the same elements} Two
    /// `JsonArray`s `ja1` and `ja2` represent the same
    /// elements if `ja1.elements().equals(ja2.elements())`.
    ///
    /// @see #elements()
    @Override
    boolean equals(Object obj);

    /// {@return the hash code value for this `JsonArray`} The hash code value
    /// of a `JsonArray` is derived from the hash code of `JsonArray`'s
    /// {@link #elements()}.
    /// Thus, for two `JsonArray`s `ja1` and `ja2`,
    /// `ja1.equals(ja2)` implies that `ja1.hashCode() == ja2.hashCode()`
    /// as required by the general contract of {@link Object#hashCode}.
    ///
    /// @see #elements()
    @Override
    int hashCode();
}
