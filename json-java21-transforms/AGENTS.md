# json-java21-transforms/AGENTS.md

This file is for contributor/agent operational notes. Read `json-java21-transforms/README.md` for purpose, supported operations, and user-facing examples.

- User docs MUST recommend only `./mvnw`.
- The `$(command -v mvnd || command -v mvn || command -v ./mvnw)` wrapper is for local developer speed only; do not put it in user-facing docs.

## Specification

This module implements JSON Document Transforms based on the Microsoft specification:
- Wiki: https://github.com/Microsoft/json-document-transforms/wiki
- C# Implementation: https://github.com/Microsoft/json-document-transforms

**IMPORTANT**: Do NOT call this technology "JDT" - that abbreviation conflicts with RFC 8927 (JSON Type Definition) which is implemented in `json-java21-jtd`. Always refer to this as "json-transforms" or "JSON Transforms".

## Stable Code Entry Points

- `json-java21-transforms/src/main/java/json/java21/transforms/JsonTransforms.java` - Main API
- `json-java21-transforms/src/main/java/json/java21/transforms/JsonTransformsParser.java` - Parser
- `json-java21-transforms/src/main/java/json/java21/transforms/JsonTransformsAst.java` - AST types
- `json-java21-transforms/src/main/java/json/java21/transforms/JsonTransformsParseException.java` - Parse errors

## When Changing Syntax/Behavior

- Update `JsonTransformsAst` + `JsonTransformsParser` + `JsonTransforms` together.
- Add parser + evaluation tests; new tests should extend `JsonTransformsLoggingConfig`.

## Design Principles

- Follow the parse/apply two-phase pattern like `JsonPath` and `Jtd`
- Use immutable records for AST nodes
- Pre-compile JsonPath expressions during parse phase
- Apply transforms using stack-based evaluation (no recursion)
- Defensive copies in all record constructors

Consider these rules if they affect your changes.
