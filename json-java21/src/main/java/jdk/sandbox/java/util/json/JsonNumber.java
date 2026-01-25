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

import jdk.sandbox.internal.util.json.JsonNumberImpl;

/// The interface that represents JSON number, an arbitrary-precision
/// number represented in base 10 using decimal digits.
///
/// A `JsonNumber` can be produced by {@link Json#parse(String)}.
/// Alternatively, {@link #of(double)}, {@link #of(long)}, or {@link #of(String)}
/// can be used to obtain a `JsonNumber`.
/// The value of the `JsonNumber` can be retrieved as a `long`
/// with {@link #toLong()} or as a `double` with {@link #toDouble()}.
/// `toString()` returns the string representation of the JSON number.
///
/// @apiNote
/// To avoid precision loss when converting JSON numbers to Java types, or when
/// converting JSON numbers outside the range of `long` or `double`, use
/// `toString()` to create arbitrary-precision Java objects.
///
/// @spec https://datatracker.ietf.org/doc/html/rfc8259#section-6 RFC 8259:
///      The JavaScript Object Notation (JSON) Data Interchange Format - Numbers
/// @since 99
public non-sealed interface JsonNumber extends JsonValue {

    /// {@return a `long` if it can be translated from the string
    /// representation of this `JsonNumber`} The value must be a whole number
    /// and within the range of {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE}.
    ///
    /// @throws JsonAssertionException if this `JsonNumber` cannot
    ///         be represented as a `long`.
    @Override
    long toLong();

    /// {@return a finite `double` if it can be translated from the string
    /// representation of this `JsonNumber`}
    ///
    /// @throws JsonAssertionException if this `JsonNumber` cannot
    ///         be represented as a finite `double`.
    @Override
    double toDouble();

    /// Creates a JSON number from the given `double` value.
    /// The string representation of the JSON number created is produced by
    /// applying {@link Double#toString(double)} on `num`.
    ///
    /// @param num the given `double` value.
    /// @return a JSON number created from the `double` value
    /// @throws IllegalArgumentException if the given `double` value
    ///         is not a finite floating-point value.
    static JsonNumber of(double num) {
        if (!Double.isFinite(num)) {
            throw new IllegalArgumentException("Not a valid JSON number");
        }
        var str = Double.toString(num);
        return new JsonNumberImpl(str.toCharArray(), 0, str.length(), 0, 0);
    }

    /// Creates a JSON number from the given `long` value.
    /// The string representation of the JSON number created is produced by
    /// applying {@link Long#toString(long)} on `num`.
    ///
    /// @param num the given `long` value.
    /// @return a JSON number created from the `long` value
    static JsonNumber of(long num) {
        var str = Long.toString(num);
        return new JsonNumberImpl(str.toCharArray(), 0, str.length(), -1, -1);
    }

    /// Creates a JSON number from the given `String` value.
    /// The string representation of the JSON number created is equivalent to `num`.
    ///
    /// @param num the given `String` value.
    /// @return a JSON number created from the `String` value
    /// @throws IllegalArgumentException if `num` is not a valid string
    ///         representation of a JSON number.
    static JsonNumber of(String num) {
        try {
            if (Json.parse(num) instanceof JsonNumber jn) {
                return jn;
            }
        } catch (JsonParseException ex) {
            // fall through to error
        }
        throw new IllegalArgumentException("Not a JSON number");
    }

    /// {@return the string representation of this `JsonNumber`}
    ///
    /// If this `JsonNumber` is created by parsing a JSON number in a JSON document,
    /// it preserves the string representation in the document, regardless of its
    /// precision or range.
    @Override
    String toString();

    /// {@return true if the given `obj` is equal to this `JsonNumber`}
    /// The comparison is based on the string representation of this `JsonNumber`,
    /// ignoring the case.
    ///
    /// @see #toString()
    @Override
    boolean equals(Object obj);

    /// {@return the hash code value of this `JsonNumber`} The returned hash code
    /// is derived from the string representation of this `JsonNumber`,
    /// ignoring the case.
    ///
    /// @see #toString()
    @Override
    int hashCode();
}
