# GitHub Copilot Instructions

**ALWAYS reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.**

## Environment Setup
- **Java 21+ REQUIRED**: Set Java 21+ as active before any build operations:
  ```bash
  export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
  export PATH=$JAVA_HOME/bin:$PATH
  java -version  # Should show Java 21+
  ```

## Core Development Principles

### Code Structure
- Use Records for all data structures
- Use sealed interfaces for protocols
- Default to package-private scope
- Package code by feature, not by layer
- Apply Data-Oriented Programming principles
- Prefer static methods with Records as parameters
- Use JEP 467 Markdown documentation comments (`///`) instead of legacy JavaDoc

### Functional Programming Style
- Use Stream operations instead of traditional loops
- Never use `for(;;)` with mutable loop variables
- Use `Arrays.setAll` for array operations
- Write exhaustive destructuring switch expressions
- Use `final var` for local variables, parameters, and destructured fields

### Documentation & Logging
- Use JEP 467 Markdown documentation with `///` prefix
- Add appropriate logging levels:
  - FINE: Production-level debugging
  - FINER: Verbose debugging, internal flow
  - INFO: Important runtime information
  - WARNING: Potential issues
  - SEVERE: Critical errors
- Use lambda logging for performance: `LOGGER.fine(() -> "message " + variable)`

### Testing & Validation
- Follow TDD principles - write tests first
- Never disable tests for unimplemented logic
- Use appropriate assertions:
  1. `Objects.requireNonNull()` for public API inputs
  2. `assert` for internal method validation
  3. Use validation methods from `Objects` and `Arrays`

### Error Handling
- Handle `JsonParseException` for malformed JSON
- Consider security implications (stack overflow, malicious inputs)
- Use appropriate logging levels for different error scenarios

### Code Examples
```java
// Correct Record usage
record User(String name, int age, boolean active) {}

// Correct logging
private static final Logger LOGGER = Logger.getLogger(ClassName.class.getName());
LOGGER.fine(() -> "Processing user: " + user.name());

// Correct documentation
/// Returns a JSON representation of the user.
/// @param user The user to convert
/// @return A JsonValue containing the user data
public static JsonValue toJson(User user) {
    Objects.requireNonNull(user, "user must not be null");
    // ...implementation
}

// Correct switch expression
return switch (jsonValue) {
    case JsonObject obj -> handleObject(obj);
    case JsonArray arr -> handleArray(arr);
    case JsonString str -> str.value();
    case JsonNumber num -> num.toString();
    case JsonBoolean bool -> bool.value();
    case JsonNull _ -> "null";
};
```

### Project Architecture
- Core package: `jdk.sandbox.java.util.json`
- Internal implementation: `jdk.sandbox.internal.util.json`
- JSON Schema validator in separate module
- Use appropriate logging configuration per module

### Common Pitfalls to Avoid
- Don't use legacy JavaDoc comments (`/** ... */`)
- Don't use mutable state or OOP patterns
- Don't use magic numbers - use enum constants
- Don't write large if-else chains - use switch expressions
- Don't filter test output - use appropriate logging levels
- Don't add early returns without proper pattern matching

### Security Considerations
- Be aware of stack exhaustion attacks with deep nesting
- Validate all inputs thoroughly
- Handle malicious inputs that may violate API contracts
- Use appropriate logging for security-related issues

### Testing Workflow
```bash
# Compile focused module
$(command -v mvnd || command -v mvn || command -v ./mvnw) -pl json-java21-api-tracker -Djava.util.logging.ConsoleHandler.level=SEVERE

# Debug with verbose logs
$(command -v mvnd || command -v mvn || command -v ./mvnw) -pl json-java21-api-tracker -Dtest=TestClass -Djava.util.logging.ConsoleHandler.level=FINER
```

### JSON Schema Development
- Follow all repository-wide logging rules
- Add INFO-level log statements at test method entry
- Extend `JsonSchemaLoggingConfig` for new tests
- Use appropriate metrics collection flags:
  - `-Djson.schema.metrics=json`
  - `-Djson.schema.metrics=csv`

Remember: This is an experimental API - focus on correctness and clarity over performance optimization.

