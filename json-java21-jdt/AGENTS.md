# json-java21-jdt/AGENTS.md

This file is for contributor/agent operational notes. Read `json-java21-jdt/README.md` for purpose, supported syntax, and user-facing examples.

- User docs MUST recommend only `./mvnw`.
- The `$(command -v mvnd || command -v mvn || command -v ./mvnw)` wrapper is for local developer speed only; do not put it in user-facing docs.

## Stable Code Entry Points

- `json-java21-jdt/src/main/java/json/java21/jdt/Jdt.java` -- Public API: `Jdt.transform(source, transform)`
- `json-java21-jdt/src/main/java/json/java21/jdt/JdtException.java` -- Exception for invalid transforms

## Dependencies

- **`json-java21`** (core): The `JsonValue` sealed type hierarchy (`jdk.sandbox.java.util.json.*`)
- **`json-java21-jsonpath`**: `JsonPath.parse(path).query(source)` for `@jdt.path`-based operations

Both are compile-time dependencies. Changes to either module's public API may require updates here.

## Architecture Notes

### Current Implementation (Static Walker)

The engine currently has **no AST**. It works directly on `JsonValue` trees, interpreting `@jdt.*` keys on-the-fly via a recursive static-method walker:

1. `Jdt.transform(source, transform)` -- public entry point
2. `applyTransform(source, transform)` -- dispatches: non-object replaces, object checks for `@jdt.*` directives
3. `applyJdtDirectives(source, transformObj)` -- applies verbs in order: Rename -> Remove -> Merge -> Replace
4. Non-JDT keys in the transform object are processed as recursive default-merge operations

### Path-Based Operations

Path operations follow the pattern:
1. Parse JSONPath string: `JsonPath.parse(pathString)`
2. Query source: `jsonPath.query(source)` returns `List<JsonValue>`
3. Transform matched nodes using reference identity (`node == match`) to find and replace

**Known limitation**: Reference identity matching is fragile. It works only because JsonPath returns the exact same object instances from the source tree (not copies).

### Incomplete Features

- **Path-based rename**: `applyRenameWithPath` is a stub (returns source unchanged). The TODO is at `Jdt.java:234`.
- **~20 skipped Microsoft fixtures**: All path-based operation fixtures are in `Skipped/` subdirectories.

## Transform Verb Processing

Directive | Method | Notes
---|---|---
`@jdt.rename` | `applyRename` -> `applyRenameMapping` / `applyRenameWithPath` | Path-based is a stub
`@jdt.remove` | `applyRemove` -> `removeKey` / `applyRemoveWithPath` | Path-based uses `removeMatchedNodes`
`@jdt.merge` | `applyMerge` -> `mergeObjects` / `applyMergeWithPath` | Supports double-bracket arrays
`@jdt.replace` | `applyReplace` -> `applyReplaceWithPath` | Supports double-bracket arrays

## Test Fixtures

Microsoft JDT fixtures are vendored into:
```
json-java21-jdt/src/test/resources/microsoft-json-document-transforms/Inputs/
```

Convention: `{Category}.Source.json` + `{TestName}.Transform.json` + `{TestName}.Expected.json`

Fixtures in `Skipped/` subdirectories are excluded by `MicrosoftJdtFixturesTest` (line 42 filter). Move fixtures out of `Skipped/` as path-based operations are implemented.

## When Changing Transform Behavior

- Update `Jdt.java` (the engine).
- Add unit tests in `JdtTest.java` for the new behavior.
- Move applicable fixtures from `Skipped/` to the active directory if path-based operations are now supported.
- New tests MUST extend `JdtLoggingConfig`.
- Verify the full build passes: test counts in `.github/workflows/ci.yml` must match.

## Future Direction: AST and Code Generation

The planned evolution follows the same pattern as `jtd-esm-codegen`:

1. **Parse** the transform JSON into a sealed JDT AST (algebraic data types)
2. **Walk the AST** with exhaustive switch expressions to generate code
3. **Bytecode codegen**: Compile JDT transforms into generated Java classes
4. **ESM codegen**: Generate ES2020 JavaScript modules from the JDT AST
5. **Compiled JSONPath**: Replace the static JsonPath walker with compiled functions

This aligns with the project-wide "AST -> codegen" metaprogramming pattern.
