package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns {@code .mcsx} source into a flat {@link Token} stream. The tokenizer is mode-aware:
 * in {@code DATA} mode it reads content (text runs, {@code {{interpolations}}}, comments and
 * tag openers); once a tag opener is seen it switches to {@code TAG} mode and reads the tag
 * name, attributes and closer, then returns to {@code DATA}. A lone {@code {} in content is
 * literal — only {@code {{} opens an interpolation. There are no character/entity escapes.
 *
 * <p>All whitespace normalization of text runs is left to {@link McsxParser}; {@code TEXT}
 * tokens carry the raw substring. Malformed input throws {@link McsxParseException}.
 */
public final class McsxTokenizer {

    private enum Mode { DATA, TAG }

    private final String src;
    private final int length;
    private int pos;
    private int line = 1;
    private int column = 1;
    private Mode mode = Mode.DATA;
    private boolean expectTagName;

    public McsxTokenizer(String source) {
        this.src = source;
        this.length = source.length();
    }

    /** Tokenizes the whole input, including a terminating {@code EOF} token. */
    public static List<Token> tokenize(String source) {
        return new McsxTokenizer(source).run();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = next();
            tokens.add(token);
        } while (token.type() != Token.Type.EOF);
        return tokens;
    }

    private Token next() {
        return mode == Mode.TAG ? nextInTag() : nextInData();
    }

    private Token nextInData() {
        if (pos >= length) {
            return new Token(Token.Type.EOF, "", line, column);
        }
        int startLine = line;
        int startColumn = column;

        if (lookingAt("<!--")) {
            return readComment(startLine, startColumn);
        }
        if (lookingAt("</")) {
            advance();
            advance();
            mode = Mode.TAG;
            expectTagName = true;
            return new Token(Token.Type.LT_SLASH, "</", startLine, startColumn);
        }
        if (peek() == '<') {
            advance();
            mode = Mode.TAG;
            expectTagName = true;
            return new Token(Token.Type.LT, "<", startLine, startColumn);
        }
        if (lookingAt("{{")) {
            return readInterpolation(startLine, startColumn);
        }
        return readText(startLine, startColumn);
    }

    private Token nextInTag() {
        skipTagWhitespace();
        int startLine = line;
        int startColumn = column;
        if (pos >= length) {
            throw error("unterminated tag");
        }
        if (lookingAt("/>")) {
            advance();
            advance();
            mode = Mode.DATA;
            return new Token(Token.Type.TAG_SELF_END, "/>", startLine, startColumn);
        }
        char c = peek();
        if (c == '>') {
            advance();
            mode = Mode.DATA;
            return new Token(Token.Type.TAG_END, ">", startLine, startColumn);
        }
        if (c == '=') {
            advance();
            return new Token(Token.Type.EQ, "=", startLine, startColumn);
        }
        if (c == '"' || c == '\'') {
            return readString(startLine, startColumn);
        }
        if (c == '{') {
            return readBinding(startLine, startColumn);
        }
        if (isNameStart(c)) {
            String name = readName();
            if (expectTagName) {
                expectTagName = false;
                return new Token(Token.Type.TAG_NAME, name, startLine, startColumn);
            }
            return new Token(Token.Type.ATTR_NAME, name, startLine, startColumn);
        }
        throw error("unexpected character '" + c + "' in tag");
    }

    private Token readText(int startLine, int startColumn) {
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != '<' && !lookingAt("{{")) {
            sb.append(peek());
            advance();
        }
        return new Token(Token.Type.TEXT, sb.toString(), startLine, startColumn);
    }

    private Token readInterpolation(int startLine, int startColumn) {
        advance();
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < length && !lookingAt("}}")) {
            sb.append(peek());
            advance();
        }
        if (pos >= length) {
            throw error("unterminated interpolation, expected '}}'");
        }
        advance();
        advance();
        String ref = sb.toString().trim();
        if (ref.isEmpty()) {
            throw new McsxParseException("empty interpolation '{{}}'", startLine, startColumn);
        }
        validateReference(ref, startLine, startColumn);
        return new Token(Token.Type.INTERP, ref, startLine, startColumn);
    }

    private Token readComment(int startLine, int startColumn) {
        advance();
        advance();
        advance();
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < length && !lookingAt("-->")) {
            sb.append(peek());
            advance();
        }
        if (pos >= length) {
            throw error("unterminated comment, expected '-->'");
        }
        advance();
        advance();
        advance();
        return new Token(Token.Type.COMMENT, sb.toString(), startLine, startColumn);
    }

    private Token readString(int startLine, int startColumn) {
        char quote = peek();
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != quote) {
            sb.append(peek());
            advance();
        }
        if (pos >= length) {
            throw error("unterminated string literal");
        }
        advance();
        return new Token(Token.Type.STRING, sb.toString(), startLine, startColumn);
    }

    private Token readBinding(int startLine, int startColumn) {
        advance();
        StringBuilder sb = new StringBuilder();
        while (pos < length && peek() != '}') {
            sb.append(peek());
            advance();
        }
        if (pos >= length) {
            throw error("unterminated binding, expected '}'");
        }
        advance();
        String ref = sb.toString().trim();
        if (ref.isEmpty()) {
            throw new McsxParseException("empty binding '{}'", startLine, startColumn);
        }
        validateReference(ref, startLine, startColumn);
        return new Token(Token.Type.BINDING, ref, startLine, startColumn);
    }

    private static void validateReference(String ref, int line, int column) {
        int segmentStart = 0;
        for (int i = 0; i <= ref.length(); i++) {
            if (i < ref.length() && ref.charAt(i) != '.') {
                continue;
            }
            if (i == segmentStart || !isReferenceStart(ref.charAt(segmentStart))) {
                throw new McsxParseException("invalid binding reference '" + ref + "'", line, column);
            }
            for (int j = segmentStart + 1; j < i; j++) {
                if (!isReferencePart(ref.charAt(j))) {
                    throw new McsxParseException("invalid binding reference '" + ref + "'", line, column);
                }
            }
            segmentStart = i + 1;
        }
    }

    private static boolean isReferenceStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isReferencePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private String readName() {
        StringBuilder sb = new StringBuilder();
        while (pos < length && isNameChar(peek())) {
            sb.append(peek());
            advance();
        }
        return sb.toString();
    }

    private void skipTagWhitespace() {
        while (pos < length && isWhitespace(peek())) {
            advance();
        }
    }

    private boolean lookingAt(String s) {
        return src.startsWith(s, pos);
    }

    private char peek() {
        return src.charAt(pos);
    }

    private void advance() {
        char c = src.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    private McsxParseException error(String message) {
        return new McsxParseException(message, line, column);
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }
}
