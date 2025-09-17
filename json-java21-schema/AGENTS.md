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

#### Two-Level Logging Strategy
Use **FINE** for general flow visibility and **FINER** for detailed debugging:
```bash
# General flow - good for understanding compilation/validation patterns
mvnd test -pl json-java21-schema -Dtest=JsonSchemaTest#testMethod -Djava.util.logging.ConsoleHandler.level=FINE

# Detailed debugging - use when tracing specific execution paths
mvnd test -pl json-java21-schema -Dtest=JsonSchemaTest#testMethod -Djava.util.logging.ConsoleHandler.level=FINER
```

#### Systematic Debugging Approach
When code isn't being reached, use systematic logging rather than guessing:
1. Add FINE or logging at entry points
2. Add FINER logging at key decision points in the call stack
3. Use binary search approach - add logging halfway between working and non-working code
4. Text-based minds excel at processing log output systematically

You also need to ensure that the test class extends `JsonSchemaLoggingConfig` to honour the system property:
```java
/// Test local reference resolution for JSON Schema 2020-12
class JsonSchemaRefLocalTest extends JsonSchemaLoggingConfig {
  ...
}
```

IMPORTANT: 

- Always adjust the logging levels to be balanced  before committing code. 
- NEVER comment out code. 
- NEVER use System.out.println or e.printStackTrace(). 
- ALWAYS use lamba based JUL logging.
- NEVER filter logging output with head, tail, grep, etc. You shoould set the logging to the correct level of INFO, FINE, FINER, FINEST and run just the one test or method with the correct logging level to control token output.
- ALWAYS add a INFO level logging line at the top of each `@Test` method so that we can log at INFO level and see which tests might hang forever. 
- You SHOULD run tests as `timeout 30 mvnd test ...` to ensure that no test can hang forever and the timeout should not be too long.

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

**Current measured compatibility** (as of Pack 5 - Format validation implementation):
- **Overall**: 54.4% (992 of 1,822 tests pass)
- **Test coverage**: 420 test groups, 1,628 validation attempts  
- **Skip breakdown**: 73 unsupported schema groups, 0 test exceptions, 638 lenient mismatches

**Note on compatibility change**: The compatibility percentage decreased from 65.9% to 54.4% because format validation is now implemented but follows the JSON Schema specification correctly - format validation is annotation-only by default and only asserts when explicitly enabled via format assertion controls. Many tests in the suite expect format validation to fail in lenient mode, but our implementation correctly treats format as annotation-only unless format assertion is enabled.

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

#### Array Keywords Tests (`JsonSchemaArrayKeywordsTest.java`) - Pack 2
- **Contains validation**: `contains` with `minContains`/`maxContains` constraints
- **Unique items**: Structural equality using canonicalization for objects/arrays
- **Prefix items**: Tuple validation with `prefixItems` + trailing `items` validation
- **Combined features**: Complex schemas using all array constraints together

#### Format Validation Tests (`JsonSchemaFormatTest.java`) - Pack 5
- **Format validators**: 11 built-in format validators (uuid, email, ipv4, ipv6, uri, uri-reference, hostname, date, time, date-time, regex)
- **Opt-in assertion**: Format validation only asserts when explicitly enabled via Options, system property, or root schema flag
- **Unknown format handling**: Graceful handling of unknown formats (logged warnings, no validation errors)
- **Constraint integration**: Format validation works with other string constraints (minLength, maxLength, pattern)
- **Specification compliance**: Follows JSON Schema 2020-12 format annotation/assertion behavior correctly

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
- **Array validation**: Draft 2020-12 array features (contains, uniqueItems, prefixItems)
- **Format validation**: 11 built-in format validators with opt-in assertion mode
- **Structural equality**: Canonical JSON serialization for uniqueItems validation

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
