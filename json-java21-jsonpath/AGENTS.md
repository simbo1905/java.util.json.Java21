# json-java21-jsonpath/AGENTS.md

This file is for contributor/agent operational notes. Read `json-java21-jsonpath/README.md` for purpose, supported syntax, and user-facing examples.

- User docs MUST recommend only `./mvnw`.
- The `$(command -v mvnd || command -v mvn || command -v ./mvnw)` wrapper is for local developer speed only; do not put it in user-facing docs.

Stable code entry points:
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPath.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathParser.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathAst.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathParseException.java`
- `json-java21-jsonpath/src/main/java/json/java21/jsonpath/JsonPathStreams.java`

When changing syntax/behavior:
- Update `JsonPathAst` + `JsonPathParser` + `JsonPath` together.
- Add parser + evaluation tests; new tests should extend `JsonPathLoggingConfig`.
