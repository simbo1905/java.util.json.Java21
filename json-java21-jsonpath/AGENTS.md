# json-java21-jsonpath/AGENTS.md

This file is for contributor/agent operational notes. Read `json-java21-jsonpath/README.md` for purpose, supported syntax, and user-facing examples.

- User docs MUST recommend only `./mvnw`.
- The `$(command -v mvnd || command -v mvn || command -v ./mvnw)` wrapper is for local developer speed only; do not put it in user-facing docs.

Stable code entry points:
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPath.java` (public sealed interface; `parse` = interpreted engine, `compile` = compiled engine)
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathInterpreted.java` (AST-walking interpreter)
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathCompiled.java` (compiled engine facade)
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathExecutor.java` (package-private functional interface implemented by generated classes)
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathParser.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathAst.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathParseException.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathStreams.java`

Compiled engine (JDK 24+ only; the Maven profile `jsonpath-codegen` activates on JDK 24+ and adds `src/main/codegen/java` plus `src/test/codegen/java`):
- `json-java21-jsonpath/src/main/codegen/java/json/java21/jsonpath/JsonPathCodegen.java` (ClassFile API, JEP 484; emits `ClassFile.JAVA_21_VERSION` / version 65 bytecode)
- `json-java21-jsonpath/src/main/codegen/java/json/java21/jsonpath/JsonPathBytecodeEmitter.java` (AST-to-bytecode emitter)
- `json-java21-jsonpath/src/test/codegen/java/json/java21/jsonpath/JsonPathCompilerParityTest.java` (interpreted == generated parity over the existing test corpus)

When changing syntax/behavior:
- Update `JsonPathAst` + `JsonPathParser` + `JsonPath` together.
- Mirror the change in `JsonPathBytecodeEmitter` (and `JsonPathHelpers` when shared evaluation logic moves); the parity test suite guards the interpreted/generated contract.
- Add parser + evaluation tests; new tests should extend `JsonPathLoggingConfig`; add a matching parity entry for any new operator.
