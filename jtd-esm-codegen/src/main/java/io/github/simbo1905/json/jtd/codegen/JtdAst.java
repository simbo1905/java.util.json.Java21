package io.github.simbo1905.json.jtd.codegen;

import java.util.List;
import java.util.Map;

/// Minimal AST for the experimental "flat JTD → ESM validator" code generator.
///
/// Supported JTD subset:
/// - root: `properties`, `optionalProperties`, `metadata.id`
/// - leaf: `{}` (empty form), `{ "type": ... }`, `{ "enum": [...] }`
public final class JtdAst {
    private JtdAst() {}

    public sealed interface JtdNode permits SchemaNode, PropertyNode, TypeNode, EnumNode, EmptyNode {}

    public record SchemaNode(
            String id, // metadata.id
            Map<String, PropertyNode> properties,
            Map<String, PropertyNode> optionalProperties
    ) implements JtdNode {}

    public record PropertyNode(String name, JtdNode type) implements JtdNode {}

    /// JTD primitive type keyword as a string, e.g. "string", "int32", "timestamp".
    public record TypeNode(String type) implements JtdNode {}

    /// Enum values (strings only in RFC 8927).
    public record EnumNode(List<String> values) implements JtdNode {}

    /// Empty form `{}`: accepts any JSON value.
    public record EmptyNode() implements JtdNode {}
}

