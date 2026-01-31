## Issue #121: Add JsonPath module with AST parser and evaluator

### Goal

Add a new Maven module that implements **JsonPath** as described by Stefan Goessner:
`https://goessner.net/articles/JsonPath/index.html`.

This module must:

- Parse a JsonPath string into a **custom AST**
- Evaluate that AST against a JSON document already parsed by this repo’s
  `jdk.sandbox.java.util.json` API
- Have **no runtime dependencies** outside `java.base` (and the existing `json-java21` module)
- Target **Java 21** and follow functional / data-oriented style (records + sealed interfaces)

### Non-goals

- Parsing JSON documents from strings (the core `Json.parse(...)` already does that)
- Adding non-trivial external dependencies (no regex engines beyond JDK, no parser generators)
- Supporting every JsonPath dialect ever published; the baseline is the article examples

### Public API shape (module `json-java21-jsonpath`)

Package: `json.java21.jsonpath`

- `JsonPathExpression JsonPath.parse(String path)`
  - Parses a JsonPath string into an AST-backed compiled expression.
- `List<JsonValue> JsonPathExpression.select(JsonValue document)`
  - Evaluates the expression against a parsed JSON document and returns matched nodes in traversal order.

Notes:
- The return type is a `List<JsonValue>` to avoid introducing a JSON encoding of “result sets”.
  Callers can wrap it into `JsonArray.of(...)` if desired.

### AST plan

Use a sealed interface with records (no inheritance trees with stateful objects):

- `sealed interface PathNode permits Root, StepChain`
- `record Root() implements PathNode`
- `record StepChain(PathNode base, Step step) implements PathNode`

Steps are a separate sealed protocol:

- `sealed interface Step permits Child, RecursiveDescent, Wildcard, ArrayIndex, ArraySlice, Union, Filter`
- `record Child(Name name)` where `Name` is either identifier or quoted key
- `record RecursiveDescent(Step selector)` where selector is `Child` or `Wildcard`
- `record Wildcard()`
- `record ArrayIndex(int index)` supports negative indices per examples
- `record ArraySlice(Integer start, Integer end, Integer step)` to cover `[:2]`, `[-1:]`, etc.
- `record Union(List<Step> selectors)` for `[0,1]`, `['a','b']`
- `record Filter(PredicateExpr expr)` for `[?(...)]`

Filter expressions:

- Keep a minimal expression AST that supports the article examples:
  - `@.field` access
  - `@.length` pseudo-property for array length
  - numeric literals
  - string literals
  - comparison operators: `<`, `<=`, `>`, `>=`, `==`, `!=`
  - arithmetic: `+`, `-` (only what’s needed for `(@.length-1)`)

### Parser plan

Hand-rolled scanner + recursive descent parser:

- Lex JsonPath into tokens (`$`, `.`, `..`, `[`, `]`, `*`, `,`, `:`, `?(`, `)`, identifiers, quoted strings, numbers, operators).
- Parse according to the article grammar:
  - Root `$` must appear first
  - Dot-notation steps: `.name`, `.*`, `..name`, `..*`
  - Bracket steps:
    - `['name']`
    - `[0]`, `[-1]`
    - `[0,1]`, `['a','b']`
    - `[:2]`, `[2:]`, `[-1:]`
    - `[?(...)]`
    - `[(...)]` for script expressions used as array index in examples (limited support)

### Evaluator plan

Evaluator is a pure function over immutable inputs, implemented as static methods:

- Maintain a worklist of “current nodes” (starting with the document root).
- For each step:
  - **Child**: for objects, pick member by key; for arrays, apply to each element if they’re objects (per example behavior).
  - **RecursiveDescent**: walk the subtree of each current node (object members + array elements) and apply the selector to every node encountered.
  - **Wildcard**: for objects select all member values; for arrays select all elements.
  - **ArrayIndex / Slice / Union**: apply only when the current node is an array.
  - **Filter**: apply only when current node is an array; keep elements where predicate is true.

Ordering:
- Preserve traversal order implied by iterating object members (`JsonObject.members()` is order-preserving) and array elements order.

### Tests (TDD baseline)

Add tests that correspond 1:1 with every example on the article page:

- Use the article’s sample document (embedded as a Java text block) and parse with `Json.parse(...)`.
- Assertions check matched values by rendering to JSON (`JsonValue.toString()`) and comparing to expected fragments.
- Every test method logs an INFO banner at start (common base class).

### Verification

Run focused module tests with logging:

```bash
$(command -v mvnd || command -v mvn || command -v ./mvnw) -pl json-java21-jsonpath test -Djava.util.logging.ConsoleHandler.level=FINE
```

Run full suite once stable:

```bash
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -Djava.util.logging.ConsoleHandler.level=INFO
```

