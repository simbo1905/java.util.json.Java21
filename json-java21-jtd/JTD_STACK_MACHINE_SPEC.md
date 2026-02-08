# JTD Stack-Machine Interpreter Specification

A language-independent specification for compiling RFC 8927 JSON Type Definition
schemas into an immutable AST, and validating JSON documents against that AST
using an explicit work stack.

This spec describes an **interpreter**: a generic validation engine that walks
a compiled AST at runtime. The AST is built once, then reused to validate
any number of JSON documents.

## 1. Terminology

| Term | Meaning |
|---|---|
| **schema** | A JSON object conforming to RFC 8927. |
| **instance** | The JSON value being validated. |
| **form** | One of the 8 mutually-exclusive schema shapes defined in RFC 8927 plus the nullable modifier. |
| **AST node** | An immutable, tagged value representing one compiled schema form. |
| **frame** | A tuple of (AST node, instance, path state) representing one unit of work. |
| **work stack** | A LIFO collection of frames. Validation is complete when the stack is empty. |
| **error** | A pair of JSON Pointers: `(instancePath, schemaPath)`. |
| **definitions** | A flat string-keyed map of named AST nodes, resolved at compile time. |
| **root** | A compiled schema document: its top-level AST node plus its definitions map. |

## 2. Compile Phase

### 2.1 Input

A JSON object (the schema) and, optionally, a base URI for multi-root
resolution (see Section 7).

### 2.2 Output

An immutable **Root**:

```
Root = {
  schema:      Node,
  definitions: Map<String, Node>   -- immutable, keyed by definition name
}
```

### 2.3 AST Node Types

A Node is a tagged union (sum type / sealed interface / discriminated union)
with exactly 9 variants. Implementations MUST represent these as immutable
value types (records, data classes, frozen structs, etc.).

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

### 2.4 Compilation Algorithm

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

### 2.5 Form-Specific Compilation

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

### 2.6 Compile-Time Invariants

After compilation, the following are guaranteed:
- Every `Ref.name` resolves to an entry in `definitions`.
- Every `Discrim.mapping` value is a `Properties` node (not nullable).
- No `Properties` node has overlapping required/optional keys.
- The AST is immutable. No node is modified after construction.

## 3. Runtime Phase: The Work Stack

### 3.1 Data Structures

**Frame** -- one unit of pending work:
```
Frame = {
  node:             Node,       -- which AST node to validate against
  instance:         JsonValue,  -- which piece of the document to inspect
  instancePath:     String,     -- JSON Pointer into the document (e.g. "/foo/0/bar")
  schemaPath:       String,     -- JSON Pointer into the schema (e.g. "/properties/foo")
  discriminatorTag: String?     -- carried from Discrim to variant Properties
}
```

**Error** -- one validation failure:
```
Error = {
  instancePath: String,   -- JSON Pointer into the document
  schemaPath:   String    -- JSON Pointer into the schema
}
```

**State** -- the complete validation state:
```
State = {
  stack:       Stack<Frame>,     -- LIFO work stack
  errors:      List<Error>,      -- accumulated errors
  definitions: Map<String, Node> -- from the compiled Root
}
```

### 3.2 Main Loop

```
validate(root: Root, instance: JsonValue) -> List<Error>:
  state = {
    stack:       [ Frame(root.schema, instance, "", "", null) ],
    errors:      [],
    definitions: root.definitions
  }

  WHILE state.stack is not empty:
    frame = state.stack.pop()
    step(frame, state)

  RETURN state.errors
```

The loop is iterative. There is no recursion. The stack depth is bounded by
the document's structural depth, not by the schema's complexity. This
prevents stack overflow on deeply nested documents.

Validation does **not** short-circuit. All frames are processed. All errors
are collected.

### 3.3 The Step Function

```
step(frame, state):
  node = frame.node

  -- Nullable check: intercepts before any form logic
  IF node is Nullable:
    IF frame.instance is null:
      RETURN                    -- null is valid; nothing to push
    ELSE:
      -- Unwrap and re-step with the inner node
      step(Frame(node.inner, frame.instance, frame.instancePath,
                 frame.schemaPath, frame.discriminatorTag), state)
      RETURN

  MATCH node:
    Empty      -> pass(frame, state)
    Ref        -> stepRef(frame, state)
    Type       -> stepType(frame, state)
    Enum       -> stepEnum(frame, state)
    Elements   -> stepElements(frame, state)
    Properties -> stepProperties(frame, state)
    Values     -> stepValues(frame, state)
    Discrim    -> stepDiscriminator(frame, state)
```

## 4. Step Functions

Each step function validates the current level, then pushes child frames
for descent. If the current level fails its type guard, no children are
pushed.

### 4.1 Empty

```
pass(frame, state):
  -- Accept anything. Push nothing.
```

### 4.2 Ref

```
stepRef(frame, state):
  target = state.definitions[frame.node.name]
  -- Replace the Ref node with its target and re-step.
  -- The frame's paths are preserved (the ref is transparent).
  step(Frame(target, frame.instance, frame.instancePath,
             frame.schemaPath, frame.discriminatorTag), state)
```

Recursive refs (a definition that references itself) are legal in RFC 8927.
This works naturally because each ref resolution pushes work onto the stack
(indirectly, via the resolved node's step function). The explicit stack
bounds memory usage to the document's depth, not the schema's recursion
depth.

### 4.3 Type

```
stepType(frame, state):
  ok = MATCH frame.node.type:
    "boolean"   -> frame.instance is a JSON boolean
    "string"    -> frame.instance is a JSON string
    "timestamp" -> frame.instance is a JSON string
                   AND matches RFC 3339 (with leap-second normalization)
    "float32"   -> frame.instance is a JSON number
    "float64"   -> frame.instance is a JSON number
    "int8"      -> isIntInRange(frame.instance, -128, 127)
    "uint8"     -> isIntInRange(frame.instance, 0, 255)
    "int16"     -> isIntInRange(frame.instance, -32768, 32767)
    "uint16"    -> isIntInRange(frame.instance, 0, 65535)
    "int32"     -> isIntInRange(frame.instance, -2147483648, 2147483647)
    "uint32"    -> isIntInRange(frame.instance, 0, 4294967295)

  IF NOT ok:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/type")
```

Helper:
```
isIntInRange(value, min, max):
  REQUIRE value is a JSON number
  REQUIRE value has zero fractional part (e.g. 3.0 is integer, 3.5 is not)
  REQUIRE min <= value <= max
```

### 4.4 Enum

```
stepEnum(frame, state):
  IF frame.instance is NOT a JSON string:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/enum")
    RETURN

  IF frame.instance.stringValue NOT IN frame.node.values:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/enum")
```

### 4.5 Elements

```
stepElements(frame, state):
  IF frame.instance is NOT a JSON array:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/elements")
    RETURN

  -- Push one child frame per array element
  FOR i = 0 TO length(frame.instance) - 1:
    child = Frame(
      node:         frame.node.schema,
      instance:     frame.instance[i],
      instancePath: frame.instancePath + "/" + str(i),
      schemaPath:   frame.schemaPath + "/elements",
      discriminatorTag: null
    )
    state.stack.push(child)
```

### 4.6 Properties

This is the only form with three concerns: missing-key checks, child
descent, and additional-key rejection.

```
stepProperties(frame, state):
  IF frame.instance is NOT a JSON object:
    -- RFC 8927 §3.3.6: point to "properties" if it exists, else "optionalProperties"
    LET guardKey = "properties" IF frame.node.required is non-empty ELSE "optionalProperties"
    state.errors += Error(frame.instancePath, frame.schemaPath + "/" + guardKey)
    RETURN

  obj = frame.instance

  -- 1. Missing required properties
  FOR EACH (key, _) IN frame.node.required:
    IF key NOT IN obj:
      state.errors += Error(frame.instancePath, frame.schemaPath + "/properties/" + key)

  -- 2. Additional properties check
  IF NOT frame.node.additional:
    FOR EACH key IN keys(obj):
      IF key NOT IN frame.node.required
         AND key NOT IN frame.node.optional
         AND key != frame.discriminatorTag:      -- discriminator tag exemption
        state.errors += Error(frame.instancePath + "/" + key, frame.schemaPath)

  -- 3. Push child frames for required properties (if present in instance)
  FOR EACH (key, childNode) IN frame.node.required:
    IF key == frame.discriminatorTag: SKIP       -- already validated by Discrim
    IF key IN obj:
      state.stack.push(Frame(
        node:         childNode,
        instance:     obj[key],
        instancePath: frame.instancePath + "/" + key,
        schemaPath:   frame.schemaPath + "/properties/" + key,
        discriminatorTag: null
      ))

  -- 4. Push child frames for optional properties (if present in instance)
  FOR EACH (key, childNode) IN frame.node.optional:
    IF key == frame.discriminatorTag: SKIP
    IF key IN obj:
      state.stack.push(Frame(
        node:         childNode,
        instance:     obj[key],
        instancePath: frame.instancePath + "/" + key,
        schemaPath:   frame.schemaPath + "/optionalProperties/" + key,
        discriminatorTag: null
      ))
```

### 4.7 Values

```
stepValues(frame, state):
  IF frame.instance is NOT a JSON object:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/values")
    RETURN

  FOR EACH (key, value) IN frame.instance:
    state.stack.push(Frame(
      node:         frame.node.schema,
      instance:     value,
      instancePath: frame.instancePath + "/" + key,
      schemaPath:   frame.schemaPath + "/values",
      discriminatorTag: null
    ))
```

### 4.8 Discriminator

The discriminator form is a 5-step sequential check. If any step fails,
no child frames are pushed.

```
stepDiscriminator(frame, state):
  -- Step 1: Must be an object
  IF frame.instance is NOT a JSON object:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/discriminator")
    RETURN

  obj = frame.instance

  -- Step 2: Tag property must exist
  IF frame.node.tag NOT IN obj:
    state.errors += Error(frame.instancePath, frame.schemaPath + "/discriminator")
    RETURN

  tagValue = obj[frame.node.tag]

  -- Step 3: Tag must be a string
  IF tagValue is NOT a JSON string:
    state.errors += Error(frame.instancePath + "/" + frame.node.tag,
                          frame.schemaPath + "/discriminator")
    RETURN

  tagString = tagValue.stringValue

  -- Step 4: Tag value must be in mapping
  IF tagString NOT IN frame.node.mapping:
    state.errors += Error(frame.instancePath + "/" + frame.node.tag,
                          frame.schemaPath + "/mapping")
    RETURN

  -- Step 5: Push variant frame with discriminator tag exemption
  variantNode = frame.node.mapping[tagString]
  state.stack.push(Frame(
    node:             variantNode,
    instance:         obj,             -- same object, NOT the tag value
    instancePath:     frame.instancePath,
    schemaPath:       frame.schemaPath + "/mapping/" + tagString,
    discriminatorTag: frame.node.tag   -- passed to Properties for exemption
  ))
```

## 5. Type Checking Reference

Exact semantics for each `TypeKeyword`.

### 5.1 boolean

```
value is a JSON boolean (true or false)
```

### 5.2 string

```
value is a JSON string
```

### 5.3 timestamp

```
value is a JSON string
AND value matches the RFC 3339 date-time production
    (regex: ^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:(\d{2}|60)(\.\d+)?(Z|[+-]\d{2}:\d{2})$)
AND the date-time is parseable (accounting for leap seconds by
    normalizing :60 to :59 before parsing)
```

### 5.4 float32, float64

```
value is a JSON number (any finite number; no range check)
```

Note: RFC 8927 does not distinguish float32 from float64 at the validation
level. Both accept any JSON number.

### 5.5 Integer types

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

## 6. Multi-Root Support

RFC 8927 defines a single-document schema. This section extends it to
support multiple schema documents that reference each other.

### 6.1 Root Registry

```
Roots = Map<URI, Root>   -- immutable after compilation
```

### 6.2 Compile-Time Resolution

Compilation uses a LIFO work stack of document URIs:

```
compileAll(initialJson, initialUri) -> Roots:
  work = Stack<URI>
  built = Map<URI, Root>

  work.push(normalize(initialUri))

  WHILE work is not empty:
    uri = work.pop()
    IF uri IN built: CONTINUE              -- dedup: at-most-once per URI

    json = fetch(uri, initialJson)         -- fetch or use initialJson if uri matches
    root = compileRoot(json)

    -- Scan for remote refs
    FOR EACH Ref node in root.schema (recursively):
      targetUri = resolveUri(node.name, uri)
      IF targetUri.document != uri AND targetUri.document NOT IN built:
        work.push(targetUri.document)

    built[uri] = root

  RETURN freeze(built)                     -- immutable
```

### 6.3 Runtime with Multiple Roots

In the initial implementation, runtime validation uses only the primary root.
Remote refs are compiled and stored but not traversed at runtime. This
preserves compatibility with single-document behavior.

Future extensions resolve remote refs at runtime by looking up the target
root in the Roots registry and pushing frames that reference nodes from
different roots.

## 7. Error Format

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

Implementations MAY enrich errors with additional information (character
offsets, human-readable breadcrumb trails, etc.) but MUST always include
the two JSON Pointer fields.

### 7.1 Schema Path Construction

Each step function appends to `schemaPath` as it descends:

| Form | Appended path component(s) |
|---|---|
| Type | `/type` |
| Enum | `/enum` |
| Elements (type guard) | `/elements` |
| Elements (child) | `/elements` |
| Properties (type guard) | `/properties` (or `/optionalProperties` if schema has no `properties` member) |
| Properties (missing key) | `/properties/<key>` |
| Properties (additional) | (nothing -- error at current path) |
| Properties (child req) | `/properties/<key>` |
| Properties (child opt) | `/optionalProperties/<key>` |
| Values (type guard) | `/values` |
| Values (child) | `/values` |
| Discrim (not object) | `/discriminator` |
| Discrim (tag missing) | `/discriminator` |
| Discrim (tag not string) | `/discriminator` |
| Discrim (tag not in map) | `/mapping` |
| Discrim (variant) | `/mapping/<tagValue>` |

### 7.2 Instance Path Construction

| Descent into | Appended to instancePath |
|---|---|
| Array element at index `i` | `/<i>` |
| Object property with key `k` | `/<k>` |
| Discriminator tag value | `/<tagFieldName>` |
| Discriminator variant | (nothing -- same object) |
| Ref target | (nothing -- transparent) |

## 8. Implementation Notes

### 8.1 Stack Ordering

The work stack is LIFO (depth-first). The last child pushed is the first
validated. For properties, this means children are validated in reverse
insertion order. The error *set* is the same regardless of stack ordering;
only the error *order* may differ. RFC 8927 does not specify error ordering.

### 8.2 Discriminator Tag Exemption

When a Discriminator pushes a variant Properties frame, it passes the tag
field name as `discriminatorTag`. The Properties step function uses this to:
- Skip the tag field when checking additional properties.
- Skip the tag field when pushing child frames (it was already validated
  by the Discriminator step).

This is the only case where state flows between step functions.

### 8.3 No Short-Circuit

Validation processes all frames even after encountering errors. This
ensures all errors are reported in a single pass. An implementation MAY
offer an optional `maxErrors` parameter to bound error collection.

### 8.4 Immutability

The AST is immutable after compilation. The Frame is immutable. The only
mutable state during validation is the work stack and the error list.
This makes the validation loop trivially thread-safe since the stack and
error list are created per `validate()` call.

### 8.5 Memory

Each frame is a small tuple (5 fields). Stack depth equals document nesting
depth. Memory is O(depth * breadth) in the worst case (a wide object with
many properties each needing validation).

The explicit stack replaces the language's call stack, preventing stack
overflow on deeply nested or recursive documents.

## 9. Conformance

An implementation conforms to this spec if:

1. It compiles any valid RFC 8927 schema into the AST defined in Section 2.3.
2. It rejects invalid schemas at compile time per the constraints in Section 2.6.
3. It validates any JSON instance against a compiled AST using the step
   functions defined in Section 4, producing the error paths defined in
   Section 7.
4. It passes the official JTD validation test suite (`validation.json` from
   `json-typedef-spec`).
5. It passes the official JTD invalid schema test suite (`invalid_schemas.json`).

## 10. Worked Example

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

Instance:
```json
{
  "name": "Alice",
  "age": 300,
  "tags": ["a", 42],
  "extra": true
}
```

### Compiled AST

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

### Validation Trace

```
Stack: [Frame(Properties, root, "", "")]
Pop:   Frame(Properties, root, "", "")
  -> Type guard: root is object? YES
  -> Missing keys: all 3 required keys present? YES
  -> Additional keys: "extra" not in required/optional -> ERROR("/extra", "")
  -> Push children:
     Frame(Type("string"), "Alice", "/name", "/properties/name")
     Frame(Type("uint8"),  300,     "/age",  "/properties/age")
     Frame(Elements,       [...],   "/tags", "/properties/tags")

Stack: [Elements, Type("uint8"), Type("string")]
Pop:   Frame(Elements, ["a",42], "/tags", "/properties/tags")
  -> Type guard: is array? YES
  -> Push children:
     Frame(Type("string"), "a",  "/tags/0", "/properties/tags/elements")
     Frame(Type("string"), 42,   "/tags/1", "/properties/tags/elements")

Stack: [Type("string")/42, Type("string")/"a", Type("uint8"), Type("string")]
Pop:   Frame(Type("string"), 42, "/tags/1", "/properties/tags/elements")
  -> 42 is not a string -> ERROR("/tags/1", "/properties/tags/elements/type")

Pop:   Frame(Type("string"), "a", "/tags/0", "/properties/tags/elements")
  -> "a" is a string -> OK

Pop:   Frame(Type("uint8"), 300, "/age", "/properties/age")
  -> 300 is number, zero fractional, but 300 > 255 -> ERROR("/age", "/properties/age/type")

Pop:   Frame(Type("string"), "Alice", "/name", "/properties/name")
  -> "Alice" is a string -> OK

Stack empty. Done.
```

### Errors Collected

```
[
  { instancePath: "/extra",  schemaPath: "" },
  { instancePath: "/tags/1", schemaPath: "/properties/tags/elements/type" },
  { instancePath: "/age",    schemaPath: "/properties/age/type" }
]
```
