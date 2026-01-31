package json.java21.jsonpath;

import java.util.ArrayList;
import java.util.List;

import static json.java21.jsonpath.JsonPathAst.Segment;

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

            final var segments = new ArrayList<Segment>();
            while (true) {
                skipWs();
                if (eof()) break;

                if (peek('.') ) {
                    consume('.');
                    if (peek('*')) {
                        consume('*');
                        segments.add(new Segment.Wildcard());
                        continue;
                    }
                    final var name = parseIdentifier();
                    segments.add(new Segment.Child(name));
                    continue;
                }

                throw error("Unexpected character '" + cur() + "'");
            }

            return new JsonPathAst(List.copyOf(segments));
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

