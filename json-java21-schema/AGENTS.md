# JSON Schema Validator - AGENTS Development Guide

Note: Prefer mvnd (Maven Daemon) for faster builds. If installed, you can alias mvn to mvnd so top-level instructions work consistently:

```bash
if command -v mvnd >/dev/null 2>&1; then alias mvn=mvnd; fi
```

## Quick Start Commands

### Building and Testing
```bash
# Compile only
mvnd compile -pl json-java21-schema

# Run all tests
mvnd test -pl json-java21-schema

# Run specific test
mvnd test -pl json-java21-schema -Dtest=JsonSchemaTest#testStringTypeValidation

# Run tests with debug logging
mvnd test -pl json-java21-schema -Dtest=JsonSchemaTest -Djava.util.logging.ConsoleHandler.level=FINE

# Run integration tests (JSON Schema Test Suite)
mvnd verify -pl json-java21-schema
```

### Logging Configuration
The project uses `java.util.logging` with levels:
- `FINE` - Schema compilation and validation flow
- `FINER` - Conditional validation branches
- `FINEST` - Stack frame operations

### Test Organization

#### Unit Tests (`JsonSchemaTest.java`)
- **Basic type validation**: string, number, boolean, null
- **Object validation**: properties, required, additionalProperties
- **Array validation**: items, min/max items, uniqueItems
- **String constraints**: length, pattern, enum
- **Number constraints**: min/max, multipleOf
- **Composition**: allOf, anyOf, if/then/else
- **Recursion**: linked lists, trees with $ref

#### Integration Tests (`JsonSchemaCheckIT.java`)
- **JSON Schema Test Suite**: Official tests from json-schema-org
- **Real-world schemas**: Complex nested validation scenarios
- **Performance tests**: Large schema compilation
- **Metrics reporting**: Comprehensive compatibility statistics with detailed skip categorization

### JSON Schema Test Suite Metrics

The integration test now provides defensible compatibility metrics:

```bash
# Run with console metrics (default)
mvnd verify -pl json-java21-schema

# Export detailed JSON metrics
mvnd verify -pl json-java21-schema -Djson.schema.metrics=json

# Export CSV metrics for analysis
mvnd verify -pl json-java21-schema -Djson.schema.metrics=csv
```

**Current measured compatibility** (as of implementation):
- **Overall**: 63.3% (1,153 of 1,822 tests pass)
- **Test coverage**: 420 test groups, 1,657 validation attempts
- **Skip breakdown**: 70 unsupported schema groups, 2 test exceptions, 504 lenient mismatches

The metrics distinguish between:
- **unsupportedSchemaGroup**: Whole groups skipped due to unsupported features (e.g., $ref, anchors)
- **testException**: Individual tests that threw exceptions during validation
- **lenientMismatch**: Expected≠actual results in lenient mode (counted as failures in strict mode)

#### OpenRPC Validation (`OpenRPCSchemaValidationIT.java`)
- **Location**: `json-java21-schema/src/test/java/io/github/simbo1905/json/schema/OpenRPCSchemaValidationIT.java`
- **Resources**: `src/test/resources/openrpc/schema.json` and `openrpc/examples/*.json`
- **Thanks**: OpenRPC meta-schema and examples (Apache-2.0). Sources: https://github.com/open-rpc/meta-schema and https://github.com/open-rpc/examples

#### Annotation Tests (`JsonSchemaAnnotationsTest.java`)
- **Annotation processing**: Compile-time schema generation
- **Custom constraints**: Business rule validation
- **Error reporting**: Detailed validation messages

### Development Workflow

1. **TDD Approach**: All tests must pass before claiming completion
2. **Stack-based validation**: No recursion, uses `Deque<ValidationFrame>`
3. **Immutable schemas**: All types are records, thread-safe
4. **Sealed interface**: Prevents external implementations

### Key Design Points

- **Single public interface**: `JsonSchema` contains all inner record types
- **Lazy $ref resolution**: Root references resolved at validation time
- **Conditional validation**: if/then/else supported via `ConditionalSchema`
- **Composition**: allOf, anyOf, not patterns implemented
- **Error paths**: JSON Pointer style paths in validation errors

### Testing Best Practices

- **Test data**: Use JSON string literals with `"""` for readability
- **Assertions**: Use AssertJ for fluent assertions
- **Error messages**: Include context in validation error messages
- **Edge cases**: Always test empty collections, null values, boundary conditions

### Performance Notes

- **Compile once**: Schemas are immutable and reusable
- **Stack validation**: O(n) time complexity for n validations
- **Memory efficient**: Records with minimal object allocation
- **Thread safe**: No shared mutable state

### Debugging Tips

- **Enable logging**: Use `-Djava.util.logging.ConsoleHandler.level=FINE`
- **Test isolation**: Run individual test methods for focused debugging
- **Schema visualization**: Use `Json.toDisplayString()` to inspect schemas
- **Error analysis**: Check validation error paths for debugging

Repo-level validation: Before pushing, run `mvn verify` at the repository root to validate unit and integration tests across all modules.
