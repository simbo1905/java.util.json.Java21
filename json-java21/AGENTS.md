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
mkdir -p .tmp/backported/jdk/incubator/java/util/json
mkdir -p .tmp/backported/jdk/incubator/internal/util/json
```

### Step 4: Apply Backporting Transformations
For each downloaded file, apply these transformations using Python heredocs (not sed/perl for multi-line):

#### 4.1 Package Renaming
- `java.util.json` → `jdk.incubator.java.util.json`
- `jdk.internal.util.json` → `jdk.incubator.internal.util.json`

#### 4.2 Remove Preview Feature Annotations
Delete lines containing:
- `import jdk.internal.javac.PreviewFeature;`
- `@PreviewFeature(feature = PreviewFeature.Feature.JSON)`

#### 4.3 LazyConstant Polyfill
Upstream (since commit `c1a4f80`, 2026-02-05) uses `java.lang.LazyConstant` (implicit
`java.lang` import, no import line to rewrite) which is not available in Java 21.

**The polyfill `LazyConstant.java`** (already in our repo, package-local) provides:
- `LazyConstant.of(Supplier<T>)` - creates a lazy constant
- `.get()` - gets the value (computing if needed, double-checked locking)

Because the polyfill exposes the identical API and lives in the same (impl) package as the
upstream call sites, **no import or call-site rewrite is needed**; upstream `LazyConstant`
usages compile unchanged against the polyfill.

This file is NOT from upstream and must be preserved during sync. The legacy
`StableValue.java` polyfill (for the pre-`c1a4f80` upstream API) is unused dead code and is
removed during the incubator uplift.

#### 4.4 DO NOT Convert JavaDoc to JEP 467 Markdown
If upstream uses `/** ... */` style, DO NOT convert them to our `/// ...` format; we will not edit the upstream files more than the absolute minimum to get them to run on Java 21. 

#### 4.5 JsonAssertionException (Shipped Upstream)
Upstream at `c1a4f80` DOES ship `java/util/json/JsonAssertionException.java`; it is NOT a local
addition. Our copy is a minimized mechanical backport of the upstream file (copyright header,
javadoc and `@Serial serialVersionUID` stripped; behaviour identical). Take the upstream file
with the standard transforms of 4.1/4.2; do not treat it as local-only.

#### 4.6 Preserve Demo File
The file `jdk/incubator/demo/JsonDemo.java` is a local addition for demonstration purposes. Preserve it. Fix it. 

### Step 5: Verify Compilation with javac
Before copying to the main source tree, verify the backported code compiles:

```bash
# Find all Java files in the backported structure
find .tmp/backported -name "*.java" > .tmp/sources.txt

# Also include our polyfill
echo "json-java21/src/main/java/jdk/incubator/internal/util/json/LazyConstant.java" >> .tmp/sources.txt

# Compile with Java 21
javac --release 21 -d .tmp/classes @.tmp/sources.txt
```

### Step 6: Copy to Source Tree (After Verification)

Only after javac succeeds:

```bash
# Backup current sources (optional)
cp -r json-java21/src/main/java/jdk/incubator .tmp/backup-incubator

# Copy backported files (excluding our local additions)
cp .tmp/backported/jdk/incubator/java/util/json/*.java \
   json-java21/src/main/java/jdk/incubator/java/util/json/

cp .tmp/backported/jdk/incubator/internal/util/json/*.java \
   json-java21/src/main/java/jdk/incubator/internal/util/json/

# Restore our local additions if overwritten
# (LazyConstant.java should not be in backported/)
```

The file `jdk/incubator/demo/JsonDemo.java` should be the example code in our README.md, as it may have changed to reflect upstream changes. You MUST update the README.md to include examples of the upgraded code in this file, which you must MANUALLY VERIFY IS GOOD post-upgrade. 

### Step 7: Full Maven Build

```bash
$(command -v mvnd || command -v mvn || command -v ./mvnw) clean test -pl json-java21 -Djava.util.logging.ConsoleHandler.level=INFO
```

## Files That Are Local Additions (Preserve During Sync)

| File | Purpose |
|------|---------|
| `jdk/incubator/internal/util/json/LazyConstant.java` | Java 21 polyfill for the JDK `java.lang.LazyConstant` API used by upstream since `c1a4f80` |
| `jdk/incubator/demo/JsonDemo.java` | Demonstration/example code |

Note: `JsonAssertionException.java` is shipped upstream (see step 4.5) and
`StableValue.java` is unused legacy pending removal; neither is a local addition anymore.

## Transformation Example

**Upstream `JsonStringImpl.java` (excerpt, commit `c1a4f80`):**
```java
package jdk.internal.util.json;

import java.util.json.JsonString;

public final class JsonStringImpl implements JsonString, JsonValueImpl {
    private final LazyConstant<String> jsonStr = LazyConstant.of(this::initJsonStr);
    // ...
}
```

**Backported version:**
```java
package jdk.incubator.internal.util.json;

import jdk.incubator.java.util.json.JsonString;
// LazyConstant is package-local (our polyfill for java.lang.LazyConstant), no import needed

public final class JsonStringImpl implements JsonString, JsonValueImpl {
    private final LazyConstant<String> jsonStr = LazyConstant.of(this::initJsonStr);
    // ...
}
```

## Troubleshooting

### Compilation Errors After Sync
1. Check package names are correctly transformed
2. Verify LazyConstant polyfill is present
3. Check for new upstream APIs that may need additional polyfills

### Test Failures After Sync
1. Run with verbose logging: `-Djava.util.logging.ConsoleHandler.level=FINE`
2. Check if upstream changed method signatures
3. Review upstream commit history for breaking changes
