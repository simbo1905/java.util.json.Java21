package io.github.simbo1905.json.jtd.codegen;

import java.util.List;
import java.util.Map;

/// Minimal AST for the experimental "flat JTD → ESM validator" code generator.
///
/// Supported JTD subset:
/// - root: `properties`, `optionalProperties`, `metadata.id`
/// - leaf: `{}` (empty form), `{ "type": ... }`, `{ "enum": [...] }`
sealed interface JtdNode permits JtdNode.SchemaNode, JtdNode.PropertyNode, JtdNode.TypeNode, JtdNode.EnumNode, JtdNode.EmptyNode {

    record SchemaNode(
            String id, // metadata.id
            Map<String, PropertyNode> properties,
            Map<String, PropertyNode> optionalProperties
    ) implements JtdNode {}

    record PropertyNode(String name, JtdNode type) implements JtdNode {}

    /// JTD primitive type keyword as a string, e.g. "string", "int32", "timestamp".
    record TypeNode(String type) implements JtdNode {}

    /// Enum values (strings only in RFC 8927).
    record EnumNode(List<String> values) implements JtdNode {}

    /// Empty form `{}`: accepts any JSON value.
    record EmptyNode() implements JtdNode {}
}

