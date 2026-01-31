# Design choices: numeric handling (`JsonNumber`, BigDecimal/BigInteger)

This repository is a **backport** of the upstream OpenJDK sandbox `java.util.json` work (mirrored here as `jdk.sandbox.java.util.json`). That matters for “why did X disappear?” questions:

- This repo intentionally avoids *inventing* new public API shapes that diverge from upstream, because doing so makes syncing harder and breaks the point of the backport.
- When upstream removes or reshapes API, this repo follows.

## What changed (the “story”)

Older revisions of this backport carried some convenience entry points that accepted `java.math.BigDecimal` and `java.math.BigInteger` when building JSON numbers.

During the last upstream sync, those entry points were removed. **That is consistent with the upstream design direction**: keep `JsonNumber`’s public surface area small and make **lossless numeric interoperability** flow through the **lexical JSON number text** (`JsonNumber.toString()`), not through a growing matrix of overloads.

Put differently: the design is “JSON numbers are text first”, not “JSON numbers are a Java numeric tower”.

## Why upstream prefers `String` (and why BigDecimal constructors are a footgun)

### 1) JSON numbers are arbitrary precision *text*

RFC 8259 defines the *syntax* of a JSON number; it does **not** define a fixed precision or a single canonical format. The API aligns with that by treating the number as a string that:

- can be preserved exactly when parsed from a document
- can be created from a string when you need exact control

### 2) `BigDecimal`/`BigInteger` introduce formatting policy into the API

If `JsonNumber` has `of(BigDecimal)` / `of(BigInteger)`:

- which textual form should be used (`toString()` vs `toPlainString()`)?  
- should `-0` be preserved, normalized, or rejected?
- should `1e2` round-trip as `1e2` or normalize to `100`?

Any choice becomes a **semantic commitment**: it changes `toString()`, equality and hash behavior, and round-trip characteristics.

Upstream avoids baking those policy decisions into the core JSON API by:

- providing `JsonNumber.of(String)` as the “I know what text I want” factory
- documenting that you can always interoperate with arbitrary precision Java numerics by converting *from* `toString()`

This intent is explicitly documented in `JsonNumber`’s own `@apiNote`.

### 3) Minimal factories avoid overload explosion

JSON object/array construction in this API already leans toward:

- immutable values
- static factories (`...of(...)`)
- pattern matching / sealed types when consuming values

That style is a natural fit for “a few sharp entry points” rather than the legacy OO pattern of ever-expanding constructor overloads for every “convenient” numeric type.

## Recommended recipes (lossless + explicit)

### Parse → BigDecimal (lossless)

```java
var n = (JsonNumber) Json.parse("3.141592653589793238462643383279");
var bd = new BigDecimal(n.toString()); // exact
```

### Parse → BigInteger (lossless, when integral)

```java
var n = (JsonNumber) Json.parse("1.23e2");
var bi = new BigDecimal(n.toString()).toBigIntegerExact(); // 123
```

### BigDecimal → JsonNumber (pick your textual policy)

If you want to preserve the *mathematical* value without scientific notation:

```java
var bd = new BigDecimal("1000");
var n = JsonNumber.of(bd.toPlainString()); // "1000"
```

If you’re fine with scientific notation when `BigDecimal` chooses it:

```java
var bd = new BigDecimal("1E+3");
var n = JsonNumber.of(bd.toString()); // "1E+3" (still valid JSON number text)
```

### JSON lexical preservation is not numeric normalization

Two JSON numbers can represent the same numeric value but still be different JSON texts:

```java
var a = (JsonNumber) Json.parse("1e2");
var b = (JsonNumber) Json.parse("100");
assert !a.toString().equals(b.toString()); // lexical difference preserved
```

If your application needs *numeric* equality or canonicalization, perform it explicitly with `BigDecimal` (or your own policy), rather than relying on the JSON value object to do it implicitly.

## Runnable examples

This document’s examples are mirrored in code:

- `json-java21/src/test/java/jdk/sandbox/java/util/json/examples/DesignChoicesExamples.java`
- `json-java21/src/test/java/jdk/sandbox/java/util/json/DesignChoicesNumberExamplesTest.java`

