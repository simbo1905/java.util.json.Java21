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

/// Provides APIs for parsing JSON text, retrieving JSON values in the text, and
/// generating JSON text.
///
/// ## Parsing JSON documents
/// Parsing produces a `JsonValue` from JSON text via `Json.parse(String)` or
/// `Json.parse(char[])`. A successful parse indicates that the JSON text adheres
/// to the RFC 8259 grammar. The parsing APIs provided do not accept JSON text
/// that contains JSON objects with duplicate names.
///
/// ## Retrieving JSON values
/// Retrieving values from a JSON document involves two steps: first navigating
/// the document structure using access methods, and then converting the result
/// to the desired type using conversion methods. For example:
/// ```java
/// var name = doc.get("foo").get("bar").element(0).string();
/// ```
/// By chaining access methods, the "foo" member is retrieved from the root object,
/// then the "bar" member from "foo", followed by the element at index 0 from "bar".
/// The navigation process leads to a leaf JSON string element. The final call to
/// `string()` returns the corresponding `String` value. For more details on these
/// methods, see `JsonValue`.
///
/// ## Generating JSON documents
/// Generating JSON text is performed with either `JsonValue.toString()` or
/// `Json.toDisplayString(JsonValue, int)`. These methods produce formatted
/// string representations of a `JsonValue` that adhere to the JSON grammar
/// defined in RFC 8259. `JsonValue.toString()` produces the most compact
/// representation, while `Json.toDisplayString(JsonValue, int)` produces a
/// human-friendly, indented representation suitable for logging or debugging.
///
/// @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
///      Object Notation (JSON) Data Interchange Format
/// @since 99

package jdk.sandbox.java.util.json;
