OpenRPC test resources

Provenance and license
- Source (meta‑schema): https://github.com/open-rpc/meta-schema (Apache-2.0)
- Source (examples): https://github.com/open-rpc/examples (Apache-2.0)

These files are copied verbatim or lightly adapted for fair use in research and education to test the JSON Schema validator in this repository. See the original repositories for authoritative copies and full license terms.

Notes
- The `schema.json` here is a minimal, self‑contained subset of the OpenRPC meta‑schema focused on validating overall document shape used by the included examples. It intentionally avoids external `$ref` to remain compatible with the current validator (which supports local `$ref`).
- Example documents live under `examples/`. Files containing `-bad-` are intentionally invalid variants used for negative tests.

