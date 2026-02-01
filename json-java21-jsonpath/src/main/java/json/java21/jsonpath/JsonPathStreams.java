package json.java21.jsonpath;

import jdk.sandbox.java.util.json.*;

/// Utility class for stream-based processing of JsonPath query results.
///
/// This module intentionally does not embed aggregate functions (avg/sum/min/max)
/// in JsonPath syntax; use Java Streams on `JsonPath.query(...)` results instead.
public final class JsonPathStreams {

    private JsonPathStreams() {}

    // =================================================================================
    // Predicates
    // =================================================================================

    /// @return true if the value is a `JsonNumber`
    public static boolean isNumber(JsonValue v) {
        return v instanceof JsonNumber;
    }

    /// @return true if the value is a `JsonString`
    public static boolean isString(JsonValue v) {
        return v instanceof JsonString;
    }

    /// @return true if the value is a `JsonBoolean`
    public static boolean isBoolean(JsonValue v) {
        return v instanceof JsonBoolean;
    }

    /// @return true if the value is a `JsonArray`
    public static boolean isArray(JsonValue v) {
        return v instanceof JsonArray;
    }

    /// @return true if the value is a `JsonObject`
    public static boolean isObject(JsonValue v) {
        return v instanceof JsonObject;
    }

    /// @return true if the value is a `JsonNull`
    public static boolean isNull(JsonValue v) {
        return v instanceof JsonNull;
    }

    // =================================================================================
    // Strict Converters (throw ClassCastException if type mismatch)
    // =================================================================================

    /// Converts a `JsonNumber` to a `double`.
    ///
    /// @throws ClassCastException if the value is not a `JsonNumber`
    public static double asDouble(JsonValue v) {
        if (v instanceof JsonNumber n) {
            return n.toDouble();
        }
        throw new ClassCastException("Expected JsonNumber but got " + v.getClass().getSimpleName());
    }

    /// Converts a `JsonNumber` to a `long`.
    ///
    /// @throws ClassCastException if the value is not a `JsonNumber`
    public static long asLong(JsonValue v) {
        if (v instanceof JsonNumber n) {
            return n.toLong();
        }
        throw new ClassCastException("Expected JsonNumber but got " + v.getClass().getSimpleName());
    }

    /// Converts a `JsonString` to a `String`.
    ///
    /// @throws ClassCastException if the value is not a `JsonString`
    public static String asString(JsonValue v) {
        if (v instanceof JsonString s) {
            return s.string();
        }
        throw new ClassCastException("Expected JsonString but got " + v.getClass().getSimpleName());
    }

    /// Converts a `JsonBoolean` to a `boolean`.
    ///
    /// @throws ClassCastException if the value is not a `JsonBoolean`
    public static boolean asBoolean(JsonValue v) {
        if (v instanceof JsonBoolean b) {
            return b.bool();
        }
        throw new ClassCastException("Expected JsonBoolean but got " + v.getClass().getSimpleName());
    }

    // =================================================================================
    // Lax Converters (return null if type mismatch)
    // =================================================================================

    /// Converts to `Double` if the value is a `JsonNumber`, otherwise returns null.
    public static Double asDoubleOrNull(JsonValue v) {
        return (v instanceof JsonNumber n) ? n.toDouble() : null;
    }

    /// Converts to `Long` if the value is a `JsonNumber`, otherwise returns null.
    public static Long asLongOrNull(JsonValue v) {
        return (v instanceof JsonNumber n) ? n.toLong() : null;
    }

    /// Converts to `String` if the value is a `JsonString`, otherwise returns null.
    public static String asStringOrNull(JsonValue v) {
        return (v instanceof JsonString s) ? s.string() : null;
    }

    /// Converts to `Boolean` if the value is a `JsonBoolean`, otherwise returns null.
    public static Boolean asBooleanOrNull(JsonValue v) {
        return (v instanceof JsonBoolean b) ? b.bool() : null;
    }
}
