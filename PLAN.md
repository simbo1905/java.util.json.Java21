# Discriminator Validation Refactor Plan

## Objectives
- Document the intended single-path validation architecture and discriminator rules before touching implementation.
- Additive-first: introduce tests and supporting guards ahead of removing legacy recursive validation.
- Enforce RFC 8927 discriminator constraints at compilation while preserving stack-based runtime checks with the discriminator tag exemption.
- Retire duplicate validation paths only after documentation and tests clearly describe the target behavior.

## Sequenced Work Breakdown

1. **Documentation First** ✅
   - Expand `json-java21-jtd/ARCHITECTURE.md` with explicit guidance on single stack-based validation, discriminator tag exemption, and compile-time schema constraints.
   - Audit other affected docs (e.g., module README, AGENTS addenda) to ensure contributors receive clear instructions prior to code edits.

2. **Reconnaissance & Impact Analysis** ✅
   - Inventory all usages of `JtdSchema.*#validate` and catalogue callers/tests that rely on the recursive path.
   - Trace how `pushChildFrames` and `PropertiesSchema` cooperate today to support discriminator handling and identify touchpoints for additive changes.

3. **Test Design (Additive Stage)** ✅
   - Keep `JtdSpecIT` focused solely on `validation.json` scenarios so we only assert that published docs validate as expected.
   - Add `CompilerSpecIT` to execute `invalid_schemas.json`, asserting compilation failures with deterministic messages instead of skipping them.
   - Introduce `CompilerTest` for incremental compiler-focused unit cases that exercise new stack artifacts while staying within JUL logging discipline (INFO log at method start via logging helper).

4. **Compile-Time Guards (Additive)** ✅
   - Introduce validation during schema compilation to reject non-`PropertiesSchema` discriminator mappings, nullable mappings, and mappings that shadow the discriminator key.
   - Emit deterministic exception messages with precise schema paths to keep property-based tests stable.

5. **Stack Engine Enhancements (Additive)**
   - Teach the stack-based validator to propagate an "exempt key" for discriminator payload evaluation before deleting any recursive logic.
   - Adjust `PropertiesSchema` handling to skip the exempt key when checking required/optional members and additionalProperties while preserving crumb/schemaPath reporting.

6. **Retire Recursive `validate` Path (Removal Stage)**
   - After new tests pass with the enhanced stack engine, remove the per-schema `validate` implementations and their call sites.
   - Clean up dead code, imports, and update any remaining references discovered by static analysis.

7. **Verification & Cleanup**
   - Run targeted tests with mandated JUL logging levels (FINE/FINEST for new cases) followed by the full suite at INFO to confirm conformity.
   - Revisit documentation to confirm implemented behavior matches the authored guidance and adjust if gaps remain.

## Risks & Mitigations
- **Hidden Recursive Callers**: Use `rg "\.validate\("` and compiler errors to surface stragglers before deleting the recursive path.
- **Error Message Drift**: Lock expected `schemaPath`/`instancePath` outputs in tests once new guards land to prevent flakiness.
- **Temporal Test Failures**: Stage code changes so new tests are introduced alongside the supporting implementation to avoid committing failing tests.
- **Documentation Drift**: Re-review docs post-implementation to ensure instructions still match code.

## Out of Scope
- Remote reference compilation or runtime changes (handled by MVF initiative).
- Adjustments to global logging frameworks beyond what is necessary for new tests.
- Broader API redesigns outside discriminator handling and validation-path consolidation.

## Documentation Targets ✅
- json-java21-jtd/ARCHITECTURE.md: add single-path validation, discriminator tag exemption, compile-time guard details.
- README.md (module-level if present): summarize discriminator constraints and reference architecture section.
- AGENTS.md references (if needed): reinforce documentation-before-code steps for discriminator work.

## Test Targets
- New compile-time rejection test suite ensuring discriminator mapping violations throw with deterministic schema paths.
- Runtime tests for RFC 8927 §3.3.8 scenarios (missing tag, non-string tag, unknown mapping, payload mismatch, success).
- Regression coverage ensuring tag exemption prevents additionalProperties violations when payload omits discriminator.
- Removal/update of tests invoking `JtdSchema.validate` only after stack engine passes new scenarios.
