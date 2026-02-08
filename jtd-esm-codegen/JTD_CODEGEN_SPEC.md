# JTD Code Generation Specification

A language-independent specification for compiling RFC 8927 JSON Type Definition
schemas into target-language source code that validates JSON documents. The
generated code contains exactly the checks the schema requires -- no
interpreter, no AST, no runtime stack, no dead code.

## 1. Terminology

| Term | Meaning |
|---|---|
| **schema** | A JSON object conforming to RFC 8927. |
| **instance** | The JSON value being validated at runtime. |
| **form** | One of the 8 mutually-exclusive schema shapes defined in RFC 8927 plus the nullable modifier. |
| **AST node** | An immutable, tagged value representing one compiled schema form. Used during generation, discarded after. |
| **error** | A pair of JSON Pointers: `(instancePath, schemaPath)`. |
| **definitions** | A flat string-keyed map of named AST nodes, resolved at compile time. Each becomes a generated function. |

## 2. Overview

A JTD code generator operates in two phases:

1. **Parse**: Read the JTD schema JSON and compile it into an intermediate
   AST of immutable nodes (Section 3).
2. **Emit**: Walk the AST and emit target-language source code. Each AST
   node maps to a specific code pattern. The AST is discarded after
   emission (Section 5).

The generated code is a standalone validation function. When executed against
a JSON instance, it produces the same `(instancePath, schemaPath)` error
pairs that RFC 8927 Section 3.3 specifies.

## 3. Intermediate AST

The AST is used only during generation. It is not present in the output.

### 3.1 Node Types

```
Node =
  | Empty                                                -- {}
  | Ref        { name: String }                          -- {"ref": "..."}
  | Type       { type: TypeKeyword }                     -- {"type": "..."}
  | Enum       { values: List<String> }                  -- {"enum": [...]}
  | Elements   { schema: Node }                          -- {"elements": ...}
  | Properties { required:   Map<String, Node>,          -- {"properties": ...}
                 optional:   Map<String, Node>,           -- {"optionalProperties": ...}
                 additional: Boolean }                     -- {"additionalProperties": ...}
  | Values     { schema: Node }                          -- {"values": ...}
  | Discrim    { tag: String, mapping: Map<String,Node>} -- {"discriminator":...,"mapping":...}
  | Nullable   { inner: Node }                           -- any form + "nullable": true
```

`TypeKeyword` is one of the 12 strings defined in RFC 8927 Section 2.2.3:

```
TypeKeyword = boolean | string | timestamp
            | int8 | uint8 | int16 | uint16 | int32 | uint32
            | float32 | float64
```

### 3.2 Compilation Algorithm

```
compile(json, isRoot=true, definitions) -> Node:

  REQUIRE json is a JSON object

  IF isRoot:
    IF json has key "definitions":
      REQUIRE json["definitions"] is a JSON object
      -- Pass 1: register all keys as placeholders for forward refs
      FOR EACH key in json["definitions"]:
        definitions[key] = PLACEHOLDER
      -- Pass 2: compile each definition
      FOR EACH key in json["definitions"]:
        definitions[key] = compile(json["definitions"][key], isRoot=false, definitions)
  ELSE:
    REQUIRE json does NOT have key "definitions"

  -- Detect form
  forms = []
  IF json has "ref":           forms += "ref"
  IF json has "type":          forms += "type"
  IF json has "enum":          forms += "enum"
  IF json has "elements":      forms += "elements"
  IF json has "values":        forms += "values"
  IF json has "discriminator": forms += "discriminator"
  IF json has "properties" OR json has "optionalProperties":
                               forms += "properties"

  REQUIRE |forms| <= 1

  -- Compile form
  node = MATCH forms:
    []               -> Empty
    ["ref"]          -> compileRef(json, definitions)
    ["type"]         -> compileType(json)
    ["enum"]         -> compileEnum(json)
    ["elements"]     -> compileElements(json, definitions)
    ["properties"]   -> compileProperties(json, definitions)
    ["values"]       -> compileValues(json, definitions)
    ["discriminator"]-> compileDiscriminator(json, definitions)

  -- Nullable modifier wraps any form
  IF json has "nullable" AND json["nullable"] == true:
    node = Nullable { inner: node }

  RETURN node
```

### 3.3 Form-Specific Compilation

**Ref**:
```
compileRef(json, definitions):
  name = json["ref"]          -- must be a string
  REQUIRE name IN definitions  -- forward refs are valid (placeholder exists)
  RETURN Ref { name }
```

**Type**:
```
compileType(json):
  t = json["type"]            -- must be a string
  REQUIRE t IN TypeKeyword
  RETURN Type { type: t }
```

**Enum**:
```
compileEnum(json):
  values = json["enum"]       -- must be a non-empty array of strings
  REQUIRE no duplicates in values
  RETURN Enum { values }
```

**Elements**:
```
compileElements(json, definitions):
  inner = compile(json["elements"], isRoot=false, definitions)
  RETURN Elements { schema: inner }
```

**Properties**:
```
compileProperties(json, definitions):
  req = {}
  opt = {}
  IF json has "properties":
    FOR EACH (key, schema) in json["properties"]:
      req[key] = compile(schema, isRoot=false, definitions)
  IF json has "optionalProperties":
    FOR EACH (key, schema) in json["optionalProperties"]:
      opt[key] = compile(schema, isRoot=false, definitions)
  REQUIRE keys(req) INTERSECT keys(opt) == {}
  additional = json.get("additionalProperties", false)
  RETURN Properties { required: req, optional: opt, additional }
```

**Values**:
```
compileValues(json, definitions):
  inner = compile(json["values"], isRoot=false, definitions)
  RETURN Values { schema: inner }
```

**Discriminator**:
```
compileDiscriminator(json, definitions):
  tag = json["discriminator"]     -- must be a string
  REQUIRE json has "mapping"
  mapping = {}
  FOR EACH (key, schema) in json["mapping"]:
    node = compile(schema, isRoot=false, definitions)
    REQUIRE node is Properties      -- not Nullable, not any other form
    REQUIRE tag NOT IN node.required
    REQUIRE tag NOT IN node.optional
    mapping[key] = node
  RETURN Discrim { tag, mapping }
```

### 3.4 Compile-Time Invariants

After compilation, the following are guaranteed:
- Every `Ref.name` resolves to an entry in `definitions`.
- Every `Discrim.mapping` value is a `Properties` node (not nullable).
- No `Properties` node has overlapping required/optional keys.
- The AST is immutable. No node is modified after construction.

## 4. Type Checking Reference

Exact semantics for each `TypeKeyword`. The code generator emits exactly
this check, inlined, for each type keyword it encounters.

### 4.1 boolean

```
value is a JSON boolean (true or false)
```

Target-language expression examples:
- JavaScript: `typeof v === "boolean"`
- Java: `v instanceof JsonBoolean`
- Python: `isinstance(v, bool)`

### 4.2 string

```
value is a JSON string
```

Target-language expression examples:
- JavaScript: `typeof v === "string"`
- Java: `v instanceof JsonString`
- Python: `isinstance(v, str)`

### 4.3 timestamp

```
value is a JSON string
AND value matches the RFC 3339 date-time production
    (regex: ^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:(\d{2}|60)(\.\d+)?(Z|[+-]\d{2}:\d{2})$)
AND the date-time is parseable (accounting for leap seconds by
    normalizing :60 to :59 before parsing)
```

Target-language expression examples:
- JavaScript: `typeof v === "string" && !Number.isNaN(Date.parse(v))` (simplified;
  a full implementation needs the regex for leap-second support)
- Java: regex match + `OffsetDateTime.parse(normalized)`

### 4.4 float32, float64

```
value is a JSON number (any finite number; no range check)
```

RFC 8927 does not distinguish float32 from float64 at the validation level.
Both accept any JSON number.

Target-language expression examples:
- JavaScript: `typeof v === "number" && Number.isFinite(v)`
- Java: `v instanceof JsonNumber`

### 4.5 Integer types

All integer types share the same two-step check:

```
value is a JSON number
AND value has zero fractional part (floor(value) == value)
AND value is within the type's range (inclusive)
```

| Type | Min | Max |
|---|---|---|
| int8 | -128 | 127 |
| uint8 | 0 | 255 |
| int16 | -32768 | 32767 |
| uint16 | 0 | 65535 |
| int32 | -2147483648 | 2147483647 |
| uint32 | 0 | 4294967295 |

Note: `3.0` is a valid int8. `3.5` is not. This is value-based, not
syntax-based.

Target-language expression examples:
- JavaScript (uint8): `typeof v === "number" && Number.isInteger(v) && v >= 0 && v <= 255`
- Java (uint8): `v instanceof JsonNumber n && n.toDouble() == Math.floor(n.toDouble()) && n.toLong() >= 0 && n.toLong() <= 255`

## 5. Emission Rules

The code generator walks the AST and emits target-language source code.
Each AST node maps to a specific code pattern. The central rule:

**Emit only what the schema requires. If the schema does not mention a
form, the generated code does not contain any logic for that form.**

### 5.1 Generated Code Structure

The generator emits:

1. **One function per definition** -- named `validate_<defName>`, taking
   `(instance, errors, instancePath)` as parameters. Only emitted if the
   schema has definitions.

2. **One exported `validate(instance)` function** -- the entry point. Creates
   the error list, calls the root validation logic, returns the error list.

3. **No helpers, no libraries, no imports.** Every check is inlined. If the
   schema uses only `"type": "string"`, the generated code contains one
   `typeof` check and nothing else.

### 5.2 Node-to-Code Mapping

#### Empty

Emit nothing. No check. No code.

If an Empty node is a required property value, the generated code checks
that the key exists but does not validate the value:

```javascript
// Schema: {"properties": {"data": {}}}
if (!("data" in obj)) e.push({instancePath: p, schemaPath: sp + "/properties/data"});
// No else branch -- empty schema accepts any value
```

#### Nullable

Emit a null guard before the inner check:

```javascript
// Schema: {"type": "string", "nullable": true}
if (v !== null) {
  if (typeof v !== "string") e.push({instancePath: p, schemaPath: sp + "/type"});
}
```

If the inner node is Empty, the nullable wraps nothing -- emit only the
null guard (which passes everything, so emit nothing at all).

#### Type

Emit the type-specific check inlined. No helper function.

```javascript
// "type": "string"
if (typeof v !== "string") e.push({instancePath: p, schemaPath: sp + "/type"});

// "type": "uint8"
if (typeof v !== "number" || !Number.isInteger(v) || v < 0 || v > 255)
  e.push({instancePath: p, schemaPath: sp + "/type"});

// "type": "boolean"
if (typeof v !== "boolean") e.push({instancePath: p, schemaPath: sp + "/type"});

// "type": "float64"
if (typeof v !== "number" || !Number.isFinite(v))
  e.push({instancePath: p, schemaPath: sp + "/type"});
```

#### Enum

Emit a set-membership check. For small enums, inline the array. For large
enums, a code generator MAY hoist the array to module scope as a constant.

```javascript
// "enum": ["a", "b", "c"]
if (typeof v !== "string" || !["a","b","c"].includes(v))
  e.push({instancePath: p, schemaPath: sp + "/enum"});
```

Note: the string type guard is required because RFC 8927 specifies that
non-string values fail enum validation.

#### Elements

Emit an array type guard, then a loop. The loop body is the generated
check for the element schema.

```javascript
// "elements": {"type": "string"}
if (!Array.isArray(v)) {
  e.push({instancePath: p, schemaPath: sp});
} else {
  for (let i = 0; i < v.length; i++) {
    if (typeof v[i] !== "string")
      e.push({instancePath: p + "/" + i, schemaPath: sp + "/elements/type"});
  }
}
```

If the element schema is a complex type (Properties, Discrim), emit a
function call in the loop body instead of inlining.

For nested arrays (arrays of arrays), a code generator MAY inline nested
loops up to a configurable depth (e.g. 3 levels) for performance, falling
back to function calls beyond that depth.

#### Properties

Emit an object type guard, then:
1. One presence check per required key.
2. Inlined value checks for each required and optional property.
3. A key-rejection loop if `additional == false`.

```javascript
// Schema: {"properties":{"name":{"type":"string"}}, "optionalProperties":{"age":{"type":"uint8"}}}
if (v === null || typeof v !== "object" || Array.isArray(v)) {
  e.push({instancePath: p, schemaPath: sp});
} else {
  // Required properties
  if (!("name" in v)) e.push({instancePath: p, schemaPath: sp + "/properties/name"});
  else if (typeof v["name"] !== "string")
    e.push({instancePath: p + "/name", schemaPath: sp + "/properties/name/type"});

  // Optional properties
  if ("age" in v) {
    const a = v["age"];
    if (typeof a !== "number" || !Number.isInteger(a) || a < 0 || a > 255)
      e.push({instancePath: p + "/age", schemaPath: sp + "/optionalProperties/age/type"});
  }

  // Additional properties (only emitted when additional == false)
  for (const k in v) {
    if (k !== "name" && k !== "age")
      e.push({instancePath: p + "/" + k, schemaPath: sp});
  }
}
```

If `additional` is `true`, the for-in loop is **not emitted at all**.

If a property value's schema is a complex type (Properties, Elements, etc.),
emit a function call instead of inlining. If it is a leaf (Type, Enum,
Empty), inline it.

#### Values

Emit an object type guard, then a for-in loop. The loop body is the
generated check for the value schema.

```javascript
// "values": {"type": "string"}
if (v === null || typeof v !== "object" || Array.isArray(v)) {
  e.push({instancePath: p, schemaPath: sp});
} else {
  for (const k in v) {
    if (typeof v[k] !== "string")
      e.push({instancePath: p + "/" + k, schemaPath: sp + "/values/type"});
  }
}
```

#### Discriminator

Emit a 5-step sequential check, then a switch/if-else dispatching to the
variant validator.

```javascript
// "discriminator": "type", "mapping": {"a": {...}, "b": {...}}
if (v === null || typeof v !== "object" || Array.isArray(v)) {
  e.push({instancePath: p, schemaPath: sp});
} else if (!("type" in v)) {
  e.push({instancePath: p, schemaPath: sp});
} else if (typeof v["type"] !== "string") {
  e.push({instancePath: p + "/type", schemaPath: sp + "/discriminator"});
} else if (v["type"] === "a") {
  validate_variant_a(v, e, p, sp + "/mapping/a");
} else if (v["type"] === "b") {
  validate_variant_b(v, e, p, sp + "/mapping/b");
} else {
  e.push({instancePath: p + "/type", schemaPath: sp + "/mapping"});
}
```

Each variant validator is a generated Properties check. The discriminator
tag field is excluded from additional-properties checking and from
property validation in the variant (it was already validated by the
discriminator check).

#### Ref

Emit a function call to the generated definition validator:

```javascript
// "ref": "address"
validate_address(v, e, p, sp);
```

Each definition becomes a generated function. The function body is the
emitted code for the definition's AST node.

### 5.3 Inlining Policy

A code generator SHOULD inline checks for leaf nodes (Type, Enum, Empty)
directly into their parent's generated code.

A code generator SHOULD emit separate functions for:
- Each definition (called via Ref).
- Each Properties or Discrim node that appears as the child of Elements,
  Values, or other container nodes.
- Each discriminator variant.

A code generator MUST NOT emit helper functions, type-checking utilities,
or library imports that are not required by the specific schema being
compiled.

### 5.4 Recursive Schemas

Recursive refs (a definition that ultimately references itself) are legal
in RFC 8927. In generated code, this becomes recursive function calls:

```javascript
// Schema: {"definitions":{"node":{"properties":{"next":{"ref":"node","nullable":true}}}},
//          "ref":"node"}
function validate_node(v, e, p, sp) {
  if (v === null || typeof v !== "object" || Array.isArray(v)) {
    e.push({instancePath: p, schemaPath: sp});
    return;
  }
  if (!("next" in v)) {
    e.push({instancePath: p, schemaPath: sp + "/properties/next"});
  } else if (v["next"] !== null) {
    validate_node(v["next"], e, p + "/next", sp + "/properties/next");
  }
}

export function validate(instance) {
  const e = [];
  validate_node(instance, e, "", "");
  return e;
}
```

The target-language call stack provides the implicit work stack. For most
real-world schemas, recursion depth is bounded by the document's structure.

### 5.5 Discriminator Tag Exemption

When emitting a variant Properties check inside a discriminator, the
code generator MUST:
- Exclude the tag field from additional-properties rejection.
- Not emit a value check for the tag field (it was already validated
  as a string by the discriminator check).

This means the generated known-key set in the for-in loop includes the
tag field name, and no property check is emitted for it.

## 6. Error Format

Errors follow RFC 8927 Section 3.3, which defines error indicators as
pairs of JSON Pointers:

```
Error = {
  instancePath: String,   -- JSON Pointer (RFC 6901) into the instance
  schemaPath:   String    -- JSON Pointer (RFC 6901) into the schema
}
```

The `instancePath` points to the value that failed. The `schemaPath` points
to the schema keyword that caused the failure.

### 6.1 Schema Path Construction

The schema path is built at generation time and baked into the generated
code as string literals. Each emission rule appends to the schema path:

| Form | Appended path component(s) |
|---|---|
| Type | `/type` |
| Enum | `/enum` |
| Elements (type guard) | (nothing -- error at current path) |
| Elements (child) | `/elements` |
| Properties (missing key) | `/properties/<key>` |
| Properties (additional) | (nothing -- error at current path) |
| Properties (child req) | `/properties/<key>` |
| Properties (child opt) | `/optionalProperties/<key>` |
| Values (type guard) | (nothing -- error at current path) |
| Values (child) | `/values` |
| Discrim (not object) | (nothing -- error at current path) |
| Discrim (tag missing) | (nothing -- error at current path) |
| Discrim (tag not string) | `/discriminator` |
| Discrim (tag not in map) | `/mapping` |
| Discrim (variant) | `/mapping/<tagValue>` |

Schema paths are string literals in the generated code. They do not change
at runtime.

### 6.2 Instance Path Construction

Instance paths are built at runtime via string concatenation:

| Descent into | Appended to instancePath |
|---|---|
| Array element at index `i` | `"/" + i` |
| Object property with key `k` | `"/" + k` |
| Discriminator tag value | `"/" + tagFieldName` |
| Discriminator variant | (nothing -- same object) |
| Ref target | (nothing -- transparent) |

## 7. Conformance

Generated code conforms to this spec if:

1. For any valid RFC 8927 schema and any JSON instance, the generated
   `validate(instance)` function returns the same set of
   `(instancePath, schemaPath)` error pairs that RFC 8927 Section 3.3
   specifies.

2. The generated code passes the official JTD validation test suite
   (`validation.json` from `json-typedef-spec`) when used as the
   validation engine.

3. The code generator rejects invalid schemas at generation time per the
   constraints in Section 3.4.

4. The generated code contains no dead code: no helper functions, loops,
   branches, or checks that the schema does not require.

5. Validation does not short-circuit. All errors are collected in a
   single pass.

## 8. Worked Example

Schema:
```json
{
  "properties": {
    "name": { "type": "string" },
    "age":  { "type": "uint8" },
    "tags": { "elements": { "type": "string" } }
  },
  "optionalProperties": {
    "email": { "type": "string" }
  }
}
```

### Compiled AST (intermediate, discarded after emission)

```
Properties {
  required: {
    "name" -> Type { type: "string" },
    "age"  -> Type { type: "uint8" },
    "tags" -> Elements { schema: Type { type: "string" } }
  },
  optional: {
    "email" -> Type { type: "string" }
  },
  additional: false
}
```

### Generated Code (JavaScript ES2020)

```javascript
export function validate(instance) {
  const e = [];
  if (instance === null || typeof instance !== "object" || Array.isArray(instance)) {
    e.push({instancePath: "", schemaPath: ""});
    return e;
  }

  if (!("name" in instance)) e.push({instancePath: "", schemaPath: "/properties/name"});
  else if (typeof instance["name"] !== "string")
    e.push({instancePath: "/name", schemaPath: "/properties/name/type"});

  if (!("age" in instance)) e.push({instancePath: "", schemaPath: "/properties/age"});
  else {
    const v = instance["age"];
    if (typeof v !== "number" || !Number.isInteger(v) || v < 0 || v > 255)
      e.push({instancePath: "/age", schemaPath: "/properties/age/type"});
  }

  if (!("tags" in instance)) e.push({instancePath: "", schemaPath: "/properties/tags"});
  else if (!Array.isArray(instance["tags"]))
    e.push({instancePath: "/tags", schemaPath: "/properties/tags"});
  else {
    const arr = instance["tags"];
    for (let i = 0; i < arr.length; i++) {
      if (typeof arr[i] !== "string")
        e.push({instancePath: "/tags/" + i, schemaPath: "/properties/tags/elements/type"});
    }
  }

  if ("email" in instance && typeof instance["email"] !== "string")
    e.push({instancePath: "/email", schemaPath: "/optionalProperties/email/type"});

  for (const k in instance) {
    if (k !== "name" && k !== "age" && k !== "tags" && k !== "email")
      e.push({instancePath: "/" + k, schemaPath: ""});
  }

  return e;
}
```

No helper functions. No dead code. Every line corresponds to a specific
constraint in the schema.

### Validation of example instance

Instance:
```json
{ "name": "Alice", "age": 300, "tags": ["a", 42], "extra": true }
```

Errors produced:
```json
[
  { "instancePath": "/age",    "schemaPath": "/properties/age/type" },
  { "instancePath": "/tags/1", "schemaPath": "/properties/tags/elements/type" },
  { "instancePath": "/extra",  "schemaPath": "" }
]
```

- `age`: 300 is a number with zero fractional part, but 300 > 255 (uint8 max).
- `tags/1`: 42 is not a string.
- `extra`: not in required or optional properties, and `additionalProperties`
  defaults to `false`.
