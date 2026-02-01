package json.java21.jsonpath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/// Parser for JsonPath expressions into AST.
/// Implements a recursive descent parser for JsonPath syntax.
///
/// Supported syntax based on [...](https://goessner.net/articles/JsonPath/):
/// - $ : root element
/// - .name : child property access
/// - \['name'\] or \["name"\] : bracket notation property access
/// - \[n\] : array index (supports negative indices)
/// - \[start:end:step\] : array slice
/// - \[*\] or .* : wildcard
/// - .. : recursive descent
/// - \[n,m\] : union of indices
/// - \['a','b'\] : union of properties
/// - \[?(@.prop)\] : filter expression for existence
/// - \[?(@.prop op value)\] : filter expression with comparison
/// - \[(@.length-1)\] : script expression
final class JsonPathParser {

    private static final Logger LOG = Logger.getLogger(JsonPathParser.class.getName());

    private final String path;
    private int pos;

    private JsonPathParser(String path) {
        this.path = path;
        this.pos = 0;
    }

    /// Parses a JsonPath expression string into an AST.
    /// @param path the JsonPath expression to parse
    /// @return the parsed AST
    /// @throws NullPointerException if path is null
    /// @throws JsonPathParseException if the path is invalid
    public static JsonPathAst.Root parse(String path) {
        Objects.requireNonNull(path, "path must not be null");
        LOG.fine(() -> "Parsing JsonPath: " + path);
        return new JsonPathParser(path).parseRoot();
    }

    private JsonPathAst.Root parseRoot() {
        if (path.isEmpty() || path.charAt(0) != '$') {
            throw new JsonPathParseException("JsonPath must start with $", path, 0);
        }
        pos = 1; // skip $

        final var segments = new ArrayList<JsonPathAst.Segment>();

        while (pos < path.length()) {
            final var segment = parseSegment();
            if (segment != null) {
                segments.add(segment);
                LOG.finer(() -> "Parsed segment: " + segment);
            }
        }

        return new JsonPathAst.Root(segments);
    }

    private JsonPathAst.Segment parseSegment() {
        if (pos >= path.length()) {
            return null;
        }

        final char c = path.charAt(pos);

        return switch (c) {
            case '.' -> parseDotNotation();
            case '[' -> parseBracketNotation();
            default -> throw new JsonPathParseException("Unexpected character", path, pos);
        };
    }

    private JsonPathAst.Segment parseDotNotation() {
        pos++; // skip .

        if (pos >= path.length()) {
            throw new JsonPathParseException("Unexpected end of path after '.'", path, pos);
        }

        final char c = path.charAt(pos);

        // Check for recursive descent (..)
        if (c == '.') {
            pos++; // skip second .
            return parseRecursiveDescent();
        }

        // Check for wildcard (.*)
        if (c == '*') {
            pos++; // skip *
            return new JsonPathAst.Wildcard();
        }

        // Property name
        return parsePropertyName();
    }

    private JsonPathAst.RecursiveDescent parseRecursiveDescent() {
        if (pos >= path.length()) {
            throw new JsonPathParseException("Unexpected end of path after '..'", path, pos);
        }

        final char c = path.charAt(pos);

        // Check for ..* (recursive wildcard)
        if (c == '*') {
            pos++;
            return new JsonPathAst.RecursiveDescent(new JsonPathAst.Wildcard());
        }

        // Check for ..[
        if (c == '[') {
            // Parse the bracket notation but wrap the target
            final var segment = parseBracketNotation();
            return new JsonPathAst.RecursiveDescent(segment);
        }

        // Property name after ..
        final var property = parsePropertyName();
        return new JsonPathAst.RecursiveDescent(property);
    }

    private JsonPathAst.PropertyAccess parsePropertyName() {
        final int start = pos;

        // Parse until we hit a special character or end
        while (pos < path.length()) {
            final char c = path.charAt(pos);
            if (c == '.' || c == '[') {
                break;
            }
            pos++;
        }

        if (pos == start) {
            throw new JsonPathParseException("Expected property name", path, pos);
        }

        final var name = path.substring(start, pos);
        return new JsonPathAst.PropertyAccess(name);
    }

    private JsonPathAst.Segment parseBracketNotation() {
        pos++; // skip [

        if (pos >= path.length()) {
            throw new JsonPathParseException("Unexpected end of path after '['", path, pos);
        }

        final char c = path.charAt(pos);

        // Wildcard [*]
        if (c == '*') {
            pos++; // skip *
            expectChar(']');
            return new JsonPathAst.Wildcard();
        }

        // Filter expression [?(...)]
        if (c == '?') {
            return parseFilterExpression();
        }

        // Script expression [(...)], but not (?...)
        if (c == '(') {
            return parseScriptExpression();
        }

        // Quoted property name or union
        if (c == '\'' || c == '"') {
            return parseQuotedPropertyOrUnion();
        }

        // Number, slice, or union
        if (c == '-' || c == ':' || Character.isDigit(c)) {
            return parseNumberOrSliceOrUnion();
        }

        throw new JsonPathParseException("Unexpected character in bracket notation", path, pos);
    }

    private JsonPathAst.Filter parseFilterExpression() {
        pos++; // skip ?

        expectChar('(');

        final var expression = parseFilterContent();

        expectChar(')');
        expectChar(']');

        return new JsonPathAst.Filter(expression);
    }

    private JsonPathAst.FilterExpression parseFilterContent() {
        return parseLogicalOr();
    }

    private JsonPathAst.FilterExpression parseLogicalOr() {
        var left = parseLogicalAnd();
        skipWhitespace();

        while (pos + 1 < path.length() && path.substring(pos).startsWith("||")) {
            pos += 2;
            final var right = parseLogicalAnd();
            skipWhitespace();
            left = new JsonPathAst.LogicalFilter(left, JsonPathAst.LogicalOp.OR, right);
        }

        return left;
    }

    private JsonPathAst.FilterExpression parseLogicalAnd() {
        var left = parseLogicalUnary();
        skipWhitespace();

        while (pos + 1 < path.length() && path.substring(pos).startsWith("&&")) {
            pos += 2;
            final var right = parseLogicalUnary();
            skipWhitespace();
            left = new JsonPathAst.LogicalFilter(left, JsonPathAst.LogicalOp.AND, right);
        }

        return left;
    }

    private JsonPathAst.FilterExpression parseLogicalUnary() {
        skipWhitespace();

        if (pos < path.length() && path.charAt(pos) == '!') {
            pos++;
            final var operand = parseLogicalUnary();
            return new JsonPathAst.LogicalFilter(operand, JsonPathAst.LogicalOp.NOT, null);
        }

        return parseLogicalPrimary();
    }

    private JsonPathAst.FilterExpression parseLogicalPrimary() {
        skipWhitespace();
        if (pos >= path.length()) {
            throw new JsonPathParseException("Unexpected end of filter expression", path, pos);
        }

        if (path.charAt(pos) == '(') {
            pos++;
            final var expr = parseLogicalOr();
            skipWhitespace();
            expectChar(')');
            return expr;
        }

        // Atom (maybe part of a comparison)
        final var left = parseFilterAtom();
        skipWhitespace();

        if (pos < path.length() && isComparisonOpStart(path.charAt(pos))) {
            final var op = parseComparisonOp();
            skipWhitespace();
            final var right = parseFilterAtom();
            return new JsonPathAst.ComparisonFilter(left, op, right);
        }

        if (left instanceof JsonPathAst.PropertyPath pp) {
            return new JsonPathAst.ExistsFilter(pp);
        }

        return left;
    }

    private boolean isComparisonOpStart(char c) {
        return c == '=' || c == '!' || c == '<' || c == '>';
    }

    private JsonPathAst.FilterExpression parseFilterAtom() {
        skipWhitespace();

        if (pos >= path.length()) {
            throw new JsonPathParseException("Unexpected end of filter expression", path, pos);
        }

        final char c = path.charAt(pos);

        // Current element reference @
        if (c == '@') {
            return parseCurrentNodePath();
        }

        // String literal
        if (c == '\'' || c == '"') {
            return parseLiteralString();
        }

        // Number literal
        if (c == '-' || Character.isDigit(c)) {
            return parseLiteralNumber();
        }

        // Boolean literal
        if (path.substring(pos).startsWith("true")) {
            pos += 4;
            return new JsonPathAst.LiteralValue(true);
        }
        if (path.substring(pos).startsWith("false")) {
            pos += 5;
            return new JsonPathAst.LiteralValue(false);
        }

        // Null literal
        if (path.substring(pos).startsWith("null")) {
            pos += 4;
            return new JsonPathAst.LiteralValue(null);
        }

        throw new JsonPathParseException("Unexpected token in filter expression", path, pos);
    }

    private JsonPathAst.FilterExpression parseCurrentNodePath() {
        pos++; // skip @

        final var properties = new ArrayList<String>();

        while (pos < path.length() && path.charAt(pos) == '.') {
            pos++; // skip .
            final int start = pos;

            while (pos < path.length()) {
                final char c = path.charAt(pos);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    break;
                }
                pos++;
            }

            if (pos == start) {
                throw new JsonPathParseException("Expected property name after '.'", path, pos);
            }

            properties.add(path.substring(start, pos));
        }

        if (properties.isEmpty()) {
            // Just @ with no properties
            return new JsonPathAst.CurrentNode();
        }

        return new JsonPathAst.PropertyPath(properties);
    }

    private JsonPathAst.LiteralValue parseLiteralString() {
        final char quote = path.charAt(pos);
        pos++; // skip opening quote

        final int start = pos;
        while (pos < path.length() && path.charAt(pos) != quote) {
            if (path.charAt(pos) == '\\' && pos + 1 < path.length()) {
                pos++; // skip escape character
            }
            pos++;
        }

        if (pos >= path.length()) {
            throw new JsonPathParseException("Unterminated string literal", path, start);
        }

        final var value = path.substring(start, pos);
        pos++; // skip closing quote

        return new JsonPathAst.LiteralValue(value);
    }

    private JsonPathAst.LiteralValue parseLiteralNumber() {
        final int start = pos;

        if (path.charAt(pos) == '-') {
            pos++;
        }

        while (pos < path.length() && Character.isDigit(path.charAt(pos))) {
            pos++;
        }

        // Check for decimal point
        if (pos < path.length() && path.charAt(pos) == '.') {
            do {
                pos++;
            } while (pos < path.length() && Character.isDigit(path.charAt(pos)));
        }

        final var numStr = path.substring(start, pos);
        if (numStr.contains(".")) {
            return new JsonPathAst.LiteralValue(Double.parseDouble(numStr));
        } else {
            return new JsonPathAst.LiteralValue(Long.parseLong(numStr));
        }
    }

    private JsonPathAst.ComparisonOp parseComparisonOp() {
        if (path.substring(pos).startsWith("==")) {
            pos += 2;
            return JsonPathAst.ComparisonOp.EQ;
        }
        if (path.substring(pos).startsWith("!=")) {
            pos += 2;
            return JsonPathAst.ComparisonOp.NE;
        }
        if (path.substring(pos).startsWith("<=")) {
            pos += 2;
            return JsonPathAst.ComparisonOp.LE;
        }
        if (path.substring(pos).startsWith(">=")) {
            pos += 2;
            return JsonPathAst.ComparisonOp.GE;
        }
        if (path.charAt(pos) == '<') {
            pos++;
            return JsonPathAst.ComparisonOp.LT;
        }
        if (path.charAt(pos) == '>') {
            pos++;
            return JsonPathAst.ComparisonOp.GT;
        }

        throw new JsonPathParseException("Expected comparison operator", path, pos);
    }

    private JsonPathAst.ScriptExpression parseScriptExpression() {
        pos++; // skip (

        final int start = pos;
        int depth = 1;

        while (pos < path.length() && depth > 0) {
            final char c = path.charAt(pos);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            pos++;
        }

        if (depth != 0) {
            throw new JsonPathParseException("Unmatched parenthesis in script expression", path, start);
        }

        // pos is now past the closing )
        final var script = path.substring(start, pos - 1);

        expectChar(']');

        return new JsonPathAst.ScriptExpression(script);
    }

    private JsonPathAst.Segment parseQuotedPropertyOrUnion() {
        final var properties = new ArrayList<JsonPathAst.Segment>();

        while (true) {
            skipWhitespace();
            final var prop = parseQuotedProperty();
            properties.add(prop);
            skipWhitespace();

            if (pos >= path.length()) {
                throw new JsonPathParseException("Unexpected end of path in bracket notation", path, pos);
            }

            if (path.charAt(pos) == ']') {
                pos++; // skip ]
                break;
            }

            if (path.charAt(pos) == ',') {
                pos++; // skip ,
                continue;
            }

            throw new JsonPathParseException("Expected ',' or ']' in bracket notation", path, pos);
        }

        if (properties.size() == 1) {
            return properties.getFirst();
        }

        return new JsonPathAst.Union(properties);
    }

    private JsonPathAst.PropertyAccess parseQuotedProperty() {
        final char quote = path.charAt(pos);
        pos++; // skip opening quote

        final int start = pos;
        while (pos < path.length() && path.charAt(pos) != quote) {
            if (path.charAt(pos) == '\\' && pos + 1 < path.length()) {
                pos++; // skip escape character
            }
            pos++;
        }

        if (pos >= path.length()) {
            throw new JsonPathParseException("Unterminated string in bracket notation", path, start);
        }

        final var name = path.substring(start, pos);
        pos++; // skip closing quote

        return new JsonPathAst.PropertyAccess(name);
    }

    private JsonPathAst.Segment parseNumberOrSliceOrUnion() {
        // Collect all the numbers and operators to determine what we have
        final var elements = new ArrayList<Integer>(); // Integer values (null for missing)
        boolean hasColon = false;
        boolean hasComma = false;

        // Parse first element (maybe empty for [:end])
        if (path.charAt(pos) == ':') {
            elements.add(null); // empty start
            hasColon = true;
            pos++;
            // After initial ':', check if there's a number for end
            if (pos < path.length() && (Character.isDigit(path.charAt(pos)) || path.charAt(pos) == '-')) {
                elements.add(parseInteger());
            }
        } else {
            elements.add(parseInteger());
        }

        // Continue parsing
        while (pos < path.length()) {
            final char c = path.charAt(pos);

            if (c == ']') {
                pos++;
                break;
            }

            if (c == ':') {
                hasColon = true;
                pos++;
                // Parse next element or leave as null
                if (pos < path.length() && (Character.isDigit(path.charAt(pos)) || path.charAt(pos) == '-')) {
                    elements.add(parseInteger());
                } else if (pos < path.length() && path.charAt(pos) != ':' && path.charAt(pos) != ']') {
                    // Not a digit, not another colon, not end bracket - unexpected
                    throw new JsonPathParseException("Unexpected character after ':' in slice", path, pos);
                } else {
                    elements.add(null);
                }
            } else if (c == ',') {
                hasComma = true;
                pos++;
                skipWhitespace();
                elements.add(parseInteger());
            } else {
                throw new JsonPathParseException("Unexpected character in array subscript", path, pos);
            }
        }

        // Determine what we parsed
        if (hasColon) {
            // It's a slice [start:end:step]
            final Integer start = !elements.isEmpty() ? elements.get(0) : null;
            final Integer end = elements.size() > 1 ? elements.get(1) : null;
            final Integer step = elements.size() > 2 ? elements.get(2) : null;
            return new JsonPathAst.ArraySlice(start, end, step);
        }

        if (hasComma) {
            // It's a union [n,m,...]
            final var indices = new ArrayList<JsonPathAst.Segment>();
            for (final var elem : elements) {
                indices.add(new JsonPathAst.ArrayIndex(elem));
            }
            return new JsonPathAst.Union(indices);
        }

        // Single index
        return new JsonPathAst.ArrayIndex(elements.getFirst());
    }

    private int parseInteger() {
        final int start = pos;
        if (pos < path.length() && path.charAt(pos) == '-') {
            pos++;
        }
        while (pos < path.length() && Character.isDigit(path.charAt(pos))) {
            pos++;
        }
        if (pos == start || (pos == start + 1 && path.charAt(start) == '-')) {
            throw new JsonPathParseException("Expected integer", path, pos);
        }
        return Integer.parseInt(path.substring(start, pos));
    }

    private void expectChar(char expected) {
        if (pos >= path.length()) {
            throw new JsonPathParseException("Expected '" + expected + "' but reached end of path", path, pos);
        }
        if (path.charAt(pos) != expected) {
            throw new JsonPathParseException("Expected '" + expected + "'", path, pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < path.length() && Character.isWhitespace(path.charAt(pos))) {
            pos++;
        }
    }
}
