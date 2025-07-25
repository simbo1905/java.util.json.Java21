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

/// Provides APIs for parsing JSON text, creating `JsonValue`s, and
/// offering a mapping between a `JsonValue` and its corresponding Java Object.
///
/// ## Design
/// This API is designed so that JSON values are composed as Algebraic
/// Data Types (ADTs) defined by interfaces. Each JSON value is represented as a
/// sealed `JsonValue` _sum_ type, which can be
/// pattern-matched into one of the following _product_ types: `JsonObject`,
/// `JsonArray`, `JsonString`, `JsonNumber`, `JsonBoolean`,
/// `JsonNull`. These product types are defined as non-sealed interfaces that
/// allow flexibility in the implementation of the type. For example, `JsonArray`
/// is defined as follows:
/// ```java
/// public non-sealed interface JsonArray extends JsonValue
/// ```
///
/// This API relies on pattern matching to allow for the extraction of a
/// JSON Value in a _single and class safe expression_ as follows:
/// ```java
/// JsonValue doc = Json.parse(text);
/// if (doc instanceof JsonObject o && o.members() instanceof Map<String, JsonValue> members
///     && members.get("name") instanceof JsonString js && js.value() instanceof String name
///     && members.get("age") instanceof JsonNumber jn && jn.toNumber() instanceof long age) {
///         // can use both "name" and "age" from a single expression
/// }
/// ```
///
/// Both `JsonValue` instances and their underlying values are immutable.
///
/// ## Parsing
///
/// Parsing produces a `JsonValue` from JSON text and is done using either
/// {@link Json#parse(java.lang.String)} or {@link Json#parse(char[])}. A successful
/// parse indicates that the JSON text adheres to the
/// [JSON grammar](https://datatracker.ietf.org/doc/html/rfc8259).
/// The parsing APIs provided do not accept JSON text that contain JSON Objects
/// with duplicate names.
///
/// For the reference JDK implementation, `JsonValue`s created via parsing
/// procure their underlying values _lazily_.
///
/// ## Formatting
///
/// Formatting of a `JsonValue` is performed with either {@link
/// JsonValue#toString()} or {@link Json#toDisplayString(JsonValue, int)}.
/// These methods produce formatted String representations of a `JsonValue`.
/// The returned text adheres to the JSON grammar defined in RFC 8259.
/// `JsonValue.toString()` produces the most compact representation which does not
/// include extra whitespaces or line-breaks, preferable for network transaction
/// or storage. `Json.toDisplayString(JsonValue, int)` produces a text representation that
/// is human friendly, preferable for debugging or logging.
///
/// ---
///
/// ## Usage Notes from Unofficial Backport
///
/// ### Major Classes
///
/// - {@link Json} - Main entry point for parsing and converting JSON
/// - {@link JsonValue} - Base sealed interface for all JSON values
/// - {@link JsonObject} - Represents JSON objects (key-value pairs)
/// - {@link JsonArray} - Represents JSON arrays
/// - {@link JsonString} - Represents JSON strings
/// - {@link JsonNumber} - Represents JSON numbers
/// - {@link JsonBoolean} - Represents JSON booleans (true/false)
/// - {@link JsonNull} - Represents JSON null
/// - {@link JsonParseException} - Thrown when parsing invalid JSON
///
/// ### Simple Parsing Example
///
/// ```java
/// // Parse a JSON string
/// String jsonText = """
///     {
///         "name": "Alice",
///         "age": 30,
///         "active": true
///     }
///     """;
/// 
/// JsonValue value = Json.parse(jsonText);
/// JsonObject obj = (JsonObject) value;
/// 
/// // Access values
/// String name = ((JsonString) obj.members().get("name")).value();
/// int age = ((JsonNumber) obj.members().get("age")).toNumber().intValue();
/// boolean active = ((JsonBoolean) obj.members().get("active")).value();
/// ```
///
/// ### Record Mapping Example
///
/// The API works seamlessly with Java records for domain modeling:
///
/// ```java
/// // Define your domain model
/// record User(String name, String email, boolean active) {}
/// record Team(String teamName, List<User> members) {}
/// 
/// // Create domain objects
/// Team team = new Team("Engineering", List.of(
///     new User("Alice", "alice@example.com", true),
///     new User("Bob", "bob@example.com", false)
/// ));
/// 
/// // Convert to JSON using Java collections
/// JsonValue teamJson = Json.fromUntyped(Map.of(
///     "teamName", team.teamName(),
///     "members", team.members().stream()
///         .map(u -> Map.of(
///             "name", u.name(),
///             "email", u.email(),
///             "active", u.active()
///         ))
///         .toList()
/// ));
/// 
/// // Parse back to records
/// JsonObject parsed = (JsonObject) Json.parse(teamJson.toString());
/// Team reconstructed = new Team(
///     ((JsonString) parsed.members().get("teamName")).value(),
///     ((JsonArray) parsed.members().get("members")).values().stream()
///         .map(v -> {
///             JsonObject member = (JsonObject) v;
///             return new User(
///                 ((JsonString) member.members().get("name")).value(),
///                 ((JsonString) member.members().get("email")).value(),
///                 ((JsonBoolean) member.members().get("active")).value()
///             );
///         })
///         .toList()
/// );
/// ```
///
/// ### REST API Response Example
///
/// Build complex JSON structures programmatically:
///
/// ```java
/// // Build a typical REST API response
/// JsonObject response = JsonObject.of(Map.of(
///     "status", JsonString.of("success"),
///     "data", JsonObject.of(Map.of(
///         "user", JsonObject.of(Map.of(
///             "id", JsonNumber.of(12345),
///             "name", JsonString.of("John Doe"),
///             "roles", JsonArray.of(List.of(
///                 JsonString.of("admin"),
///                 JsonString.of("user")
///             ))
///         )),
///         "timestamp", JsonNumber.of(System.currentTimeMillis())
///     )),
///     "errors", JsonArray.of(List.of())
/// ));
/// 
/// // Pretty print the response
/// String formatted = Json.toDisplayString(response, 2);
/// ```
///
/// @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
///      Object Notation (JSON) Data Interchange Format
/// @since 99

package jdk.sandbox.java.util.json;

