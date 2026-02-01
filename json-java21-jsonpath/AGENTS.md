# json-java21-jsonpath Module AGENTS.md

## Purpose
This module implements a JsonPath query engine for the java.util.json Java 21 backport. It parses JsonPath expressions into an AST and evaluates them against JSON documents.

## Specification
Based on Stefan Goessner's JSONPath specification:
https://goessner.net/articles/JsonPath/

## Module Structure

### Main Classes
- `JsonPath`: Public API facade with `query()` method
- `JsonPathAst`: Sealed interface hierarchy defining the AST
- `JsonPathParser`: Recursive descent parser
- `JsonPathParseException`: Parse error exception

### Test Classes
- `JsonPathLoggingConfig`: JUL test configuration base class
- `JsonPathAstTest`: Unit tests for AST records
- `JsonPathParserTest`: Unit tests for parser
- `JsonPathGoessnerTest`: Integration tests based on Goessner article examples

## Development Guidelines

### Adding New Operators
1. Add new record type to `JsonPathAst.Segment` sealed interface
2. Update `JsonPathParser` to parse the new syntax
3. Add parser tests in `JsonPathParserTest`
4. Implement evaluation in `JsonPath.evaluateSegments()`
5. Add integration tests in `JsonPathGoessnerTest`

### Testing
```bash
# Run all tests
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jsonpath -Djava.util.logging.ConsoleHandler.level=INFO

# Run specific test class with debug logging
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jsonpath -Dtest=JsonPathGoessnerTest -Djava.util.logging.ConsoleHandler.level=FINE

# Run single test method
$(command -v mvnd || command -v mvn || command -v ./mvnw) test -pl json-java21-jsonpath -Dtest=JsonPathGoessnerTest#testAuthorsOfAllBooks -Djava.util.logging.ConsoleHandler.level=FINEST
```

## Design Principles

1. **No external dependencies**: Only java.base is allowed
2. **Pure TDD**: Tests first, then implementation
3. **Functional style**: Immutable records, pure evaluation functions
4. **Java 21 features**: Records, sealed interfaces, pattern matching with switch
5. **Defensive copies**: All collections in records are copied for immutability

## Known Limitations

1. **Script expressions**: Only `@.length-1` pattern is supported
2. **No general expression evaluation**: Complex scripts are not supported
3. **Stack-based recursion**: May overflow on very deep documents

## API Usage

```java
import jdk.sandbox.java.util.json.*;
import json.java21.jsonpath.JsonPath;

JsonValue json = Json.parse(jsonString);

// Preferred: parse once (cache) and reuse
JsonPath path = JsonPath.parse("$.store.book[*].author");
List<JsonValue> results = path.query(json);

// If you want a static call site, pass the compiled JsonPath
List<JsonValue> sameResults = JsonPath.query(path, json);
```

Notes:
- Parsing a JsonPath expression is relatively expensive compared to evaluation; cache compiled `JsonPath` instances in hot code paths.
- `JsonPath.query(String, JsonValue)` is intended for one-off usage only.
