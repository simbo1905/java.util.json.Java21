# json-java21 Module AGENTS.md

## Purpose
This module backports the upstream OpenJDK sandbox `java.util.json` API to Java 21.

## Upstream Source
- Repository: https://github.com/openjdk/jdk-sandbox
- Branch: `json` (NOT master!)
- Base path: `src/java.base/share/classes/`
- Public API: `java/util/json/*.java`
- Internal implementation: `jdk/internal/util/json/*.java`

## CRITICAL WARNING

**DO NOT DOWNLOAD THE REPOSITORY ZIP FILE!**

The jdk-sandbox repository is MASSIVE (the entire JDK). We only need ~19 small Java files.

**ALWAYS fetch individual files using raw GitHub URLs one at a time.**

## Sync Process

### Step 1: Prepare Fresh Download Area
```bash
rm -rf .tmp/upstream-sync
mkdir -p .tmp/upstream-sync/java/util/json
mkdir -p .tmp/upstream-sync/jdk/internal/util/json
```

### Step 2: Fetch Upstream Sources (ONE FILE AT A TIME)

**CRITICAL: Fetch each file individually using curl or wget with the raw GitHub URL.**

The URL pattern is:
```
https://raw.githubusercontent.com/openjdk/jdk-sandbox/json/src/java.base/share/classes/<path>
```

Note the branch is `json` in the URL path (NOT `refs/heads/json`, just `json`).

#### Public API files (~10 files):
```bash
curl -o .tmp/upstream-sync/java/util/json/Json.java \
  "https://raw.githubusercontent.com/openjdk/jdk-sandbox/json/src/java.base/share/classes/java/util/json/Json.java"

curl -o .tmp/upstream-sync/java/util/json/JsonArray.java \
  "https://raw.githubusercontent.com/openjdk/jdk-sandbox/json/src/java.base/share/classes/java/util/json/JsonArray.java"

...
```

#### Internal implementation files (~9 files):
```bash
curl -o .tmp/upstream-sync/jdk/internal/util/json/JsonArrayImpl.java \
  "https://raw.githubusercontent.com/openjdk/jdk-sandbox/json/src/java.base/share/classes/jdk/internal/util/json/JsonArrayImpl.java"

curl -o .tmp/upstream-sync/jdk/internal/util/json/JsonBooleanImpl.java \
  "https://raw.githubusercontent.com/openjdk/jdk-sandbox/json/src/java.base/share/classes/jdk/internal/util/json/JsonBooleanImpl.java"

...
```

#### Verify downloads succeeded:
```bash
# Should show X files (whatever is currently upstream)
ls -la .tmp/upstream-sync/java/util/json/

# Should show Y files (whatever is currently upstream)
ls -la .tmp/upstream-sync/jdk/internal/util/json/

# Check none are empty or HTML error pages
wc -l .tmp/upstream-sync/java/util/json/*.java
wc -l .tmp/upstream-sync/jdk/internal/util/json/*.java
```

### Step 3: Create Backported Structure
Create parallel structure in `.tmp/backported/` with our package names:

```bash
mkdir -p .tmp/backported/jdk/sandbox/java/util/json
mkdir -p .tmp/backported/jdk/sandbox/internal/util/json
```

### Step 4: Apply Backporting Transformations
For each downloaded file, apply these transformations using Python heredocs (not sed/perl for multi-line):

#### 4.1 Package Renaming
- `java.util.json` → `jdk.sandbox.java.util.json`
- `jdk.internal.util.json` → `jdk.sandbox.internal.util.json`

#### 4.2 Remove Preview Feature Annotations
Delete lines containing:
- `import jdk.internal.javac.PreviewFeature;`
- `@PreviewFeature(feature = PreviewFeature.Feature.JSON)`

#### 4.3 StableValue Polyfill
Upstream uses `jdk.internal.lang.stable.StableValue` which is not available in Java 21.

**Replace imports:**
- `import jdk.internal.lang.stable.StableValue;` → (remove, our polyfill is package-local)

**The polyfill `StableValue.java`** (already in our repo) provides:
- `StableValue.of()` - creates empty holder
- `orElse(T defaultValue)` - returns value or default
- `orElseSet(Supplier<T>)` - lazy initialization with double-checked locking
- `setOrThrow(T)` - one-time set
- `StableValue.supplier(Supplier<T>)` - memoizing supplier wrapper

This file is NOT from upstream and must be preserved during sync.

#### 4.4 DO NOT Convert JavaDoc to JEP 467 Markdown
If upstream uses `/** ... */` style, DO NOT convert them to our `/// ...` format; we will not edit the upstream files more than the absolute minimum to get them to run on Java 21. 

#### 4.5 Add JsonAssertionException (Our Addition)
The file `JsonAssertionException.java` is a local addition not in upstream. Preserve it.

#### 4.6 Preserve Demo File
The file `jdk/sandbox/demo/JsonDemo.java` is a local addition for demonstration purposes. Preserve it. Fix it. 

### Step 5: Verify Compilation with javac
Before copying to the main source tree, verify the backported code compiles:

```bash
# Find all Java files in the backported structure
find .tmp/backported -name "*.java" > .tmp/sources.txt

# Also include our polyfill and local additions
echo "json-java21/src/main/java/jdk/sandbox/internal/util/json/StableValue.java" >> .tmp/sources.txt
echo "json-java21/src/main/java/jdk/sandbox/java/util/json/JsonAssertionException.java" >> .tmp/sources.txt

# Compile with Java 21
javac --release 21 -d .tmp/classes @.tmp/sources.txt
```

### Step 6: Copy to Source Tree (After Verification)

Only after javac succeeds:

```bash
# Backup current sources (optional)
cp -r json-java21/src/main/java/jdk/sandbox .tmp/backup-sandbox

# Copy backported files (excluding our local additions)
cp .tmp/backported/jdk/sandbox/java/util/json/*.java \
   json-java21/src/main/java/jdk/sandbox/java/util/json/

cp .tmp/backported/jdk/sandbox/internal/util/json/*.java \
   json-java21/src/main/java/jdk/sandbox/internal/util/json/

# Restore our local additions if overwritten
# (StableValue.java, JsonAssertionException.java should not be in backported/)
```

The file `jdk/sandbox/demo/JsonDemo.java` should be the example code in our README.md, as it may have changed to reflect upstream changes. You MUST update the README.md to include examples of the upgraded code in this file, which you must MANUALLY VERIFY IS GOOD post-upgrade. 

### Step 7: Full Maven Build

```bash
$(command -v mvnd || command -v mvn || command -v ./mvnw) clean test -pl json-java21 -Djava.util.logging.ConsoleHandler.level=INFO
```

## Files That Are Local Additions (Preserve During Sync)

| File | Purpose |
|------|---------|
| `jdk/sandbox/internal/util/json/StableValue.java` | Java 21 polyfill for future JDK StableValue API |
| `jdk/sandbox/java/util/json/JsonAssertionException.java` | Custom exception for type assertion errors |
| `jdk/sandbox/demo/JsonDemo.java` | Demonstration/example code |

## Transformation Example

**Upstream `JsonStringImpl.java` (excerpt):**
```java
package jdk.internal.util.json;

import java.util.json.JsonString;
import jdk.internal.lang.stable.StableValue;

public final class JsonStringImpl implements JsonString, JsonValueImpl {
    private final StableValue<String> jsonStr = StableValue.of();
    // ...
}
```

**Backported version:**
```java
package jdk.sandbox.internal.util.json;

import jdk.sandbox.java.util.json.JsonString;
// StableValue is package-local, no import needed

public final class JsonStringImpl implements JsonString, JsonValueImpl {
    private final StableValue<String> jsonStr = StableValue.of();
    // ...
}
```

## Troubleshooting

### Compilation Errors After Sync
1. Check package names are correctly transformed
2. Verify StableValue polyfill is present
3. Check for new upstream APIs that may need additional polyfills

### Test Failures After Sync
1. Run with verbose logging: `-Djava.util.logging.ConsoleHandler.level=FINE`
2. Check if upstream changed method signatures
3. Review upstream commit history for breaking changes
