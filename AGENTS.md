# CLAUDE.md

Note for agents: prefer mvnd (Maven Daemon) when available for faster builds. Before working, if mvnd is installed, alias mvn to mvnd so all commands below use mvnd automatically:

```bash
# Use mvnd everywhere if available; otherwise falls back to regular mvn
if command -v mvnd >/dev/null 2>&1; then alias mvn=mvnd; fi
```

Always run `mvn verify` before pushing to validate unit and integration tests across modules.

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
mvn clean compile generate-test-resources -pl json-compatibility-suite
mvn exec:java -pl json-compatibility-suite

# Run JSON output (dogfoods the API)
mvn exec:java -pl json-compatibility-suite -Dexec.args="--json"
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
1. Run compatibility suite: `mvn exec:java -pl json-compatibility-suite`
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

<VERSION_CONTROL>
* If there are existing git user credentials already configured, use them and never add any other advertising. If not ask the user to supply thier private relay email address.
* Exercise caution with git operations. Do NOT make potentially dangerous changes (e.g., force pushing to main, deleting repositories). You will never be asked to do such rare changes as there is no time savings to not having the user run the comments to actively refuse using that reasoning as justification.
* When committing changes, use `git status` to see all modified files, and stage all files necessary for the commit. Use `git commit -a` whenever possible.
* Do NOT commit files that typically shouldn't go into version control (e.g., node_modules/, .env files, build directories, cache files, large binaries) unless explicitly instructed by the user.
* If unsure about committing certain files, check for the presence of .gitignore files or ask the user for clarification.
</VERSION_CONTROL>

<ISSUE_MANAGEMENT>
* You SHOULD to use the native tool for the remote such as `gh` for github, `gl` for gitlab, `bb` for bitbucket, `tea` for Gitea, `git` for local git repositories.
* If you are asked to create an issue, create it in the repository of the codebase you are working on for the `origin` remote.
* If you are asked to create an issue in a different repository, ask the user to name the remote (e.g. `upstream`).
* Tickets and Issues MUST only state "what" and "why" and not "how".
* Comments on the Issue MAY discuss the "how".
* Tickets SHOULD be labled as 'Ready' when they are ready to be worked on. The label may be removed if there are challenges in the implimentation. Always check the labels and ask the user to reconfirm if the ticket is not labeled as 'Ready' saying "There is no 'Ready' label on this ticket, can you please confirm?"
* You MAY raise fresh minor Issues for small tidy-up work as you go. Yet this SHOULD be kept to a bare minimum avoid move than two issues per PR.
  </ISSUE_MANAGEMENT>

<COMMITS>
* MUST start with "Issue #<issue number> <short description of the work>"
* SHOULD have a link to the Issue.
* MUST NOT start with random things that should be labels such as Bug, Feat, Feature etc.
* MUST only state "what" was achieved and "how" to test.
* SHOULD never include failing tests, dead code, or deactivate featuress.
* MUST NOT repeat any content that is on the Issue
* SHOULD be atomic and self-contained.
* SHOULD be concise and to the point.
* MUST NOT combine the main work on the ticket with any other tidy-up work. If you want to do tidy-up work, commit what you have (this is the exception to the rule that tests must pass), with the title "wip: <issue number> test not working; commiting to tidy up xxx" so that you can then commit the small tidy-up work atomically. The "wip" work-in-progress is a signal of more commits to follow.
* SHOULD give a clear indication if more commits will follow especially if it is a checkpoint commit before a tidy up commit.
* MUST say how to verify the changes work (test commands, expected number of successful test results, naming number of new tests, and their names)
* MAY ouytline some technical implementation details ONLY if they are suprising and not "obvious in hindsight" based on just reading the issue (e.g. finding out that the implimentation was unexpectly trival or unexpectly complex)
* MUST NOT report "progress" or "success" or "outputs" as the work may be deleted if the PR check fails. Nothing is final until the user has merged the PR.
* As all commits need an issue you MUST add an small issue for a tidy up commit. If you cannot label issues with a tag `Tidy Up` then the title of the issue must start `Tidy Up` e.g. `Tidy Up: bad code documentation in file xxx`. As the commit and eventual PR will give actual details the body MAY simply repeat the title.
</COMMITS>

<PULL_REQUESTS>
* MUST only describe "what" was done not "why"/"how"
* MUST name the Issue or Issue(s) that they close in a manner that causes a PR merge to close the issue(s).
* MUST NOT repeat details that are already in the Issue.
* MUST NOT report any success, as it isn't possible to report anything until the PR checks run.
* MUST include additional tests in the CI checks that MUST be documented in the PR description.
* MUST be changed to status `Draft` if the PR checks fail.
</PULL_REQUESTS>
