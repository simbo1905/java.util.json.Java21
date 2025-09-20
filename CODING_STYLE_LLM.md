# Java DOP Coding Standards ####################

This file is a Gen AI summary of CODING_STYLE.md to use less tokens of context window. Read the original file for full details.

IMPORTANT: We do TDD so all code must include targeted unit tests.
IMPORTANT: Never disable tests written for logic that we are yet to write we do Red-Green-Refactor coding.

## Core Principles

* Use Records for all data structures. Use sealed interfaces for protocols.
* Prefer static methods with Records as parameters
* Default to package-private scope
* Package-by-feature, not package-by-layer
* Create fewer, cohesive, wide packages (functionality modules or records as protocols)
* Use public only when cross-package access is required
* Use JEP 467 Markdown documentation examples: `/// good markdown` not legacy `/** bad html */`
* Apply Data-Oriented Programming principles and avoid OOP
* Use Stream operations instead of traditional loops. Never use `for(;;)` with mutable loop variables use
  `Arrays.setAll`
* Prefer exhaustive destructuring switch expressions over if-else statements
* Use destructuring switch expressions that operate on Records and sealed interfaces
* Use anonymous variables in record destructuring and switch expressions
* Use `final var` for local variables, parameters, and destructured fields
* Apply JEP 371 "Local Classes and Interfaces" for cohesive files with narrow APIs

## Data-Oriented Programming

* Separate data (immutable Records) from behavior (never utility classes always static methods)
* Use immutable generic data structures (maps, lists, sets) and take defense copies in constructors
* Write pure functions that don't modify state
* Leverage Java 21+ features:
    * Records for immutable data
    * Pattern matching for structural decomposition
    * Sealed classes for exhaustive switches
    * Virtual threads for concurrent processing

## Package Structure

* Use default (package-private) access as the standard. Do not use 'private' or 'public' by default.
* Limit public to genuine cross-package APIs
* Prefer package-private static methods. Do not use 'private' or 'public' by default.
* Limit private to security-related code
* Avoid anti-patterns: boilerplate OOP, excessive layering, dependency injection overuse

## Constants and Magic Numbers

* **NEVER use magic numbers** - always use enum constants
* **NEVER write large if-else-if statements over known types** - will not be exhaustive and creates bugs when new types are added. Use exhaustive switch statements over bounded sets such as enum values or sealed interface permits

## Functional Style

* Combine Records + static methods for functional programming
* Emphasize immutability and explicit state transformations
* Reduce package count to improve testability
* Implement Algebraic Data Types pattern with Function Modules
* Modern Stream Programming
* Use Stream API instead of traditional loops
* Write declarative rather than imperative code
* Chain operations without intermediate variables
* Support immutability throughout processing
* Example: `IntStream.range(0, 100).filter(i -> i % 2 == 0).sum()` instead of counting loops
* Always use final variables in functional style.
* Prefer `final var` with self documenting names over `int i` or `String s` but its not possible to do that on a `final` variable that is not yet initialized so its a weak preference not a strong one.
* Avoid just adding new functionality to the top of a method to make an early return. It is fine to have a simple guard statement. Yet general you should pattern match over the input to do different things with the same method. Adding special case logic is a code smell that should be avoided.

## Documentation using JEP 467 Markdown documentation

IMPORTANT: You must not write JavaDoc comments that start with `/**` and end with `*/`
IMPORTANT: You must "JEP 467: Markdown Documentation Comments" that start all lines with `///`

Here is an example of the correct format for documentation comments:

```java
/// Returns a hash code value for the object. This method is
/// supported for the benefit of hash tables such as those provided by
/// [java.util.HashMap].
///
/// The general contract of `hashCode` is:
///
///   - Whenever it is invoked on the same object more than once during
///     an execution of a Java application, the `hashCode` method
///   - If two objects are equal according to the
///     [equals][#equals(Object)] method, then calling the
///   - It is _not_ required that if two objects are unequal
///     according to the [equals][#equals(Object)] method, then
///
/// @return a hash code value for this object.
/// @see     java.lang.Object#equals(java.lang.Object)
```

## Logging

- Use Java's built-in logging: `java.util.logging.Logger`
- Log levels: Use appropriate levels (FINE, FINER, INFO, WARNING, SEVERE)
  - **FINE**: Production-level debugging, default for most debug output
  - **FINER**: Verbose debugging, detailed internal flow, class resolution details
  - **INFO**: Important runtime information
- LOGGER is a static field: `static final Logger LOGGER = Logger.getLogger(ClassName.class.getName());` where use the primary interface or the package as the logger name with the logger package-private and shared across the classes when the package is small enough.
- Use lambda logging for performance: `LOGGER.fine(() -> "message " + variable);`

# Compile, Test, Debug Loop

- **Check Compiles**: Focusing on the correct mvn module run without verbose logging and do not grep the output to see compile errors:
  ```bash
  $(command -v mvnd || command -v mvn || command -v ./mvnw) -pl json-java21-api-tracker -Djava.util.logging.ConsoleHandler.level=SEVERE
  ```
- **Debug with Verbose Logs**: Use `-Dtest=` to focus on just one or two test methods, or one class, using more logging to debug the code:
  ```bash
  $(command -v mvnd || command -v mvn || command -v ./mvnw) -pl json-java21-api-tracker -Dtest=XXX -Djava.util.logging.ConsoleHandler.level=FINER
  ```
- **No Grep Filtering**: Use logging levels to filter output, do not grep the output for compile errors, just run less test methods with the correct logging to reduce the output to a manageable size. Filtering hides problems and needs more test excution to find the same problems which wastes time.

## Modern Java Singleton Pattern: Sealed Interfaces

**Singleton Object Anti-Pattern**: Traditional singleton classes with private constructors and static instances are legacy should be avoided. With a functional style we can create a "package-private companion module" of small package-private methods with `sealed interfacee GoodSingletonModule permits Nothing { enum Nothing extends GoodSingletonModule{}; /* static functional methods here */ }`.

### Assertions and Input Validation

1.  On the public API entry points use `Objects.assertNonNull()` to ensure that the inputs are legal.
2. After that on internal method that should be passed only valid data use `assert` to ensure that the data is valid.
  - e.g. use `assert x==y: "unexpected x="+x+" y="+y;` as `mvn` base should be run with `-ea` to enable assertions.
3. Often there is an `orElseThrow()` which can be used so the only reason to use `assert` is to add more logging to the error message.
4. Consider using the validations of `Object` and `Arrays` and the like to ensure that the data is valid.
  - e.g. `Objects.requireNonNull(type, "type must not be null")` or `Arrays.checkIndex(index, array.length)`.

## JEP References

[JEP 467](https://openjdk.org/jeps/467): Markdown Documentation in JavaDoc
[JEP 371](https://openjdk.org/jeps/371): Local Classes and Interfaces
[JEP 395](https://openjdk.org/jeps/395): Records
[JEP 409](https://openjdk.org/jeps/409): Sealed Classes
[JEP 440](https://openjdk.org/jeps/440): Record Patterns
[JEP 427](https://openjdk.org/jeps/427): Pattern Matching for Switch
