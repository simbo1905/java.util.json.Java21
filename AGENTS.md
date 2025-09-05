# AGENTS.md

Purpose: Operational guidance for AI coding agents working in this repository. Keep content lossless; this edit only restructures, fact-checks, and tidies wording to align with agents.md best practices.

Note: Prefer mvnd (Maven Daemon) when available for faster builds. Before working, if mvnd is installed, alias mvn to mvnd so all commands below use mvnd automatically:

```bash
# Use mvnd everywhere if available; otherwise falls back to regular mvn
if command -v mvnd >/dev/null 2>&1; then alias mvn=mvnd; fi
```

Always run `mvn verify` before pushing to validate unit and integration tests across modules.

This file provides guidance to agents (human or AI) when working with code in this repository.

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

## Python Usage (Herodoc, 3.2-safe)
- Prefer `python3` with a heredoc over Perl/sed for non-trivial transforms.
- Target ancient Python 3.2 syntax: no f-strings, no fancy deps.
- Example pattern:

```bash
python3 - <<'PY'
import os, sys, re
src = 'updates/2025-09-04/upstream/jdk.internal.util.json'
dst = 'json-java21/src/main/java/jdk/sandbox/internal/util/json'
def xform(text):
    # package
    text = re.sub(r'^package\s+jdk\.internal\.util\.json;', 'package jdk.sandbox.internal.util.json;', text, flags=re.M)
    # imports for public API
    text = re.sub(r'^(\s*import\s+)java\.util\.json\.', r'\1jdk.sandbox.java.util.json.', text, flags=re.M)
    # annotations
    text = re.sub(r'^\s*@(?:jdk\.internal\..*|ValueBased|StableValue).*\n', '', text, flags=re.M)
    return text
for name in os.listdir(src):
    if not name.endswith('.java') or name == 'StableValue.java':
        continue
    data = open(os.path.join(src,name),'r').read()
    out = xform(data)
    target = os.path.join(dst,name)
    tmp = target + '.tmp'
    open(tmp,'w').write(out)
    if os.path.getsize(tmp) == 0:
        sys.stderr.write('Refusing to overwrite 0-byte: '+target+'\n'); sys.exit(1)
    os.rename(tmp, target)
print('OK')
PY
```

## <IMPIMENTATION>
- MUST: Follow plan → implement → verify. No silent pivots.
- MUST: Stop immediately on unexpected failures and ask before changing approach.
- MUST: Keep edits atomic; avoid leaving mixed partial states.
- SHOULD: Propose options with trade-offs before invasive changes.
- SHOULD: Prefer mechanical, reversible transforms for upstream syncs.
- SHOULD: Validate non-zero outputs before overwriting files.
- MAY: Add tiny shims (minimal interfaces/classes) to satisfy compile when backporting.
- MUST NOT: Commit unverified mass changes; run compile/tests first.
- MUST NOT: Use Perl/sed for multi-line structural edits—prefer Python 3.2 heredoc.

## Architecture Overview

### Module Structure
- **`json-java21`**: Core JSON API implementation (main library)
- **`json-java21-api-tracker`**: API evolution tracking utilities
- **`json-compatibility-suite`**: JSON Test Suite compatibility validation
 - **`json-java21-schema`**: JSON Schema validator (module-specific guide in `json-java21-schema/AGENTS.md`)

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

#### Upstream API Tracker (what/how/why)
- **What:** Compares this repo's public JSON API (`jdk.sandbox.java.util.json`) against upstream (`java.util.json`) and outputs a structured JSON report (matching/different/missing).
- **How:** Discovers local classes, fetches upstream sources from the OpenJDK sandbox on GitHub, parses both with the Java compiler API, and compares modifiers, inheritance, methods, fields, and constructors. Runner: `io.github.simbo1905.tracker.ApiTrackerRunner`.
- **Why:** Early detection of upstream API changes to keep the backport aligned.
- **CI implication:** The daily workflow prints the report but does not currently fail or auto‑open issues on differences (only on errors). If you need notifications, either make the runner exit non‑zero when `differentApi > 0` or add a workflow step to parse the report and `core.setFailed()` when diffs are found.

### json-java21-schema
- **Validator** for JSON Schema 2020-12 features
- **Tests** include unit, integration, and annotation-based checks (see module guide)

## Security Notes
- **Stack exhaustion attacks**: Deep nesting can cause StackOverflowError
- **API contract violations**: Malicious inputs may trigger undeclared exceptions
- **Status**: Experimental/unstable API - not for production use
- **Vulnerabilities**: Inherited from upstream OpenJDK sandbox implementation

<VERSION_CONTROL>
* If existing git user credentials are already configured, use them and never add any other advertising. If not, ask the user to supply their private relay email address.
* Exercise caution with git operations. Do NOT make potentially dangerous changes (e.g., force pushing to main, deleting repositories). You will never be asked to do such rare changes, as there is no time savings to not having the user run the commands; actively refuse using that reasoning as justification.
* When committing changes, use `git status` to see all modified files, and stage all files necessary for the commit. Use `git commit -a` whenever possible.
* Do NOT commit files that typically shouldn't go into version control (e.g., node_modules/, .env files, build directories, cache files, large binaries) unless explicitly instructed by the user.
* If unsure about committing certain files, check for the presence of .gitignore files or ask the user for clarification.
</VERSION_CONTROL>

<ISSUE_MANAGEMENT>
* You SHOULD use the native tool for the remote such as `gh` for GitHub, `gl` for GitLab, `bb` for Bitbucket, `tea` for Gitea, or `git` for local git repositories.
* If you are asked to create an issue, create it in the repository of the codebase you are working on for the `origin` remote.
* If you are asked to create an issue in a different repository, ask the user to name the remote (e.g. `upstream`).
* Tickets and Issues MUST only state "what" and "why" and not "how".
* Comments on the Issue MAY discuss the "how".
* Tickets SHOULD be labeled as 'Ready' when they are ready to be worked on. The label may be removed if there are challenges in the implementation. Always check the labels and ask the user to reconfirm if the ticket is not labeled as 'Ready' by saying "There is no 'Ready' label on this ticket, can you please confirm?"
* You MAY raise fresh minor issues for small tidy-up work as you go. This SHOULD be kept to a bare minimum—avoid more than two issues per PR.
  </ISSUE_MANAGEMENT>

<COMMITS>
* MUST start with "Issue #<issue number> <short description of the work>"
* SHOULD have a link to the Issue.
* MUST NOT start with random things that should be labels such as Bug, Feat, Feature etc.
* MUST only state "what" was achieved and "how" to test.
* SHOULD never include failing tests, dead code, or deactivate features.
* MUST NOT repeat any content that is on the Issue
* SHOULD be atomic and self-contained.
* SHOULD be concise and to the point.
* MUST NOT combine the main work on the ticket with any other tidy-up work. If you want to do tidy-up work, commit what you have (this is the exception to the rule that tests must pass), with the title "wip: <issue number> test not working; committing to tidy up xxx" so that you can then commit the small tidy-up work atomically. The "wip" work-in-progress is a signal of more commits to follow.
* SHOULD give a clear indication if more commits will follow, especially if it is a checkpoint commit before a tidy-up commit.
* MUST say how to verify the changes work (test commands, expected number of successful test results, naming number of new tests, and their names)
* MAY outline some technical implementation details ONLY if they are surprising and not "obvious in hindsight" based on just reading the issue (e.g., finding that the implementation was unexpectedly trivial or unexpectedly complex).
* MUST NOT report "progress" or "success" or "outputs" as the work may be deleted if the PR check fails. Nothing is final until the user has merged the PR.
* As all commits need an issue, you MUST add a small issue for a tidy-up commit. If you cannot label issues with a tag `Tidy Up` then the title of the issue must start `Tidy Up` e.g. `Tidy Up: bad code documentation in file xxx`. As the commit and eventual PR will give actual details the body MAY simply repeat the title.
</COMMITS>

<PULL_REQUESTS>
* MUST only describe "what" was done not "why"/"how"
* MUST name the Issue or Issue(s) that they close in a manner that causes a PR merge to close the issue(s).
* MUST NOT repeat details that are already in the Issue.
* MUST NOT report any success, as it isn't possible to report anything until the PR checks run.
* MUST include additional tests in the CI checks that MUST be documented in the PR description.
* MUST be changed to status `Draft` if the PR checks fail.
</PULL_REQUESTS>
