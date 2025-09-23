package io.github.simbo1905.json.schema;

import jdk.sandbox.java.util.json.JsonArray;
import jdk.sandbox.java.util.json.JsonObject;
import jdk.sandbox.java.util.json.JsonString;
import jdk.sandbox.java.util.json.JsonValue;

import java.util.*;

/// Array schema with item validation and constraints
public record ArraySchema(
    JsonSchema items,
    Integer minItems,
    Integer maxItems,
    Boolean uniqueItems,
    // NEW: Pack 2 array features
    List<JsonSchema> prefixItems,
    JsonSchema contains,
    Integer minContains,
    Integer maxContains
) implements JsonSchema {

  @Override
  public ValidationResult validateAt(String path, JsonValue json, Deque<ValidationFrame> stack) {
    if (!(json instanceof JsonArray arr)) {
      return ValidationResult.failure(List.of(
          new ValidationError(path, "Expected array")
      ));
    }

    List<ValidationError> errors = new ArrayList<>();
    int itemCount = arr.values().size();

    // Check item count constraints
    if (minItems != null && itemCount < minItems) {
      errors.add(new ValidationError(path, "Too few items: expected at least " + minItems));
    }
    if (maxItems != null && itemCount > maxItems) {
      errors.add(new ValidationError(path, "Too many items: expected at most " + maxItems));
    }

    // Check uniqueness if required (structural equality)
    if (uniqueItems != null && uniqueItems) {
      Set<String> seen = new HashSet<>();
      for (JsonValue item : arr.values()) {
        String canonicalKey = canonicalize(item);
        if (!seen.add(canonicalKey)) {
          errors.add(new ValidationError(path, "Array items must be unique"));
          break;
        }
      }
    }

    // Validate prefixItems + items (tuple validation)
    if (prefixItems != null && !prefixItems.isEmpty()) {
      // Validate prefix items - fail if not enough items for all prefix positions
      for (int i = 0; i < prefixItems.size(); i++) {
        if (i >= itemCount) {
          errors.add(new ValidationError(path, "Array has too few items for prefixItems validation"));
          break;
        }
        String itemPath = path + "[" + i + "]";
        // Validate prefix items immediately to capture errors
        ValidationResult prefixResult = prefixItems.get(i).validateAt(itemPath, arr.values().get(i), stack);
        if (!prefixResult.valid()) {
          errors.addAll(prefixResult.errors());
        }
      }
      // Validate remaining items with items schema if present
      if (items != null && items != AnySchema.INSTANCE) {
        for (int i = prefixItems.size(); i < itemCount; i++) {
          String itemPath = path + "[" + i + "]";
          stack.push(new ValidationFrame(itemPath, items, arr.values().get(i)));
        }
      }
    } else if (items != null && items != AnySchema.INSTANCE) {
      // Original items validation (no prefixItems)
      int index = 0;
      for (JsonValue item : arr.values()) {
        String itemPath = path + "[" + index + "]";
        stack.push(new ValidationFrame(itemPath, items, item));
        index++;
      }
    }

    // Validate contains / minContains / maxContains
    if (contains != null) {
      int matchCount = 0;
      for (JsonValue item : arr.values()) {
        // Create isolated validation to check if item matches contains schema
        Deque<ValidationFrame> tempStack = new ArrayDeque<>();
        List<ValidationError> tempErrors = new ArrayList<>();
        tempStack.push(new ValidationFrame("", contains, item));

        while (!tempStack.isEmpty()) {
          ValidationFrame frame = tempStack.pop();
          ValidationResult result = frame.schema().validateAt(frame.path(), frame.json(), tempStack);
          if (!result.valid()) {
            tempErrors.addAll(result.errors());
          }
        }

        if (tempErrors.isEmpty()) {
          matchCount++;
        }
      }

      int min = (minContains != null ? minContains : 1); // default min=1
      int max = (maxContains != null ? maxContains : Integer.MAX_VALUE); // default max=∞

      if (matchCount < min) {
        errors.add(new ValidationError(path, "Array must contain at least " + min + " matching element(s)"));
      } else if (matchCount > max) {
        errors.add(new ValidationError(path, "Array must contain at most " + max + " matching element(s)"));
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  /// Canonicalization helper for structural equality in uniqueItems
  static String canonicalize(JsonValue v) {
    switch (v) {
      case JsonObject o -> {
        var keys = new ArrayList<>(o.members().keySet());
        Collections.sort(keys);
        var sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
          String k = keys.get(i);
          if (i > 0) sb.append(',');
          sb.append('"').append(escapeJsonString(k)).append("\":").append(canonicalize(o.members().get(k)));
        }
        return sb.append('}').toString();
      }
      case JsonArray a -> {
        var sb = new StringBuilder("[");
        for (int i = 0; i < a.values().size(); i++) {
          if (i > 0) sb.append(',');
          sb.append(canonicalize(a.values().get(i)));
        }
        return sb.append(']').toString();
      }
      case JsonString s -> {
        return "\"" + escapeJsonString(s.value()) + "\"";
      }
      case null, default -> {
        // numbers/booleans/null: rely on stable toString from the Json* impls
        assert v != null;
        return v.toString();
      }
    }
  }
  static String escapeJsonString(String s) {
    if (s == null) return "null";
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      switch (ch) {
        case '"':
          result.append("\\\"");
          break;
        case '\\':
          result.append("\\\\");
          break;
        case '\b':
          result.append("\\b");
          break;
        case '\f':
          result.append("\\f");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        default:
          if (ch < 0x20 || ch > 0x7e) {
            result.append("\\u").append(String.format("%04x", (int) ch));
          } else {
            result.append(ch);
          }
      }
    }
    return result.toString();
  }

}
