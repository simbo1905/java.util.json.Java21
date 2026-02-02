# json-transforms

This module implements **json-transforms**: JSON-to-JSON transformations defined by a JSON “transform” document applied to a JSON “source” document.

This is based on Microsoft’s *json-document-transforms* specification:
- Wiki/spec: `https://github.com/Microsoft/json-document-transforms/wiki`
- Reference implementation (C#): `https://github.com/microsoft/json-document-transforms`

Important naming note:
- We refer to this technology as **json-transforms** in this repository (to avoid ambiguity with other unrelated acronyms).
- The specification’s on-the-wire syntax uses the literal keys `@jdt.*` (for example `@jdt.remove`), and this implementation follows that syntax for compatibility.

## Quick Start

This library has a **parse/run split** so transform parsing (including JSONPath compilation) can be reused across documents:

```java
import jdk.sandbox.java.util.json.Json;
import json.java21.transforms.JsonTransform;

var source = Json.parse("""
  { "Settings": { "A": 1, "B": 2 }, "Keep": true }
  """);

var transform = Json.parse("""
  {
    "Settings": { "@jdt.remove": "A" }
  }
  """);

var program = JsonTransform.parse(transform); // parse/compile once (reusable + thread-safe)
var result = program.run(source);             // run (immutable result)
```

## Supported Syntax (Spec)

This module follows the Microsoft wiki spec:
- **Default transform**: merge transform object into source object
- **Verbs** (object keys, case-sensitive):
  - `@jdt.remove`
  - `@jdt.replace`
  - `@jdt.merge`
  - `@jdt.rename`
- **Attributes** (inside a verb object, case-sensitive):
  - `@jdt.path` (JSONPath selector)
  - `@jdt.value` (the value to apply)

See the wiki pages for details and examples:
- `Default Transformation`
- `Transform Attributes`
- `Transform Verbs` (Remove/Replace/Merge/Rename)
- `Order of Execution`

