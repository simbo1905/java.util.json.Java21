package json.java21.jsonpath;

import jdk.incubator.java.util.json.*;

/// Helpers for stream-based processing of `JsonPath.query(...)` results.
public final class JsonPathStreams {

    private JsonPathStreams() {}

    public static boolean isNumber(JsonValue v) {
        return v instanceof JsonNumber;
    }

    public static boolean isString(JsonValue v) {
        return v instanceof JsonString;
    }

    public static boolean isBoolean(JsonValue v) {
        return v instanceof JsonBoolean;
    }

    public static boolean isArray(JsonValue v) {
        return v instanceof JsonArray;
    }

    public static boolean isObject(JsonValue v) {
        return v instanceof JsonObject;
    }

    public static boolean isNull(JsonValue v) {
        return v instanceof JsonNull;
    }

    /// @throws ClassCastException if the value is not a `JsonNumber`
    public static double asDouble(JsonValue v) {
        if (v instanceof JsonNumber n) {
            return n.asDouble();
        }
        throw new ClassCastException("Expected JsonNumber but got " + v.getClass().getSimpleName());
    }

    /// @throws ClassCastException if the value is not a `JsonNumber`
    public static long asLong(JsonValue v) {
        if (v instanceof JsonNumber n) {
            return n.asLong();
        }
        throw new ClassCastException("Expected JsonNumber but got " + v.getClass().getSimpleName());
    }

    /// @throws ClassCastException if the value is not a `JsonString`
    public static String asString(JsonValue v) {
        if (v instanceof JsonString s) {
            return s.asString();
        }
        throw new ClassCastException("Expected JsonString but got " + v.getClass().getSimpleName());
    }

    /// @throws ClassCastException if the value is not a `JsonBoolean`
    public static boolean asBoolean(JsonValue v) {
        if (v instanceof JsonBoolean b) {
            return b.asBoolean();
        }
        throw new ClassCastException("Expected JsonBoolean but got " + v.getClass().getSimpleName());
    }

    public static Double asDoubleOrNull(JsonValue v) {
        return (v instanceof JsonNumber n) ? n.asDouble() : null;
    }

    public static Long asLongOrNull(JsonValue v) {
        return (v instanceof JsonNumber n) ? n.asLong() : null;
    }

    public static String asStringOrNull(JsonValue v) {
        return (v instanceof JsonString s) ? s.asString() : null;
    }

    public static Boolean asBooleanOrNull(JsonValue v) {
        return (v instanceof JsonBoolean b) ? b.asBoolean() : null;
    }
}
