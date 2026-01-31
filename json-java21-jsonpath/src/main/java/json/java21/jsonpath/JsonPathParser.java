package json.java21.jsonpath;

import java.util.ArrayList;
import java.util.List;

import json.java21.jsonpath.BoolExpr.CmpOp;
import static json.java21.jsonpath.JsonPathAst.Step;

sealed interface JsonPathParser permits JsonPathParser.Impl {

    static JsonPathAst parse(String path) {
        return new Impl(path).parse();
    }

    final class Impl implements JsonPathParser {
        private final String in;
        private int i;

        Impl(String in) {
            this.in = in;
        }

        JsonPathAst parse() {
            skipWs();
            expect('$');

            final var steps = new ArrayList<Step>();
            while (true) {
                skipWs();
                if (eof()) break;

                if (peek('.')) {
                    consume('.');
                    if (peek('.')) {
                        consume('.');
                        steps.add(parseRecursiveDescentSelector());
                    } else if (peek('*')) {
                        consume('*');
                        steps.add(new Step.Wildcard());
                    } else {
                        steps.add(new Step.Child(parseIdentifier()));
                    }
                    continue;
                }

                if (peek('[')) {
                    steps.add(parseBracketStep());
                    continue;
                }

                throw error("Unexpected character '" + cur() + "'");
            }

            return new JsonPathAst(List.copyOf(steps));
        }

        private Step parseRecursiveDescentSelector() {
            if (peek('*')) {
                consume('*');
                return new Step.RecursiveDescent(new Step.RecursiveSelector.Wildcard());
            }
            return new Step.RecursiveDescent(new Step.RecursiveSelector.Name(parseIdentifier()));
        }

        private Step parseBracketStep() {
            expect('[');
            skipWs();

            if (peek('*')) {
                consume('*');
                skipWs();
                expect(']');
                return new Step.Wildcard();
            }

            if (peek('?')) {
                consume('?');
                skipWs();
                expect('(');
                final var predicate = parseBoolExpr();
                expect(')');
                skipWs();
                expect(']');
                return new Step.Filter(predicate);
            }

            if (peek('(')) {
                consume('(');
                final var expr = parseValueExpr();
                expect(')');
                skipWs();
                expect(']');
                return new Step.ScriptIndex(expr);
            }

            if (peek('\'') || peek('"')) {
                final var selectors = new ArrayList<Step.Selector>();
                selectors.add(new Step.Selector.Name(parseQuotedString()));
                skipWs();
                while (peek(',')) {
                    consume(',');
                    skipWs();
                    selectors.add(new Step.Selector.Name(parseQuotedString()));
                    skipWs();
                }
                expect(']');
                if (selectors.size() == 1) {
                    return new Step.Child(((Step.Selector.Name) selectors.getFirst()).name());
                }
                return new Step.Union(List.copyOf(selectors));
            }

            // number / union / slice
            final Integer startOrIndex;
            if (peek(':')) {
                startOrIndex = null;
            } else {
                startOrIndex = parseSignedInt();
            }
            skipWs();

            if (peek(':')) {
                // slice: [start?:end?]
                consume(':');
                skipWs();
                final Integer end;
                if (peek(']')) {
                    end = null;
                } else {
                    end = parseSignedInt();
                }
                skipWs();
                expect(']');
                return new Step.ArraySlice(startOrIndex, end);
            }

            if (peek(',')) {
                final var selectors = new ArrayList<Step.Selector>();
                selectors.add(new Step.Selector.Index(requireNonNullInt(startOrIndex)));
                while (peek(',')) {
                    consume(',');
                    skipWs();
                    selectors.add(new Step.Selector.Index(parseSignedInt()));
                    skipWs();
                }
                expect(']');
                return new Step.Union(List.copyOf(selectors));
            }

            expect(']');
            return new Step.ArrayIndex(requireNonNullInt(startOrIndex));
        }

        private int requireNonNullInt(Integer i) {
            if (i == null) throw error("Expected integer");
            return i;
        }

        private String parseQuotedString() {
            if (eof()) throw error("Expected string, got <eof>");
            final char q = cur();
            if (q != '\'' && q != '"') throw error("Expected quote, got '" + cur() + "'");
            consume(q);
            final var sb = new StringBuilder();
            while (!eof()) {
                final char c = cur();
                if (c == q) {
                    consume(q);
                    return sb.toString();
                }
                // JsonPath examples use simple quoted names; support basic escapes for completeness
                if (c == '\\') {
                    consume('\\');
                    if (eof()) throw error("Unterminated escape sequence");
                    final char e = cur();
                    consume(e);
                    sb.append(e);
                    continue;
                }
                consume(c);
                sb.append(c);
            }
            throw error("Unterminated string literal");
        }

        private BoolExpr parseBoolExpr() {
            final var left = parseValueExpr();
            skipWs();
            if (peek(')')) {
                if (left instanceof ValueExpr.Path p) {
                    return new BoolExpr.Exists(p);
                }
                throw error("Filter expression without operator must be a path, got: " + left);
            }

            final var op = parseCmpOp();
            skipWs();
            final var right = parseValueExpr();
            return new BoolExpr.Compare(op, left, right);
        }

        private CmpOp parseCmpOp() {
            if (eof()) throw error("Expected operator, got <eof>");

            if (peek('<')) {
                consume('<');
                if (peek('=')) {
                    consume('=');
                    return CmpOp.LE;
                }
                return CmpOp.LT;
            }
            if (peek('>')) {
                consume('>');
                if (peek('=')) {
                    consume('=');
                    return CmpOp.GE;
                }
                return CmpOp.GT;
            }
            if (peek('=')) {
                consume('=');
                expect('=');
                return CmpOp.EQ;
            }
            if (peek('!')) {
                consume('!');
                expect('=');
                return CmpOp.NE;
            }

            throw error("Expected comparison operator");
        }

        private ValueExpr parseValueExpr() {
            var expr = parsePrimary();
            skipWs();
            while (!eof() && (peek('+') || peek('-'))) {
                final char op = cur();
                consume(op);
                skipWs();
                final var rhs = parsePrimary();
                expr = new ValueExpr.Arith(op == '+' ? ValueExpr.ArithOp.ADD : ValueExpr.ArithOp.SUB, expr, rhs);
                skipWs();
            }
            return expr;
        }

        private ValueExpr parsePrimary() {
            skipWs();
            if (eof()) throw error("Expected expression, got <eof>");

            if (peek('@')) {
                consume('@');
                final var parts = new ArrayList<String>();
                while (!eof() && peek('.')) {
                    consume('.');
                    parts.add(parseIdentifier());
                }
                return new ValueExpr.Path(List.copyOf(parts));
            }

            if (peek('\'') || peek('"')) {
                return new ValueExpr.Str(parseQuotedString());
            }

            if (peek('(')) {
                consume('(');
                final var inner = parseValueExpr();
                expect(')');
                return inner;
            }

            if (peek('-') || isDigit(cur())) {
                return new ValueExpr.Num(parseNumber());
            }

            throw error("Unexpected token in expression: '" + cur() + "'");
        }

        private double parseNumber() {
            final int start = i;
            if (peek('-')) consume('-');
            if (eof() || !isDigit(cur())) throw error("Expected number");
            while (!eof() && isDigit(cur())) i++;
            if (!eof() && peek('.')) {
                consume('.');
                while (!eof() && isDigit(cur())) i++;
            }
            final var raw = in.substring(start, i);
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ex) {
                throw error("Invalid number: " + raw);
            }
        }

        private int parseSignedInt() {
            final int start = i;
            if (peek('-')) consume('-');
            if (eof() || !isDigit(cur())) throw error("Expected integer");
            while (!eof() && isDigit(cur())) i++;
            final var raw = in.substring(start, i);
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                throw error("Invalid integer: " + raw);
            }
        }

        private String parseIdentifier() {
            if (eof()) throw error("Expected identifier, got <eof>");
            final int start = i;
            if (!isIdentStart(cur())) throw error("Expected identifier, got '" + cur() + "'");
            i++;
            while (!eof() && isIdentPart(cur())) {
                i++;
            }
            return in.substring(start, i);
        }

        private boolean isIdentStart(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
        }

        private boolean isIdentPart(char c) {
            return isIdentStart(c) || (c >= '0' && c <= '9');
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("Expected '" + c + "', got " + (eof() ? "<eof>" : "'" + cur() + "'"));
            }
            consume(c);
        }

        private boolean peek(char c) {
            return !eof() && cur() == c;
        }

        private void consume(char c) {
            assert cur() == c : "internal error: consume expected '" + c + "' got '" + cur() + "'";
            i++;
        }

        private void skipWs() {
            while (!eof()) {
                final char c = cur();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private boolean eof() {
            return i >= in.length();
        }

        private char cur() {
            return in.charAt(i);
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at index " + i + " in: " + in);
        }
    }
}

