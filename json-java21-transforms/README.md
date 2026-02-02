# JSON Transforms

A Java implementation of JSON document transforms based on the Microsoft JSON Document Transforms specification.

**Specification**: https://github.com/Microsoft/json-document-transforms/wiki

**Reference Implementation (C#)**: https://github.com/Microsoft/json-document-transforms

## Overview

JSON Transforms provides a declarative way to transform JSON documents using transform specifications. A transform specification is itself a JSON document that describes operations (rename, remove, replace, merge) to apply to a source document.

This implementation uses JsonPath queries (from `json-java21-jsonpath`) to select target nodes in the source document.

## Quick Start

```java
import jdk.sandbox.java.util.json.*;
import json.java21.transforms.JsonTransforms;

// Source document
JsonValue source = Json.parse("""
    {
        "name": "Alice",
        "age": 30,
        "city": "Seattle"
    }
    """);

// Transform specification
JsonValue transform = Json.parse("""
    {
        "name": {
            "@jdt.rename": "fullName"
        },
        "age": {
            "@jdt.remove": true
        },
        "country": {
            "@jdt.value": "USA"
        }
    }
    """);

// Parse and apply transform
JsonTransforms transformer = JsonTransforms.parse(transform);
JsonValue result = transformer.apply(source);

// Result:
// {
//     "fullName": "Alice",
//     "city": "Seattle",
//     "country": "USA"
// }
```

## Transform Operations

### @jdt.path

Specifies a JsonPath query to select which elements to transform. When used at the root of a transform, applies the transform to matching elements.

```json
{
    "@jdt.path": "$.users[*]",
    "status": {
        "@jdt.value": "active"
    }
}
```

### @jdt.value

Sets the value of a property. The value can be any JSON type (string, number, boolean, null, object, array).

```json
{
    "newProperty": {
        "@jdt.value": "hello"
    },
    "count": {
        "@jdt.value": 42
    }
}
```

### @jdt.remove

Removes a property from the document. Set to `true` to remove.

```json
{
    "obsoleteField": {
        "@jdt.remove": true
    }
}
```

### @jdt.rename

Renames a property to a new name.

```json
{
    "oldName": {
        "@jdt.rename": "newName"
    }
}
```

### @jdt.replace

Replaces a property value with a new value. Unlike `@jdt.value`, this only works if the property already exists.

```json
{
    "existingField": {
        "@jdt.replace": "new value"
    }
}
```

### @jdt.merge

Performs a deep merge of an object with the existing value.

```json
{
    "config": {
        "@jdt.merge": {
            "newSetting": true,
            "timeout": 5000
        }
    }
}
```

## Design

JSON Transforms follows the two-phase pattern used by other modules in this repository:

1. **Parse Phase**: The transform specification is parsed into an immutable AST (Abstract Syntax Tree) of transform operations. JsonPath expressions are pre-compiled for efficiency.

2. **Apply Phase**: The parsed transform is applied to source documents. The same parsed transform can be reused across multiple source documents.

### Architecture

- **Immutable Records**: All transform operations are represented as immutable records
- **Stack-based Evaluation**: Transforms are applied using a stack-based approach to avoid stack overflow on deeply nested documents
- **JsonPath Integration**: Uses the `json-java21-jsonpath` module for powerful node selection

## Building and Testing

```bash
# Build the module
./mvnw compile -pl json-java21-transforms -am

# Run tests
./mvnw test -pl json-java21-transforms -am

# Run with detailed logging
./mvnw test -pl json-java21-transforms -am -Djava.util.logging.ConsoleHandler.level=FINE
```

## API Reference

### JsonTransforms

Main entry point for parsing and applying transforms.

```java
// Parse a transform specification
JsonTransforms transform = JsonTransforms.parse(transformJson);

// Apply to a source document
JsonValue result = transform.apply(sourceJson);
```

### JsonTransformsAst

The AST (Abstract Syntax Tree) representation of transform operations. This is a sealed interface hierarchy:

- `TransformRoot` - Root of a transform specification
- `PathTransform` - Transform with JsonPath selector (`@jdt.path`)
- `ValueOp` - Set value operation (`@jdt.value`)
- `RemoveOp` - Remove operation (`@jdt.remove`)
- `RenameOp` - Rename operation (`@jdt.rename`)
- `ReplaceOp` - Replace operation (`@jdt.replace`)
- `MergeOp` - Merge operation (`@jdt.merge`)
- `NestedTransform` - Nested transform for object properties

### JsonTransformsParseException

Thrown when a transform specification is invalid.

## License

This project is part of the OpenJDK JSON API implementation and follows the same licensing terms.
