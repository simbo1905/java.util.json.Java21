# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start Commands

### Building the Project
```bash
# Full build
mvn clean compile
mvn package

# Build specific module
mvn clean compile -pl json-java21
mvn package -pl json-java21

# Build with test skipping
mvn clean compile -DskipTests
```

### Running Tests
```bash
# Run all tests
mvn test

# Run tests with clean output (recommended)
./mvn-test-no-boilerplate.sh

# Run specific test class
./mvn-test-no-boilerplate.sh -Dtest=JsonParserTests
./mvn-test-no-boilerplate.sh -Dtest=JsonTypedUntypedTests

# Run specific test method
./mvn-test-no-boilerplate.sh -Dtest=JsonParserTests#testParseEmptyObject

# Run tests in specific module
./mvn-test-no-boilerplate.sh -pl json-java21-api-tracker -Dtest=ApiTrackerTest
```

### JSON Compatibility Suite
```bash
# Build and run compatibility report
./mvnw clean compile generate-test-resources -pl json-compatibility-suite
./mvnw exec:java -pl json-compatibility-suite

# Run JSON output (dogfoods the API)
./mvnw exec:java -pl json-compatibility-suite -Dexec.args="--json"
```

### Debug Logging
```bash
# Enable debug logging for specific test
./mvn-test-no-boilerplate.sh -Dtest=JsonParserTests -Djava.util.logging.ConsoleHandler.level=FINER
```

## Architecture Overview

### Module Structure
- **`json-java21`**: Core JSON API implementation (main library)
- **`json-java21-api-tracker`**: API evolution tracking utilities
- **`json-compatibility-suite`**: JSON Test Suite compatibility validation

### Core Components

#### Public API (jdk.sandbox.java.util.json)
- **`Json`** - Static utility class for parsing/formatting/conversion
- **`JsonValue`** - Sealed root interface for all JSON types
- **`JsonObject`** - JSON objects (key-value pairs)
- **`JsonArray`** - JSON arrays
- **`JsonString`** - JSON strings
- **`JsonNumber`** - JSON numbers
- **`JsonBoolean`** - JSON booleans
- **`JsonNull`** - JSON null

#### Internal Implementation (jdk.sandbox.internal.util.json)
- **`JsonParser`** - Recursive descent JSON parser
- **`Json*Impl`** - Immutable implementations of JSON types
- **`Utils`** - Internal utilities and factory methods

### Design Patterns
- **Algebraic Data Types**: Sealed interfaces with exhaustive pattern matching
- **Immutable Value Objects**: All types are immutable and thread-safe
- **Lazy Evaluation**: Strings/numbers store offsets until accessed
- **Factory Pattern**: Static factory methods for construction
- **Bridge Pattern**: Clean API/implementation separation

## Key Development Practices

### Testing Approach
- **JUnit 5** with AssertJ for fluent assertions
- **Test Organization**: 
  - `JsonParserTests` - Parser-specific tests
  - `JsonTypedUntypedTests` - Conversion tests
  - `JsonRecordMappingTests` - Record mapping tests
  - `ReadmeDemoTests` - Documentation example validation

### Code Style
- **JEP 467 Documentation**: Use `///` triple-slash comments
- **Immutable Design**: All public types are immutable
- **Pattern Matching**: Use switch expressions with sealed types
- **Null Safety**: Use `Objects.requireNonNull()` for public APIs

### Performance Considerations
- **Lazy String/Number Creation**: Values computed on demand
- **Singleton Patterns**: Single instances for true/false/null
- **Defensive Copies**: Immutable collections prevent external modification
- **Efficient Parsing**: Character array processing with minimal allocations

## Common Workflows

### Adding New JSON Type Support
1. Add interface extending `JsonValue`
2. Add implementation in `jdk.sandbox.internal.util.json`
3. Update `Json.fromUntyped()` and `Json.toUntyped()`
4. Add parser support in `JsonParser`
5. Add comprehensive tests

### Debugging Parser Issues
1. Enable `FINER` logging: `-Djava.util.logging.ConsoleHandler.level=FINER`
2. Use `./mvn-test-no-boilerplate.sh` for clean output
3. Focus on specific test: `-Dtest=JsonParserTests#testMethod`
4. Check JSON Test Suite compatibility with compatibility suite

### API Compatibility Testing
1. Run compatibility suite: `./mvnw exec:java -pl json-compatibility-suite`
2. Check for regressions in JSON parsing
3. Validate against official JSON Test Suite

## Module-Specific Details

### json-java21
- **Main library** containing the core JSON API
- **Maven coordinates**: `io.github.simbo1905.json:json-java21:0.1-SNAPSHOT`
- **JDK requirement**: Java 21+

### json-compatibility-suite
- **Downloads** JSON Test Suite from GitHub automatically
- **Reports** 99.3% conformance with JSON standards
- **Identifies** security vulnerabilities (StackOverflowError with deep nesting)
- **Usage**: Educational/testing, not production-ready

### json-java21-api-tracker
- **Tracks** API evolution and compatibility
- **Uses** Java 24 preview features (`--enable-preview`)
- **Purpose**: Monitor upstream OpenJDK changes

## Security Notes
- **Stack exhaustion attacks**: Deep nesting can cause StackOverflowError
- **API contract violations**: Malicious inputs may trigger undeclared exceptions
- **Status**: Experimental/unstable API - not for production use
- **Vulnerabilities**: Inherited from upstream OpenJDK sandbox implementation