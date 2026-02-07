package io.github.simbo1905.json.jtd.codegen;

import java.util.List;
import java.util.Map;

/// Complete AST for RFC 8927 JTD code generation.
/// Supports all schema forms: empty, type, enum, elements, properties, values, 
/// discriminator, ref, and nullable.
///
/// This AST is designed for stack-based code generation where each node
/// knows how to generate its own validation logic.
public final class JtdAst {
    private JtdAst() {}

    public sealed interface JtdNode permits 
            EmptyNode, TypeNode, EnumNode, ElementsNode, PropertiesNode, 
            ValuesNode, DiscriminatorNode, RefNode, NullableNode {}

    /// Root of a JTD document with metadata, definitions, and root schema.
    public record RootNode(
            String id,
            Map<String, JtdNode> definitions,
            JtdNode rootSchema
    ) {}

    /// Empty form {} - accepts any JSON value.
    public record EmptyNode() implements JtdNode {}

    /// Type form - validates primitive types.
    /// Type values: string, boolean, timestamp, int8, uint8, int16, uint16, 
    /// int32, uint32, float32, float64
    public record TypeNode(String type) implements JtdNode {}

    /// Enum form - validates string is one of allowed values.
    public record EnumNode(List<String> values) implements JtdNode {}

    /// Elements form - validates array where each element matches schema.
    public record ElementsNode(JtdNode schema) implements JtdNode {}

    /// Properties form - validates object with required/optional properties.
    public record PropertiesNode(
            Map<String, JtdNode> properties,
            Map<String, JtdNode> optionalProperties,
            boolean additionalProperties
    ) implements JtdNode {}

    /// Values form - validates object where all values match schema.
    public record ValuesNode(JtdNode schema) implements JtdNode {}

    /// Discriminator form - validates tagged unions.
    public record DiscriminatorNode(
            String discriminator,
            Map<String, JtdNode> mapping
    ) implements JtdNode {}

    /// Ref form - references a definition.
    public record RefNode(String ref) implements JtdNode {}

    /// Nullable wrapper - allows null in addition to wrapped schema.
    public record NullableNode(JtdNode wrapped) implements JtdNode {}
}
