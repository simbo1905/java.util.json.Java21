# json-java21-jsonpath/AGENTS.md

This file is for contributor/agent operational notes. Read `json-java21-jsonpath/README.md` for purpose, supported syntax, and user-facing examples.

- User docs MUST recommend only `./mvnw`.
- The `$(command -v mvnd || command -v mvn || command -v ./mvnw)` wrapper is for local developer speed only; do not put it in user-facing docs.

## Architecture

JsonPath is a sealed interface with two implementations:
- `JsonPathInterpreted`: AST-walking interpreter (default from `JsonPath.parse()`)
- `JsonPathCompiled`: Bytecode-compiled version (from `JsonPath.compile()`)

The compilation flow:
1. `JsonPath.parse()` -> `JsonPathInterpreted` (fast parsing, AST-based evaluation)
2. `JsonPath.compile()` -> `JsonPathCompiled` (generates Java source, compiles with JDK ToolProvider)

## Stable Code Entry Points

- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPath.java` - Public sealed interface
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathParser.java` - Parses expressions to AST
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathAst.java` - AST node definitions
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathInterpreted.java` - AST-walking evaluator
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathCompiler.java` - Code generator and compiler
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathCompiled.java` - Compiled executor wrapper
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathExecutor.java` - Public interface for generated executors
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathHelpers.java` - Helpers for generated code
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathStreams.java` - Stream processing utilities

## When Changing Syntax/Behavior

- Update `JsonPathAst` + `JsonPathParser` + `JsonPathInterpreted` together.
- Update `JsonPathCompiler` code generation to match any AST changes.
- Add parser + evaluation tests; new tests should extend `JsonPathLoggingConfig`.
- Add compiler tests in `JsonPathCompilerTest` to verify compiled and interpreted produce identical results.

## Code Generation Notes

The `JsonPathCompiler` generates Java source code that:
- Imports from `jdk.sandbox.java.util.json.*` and `json.java21.jsonpath.*`
- Implements `JsonPathExecutor` functional interface
- Uses `JsonPathHelpers` for complex operations (recursive descent, comparisons)
- Is compiled in-memory using `javax.tools.ToolProvider`

When adding new AST node types:
1. Add the case to `generateSegmentEvaluation()` in `JsonPathCompiler`
2. Consider if a helper method in `JsonPathHelpers` would simplify the generated code
