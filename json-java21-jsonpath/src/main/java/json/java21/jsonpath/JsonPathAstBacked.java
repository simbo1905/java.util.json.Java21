package json.java21.jsonpath;

/// Marker for `JsonPath` implementations backed by a `JsonPathAst.Root`.
///
/// This is intentionally package-private so tests in this package can inspect internals,
/// while keeping the public API surface small.
interface JsonPathAstBacked {
    JsonPathAst.Root ast();
}

