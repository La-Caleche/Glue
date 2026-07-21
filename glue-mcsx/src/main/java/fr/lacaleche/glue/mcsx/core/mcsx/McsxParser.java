package fr.lacaleche.glue.mcsx.core.mcsx;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the {@link McsxTokenizer} token stream into the {@code .mcsx} AST. Two entry points:
 * {@link #parse(String)} for a bare document with a single root and no imports, and
 * {@link #parseDocument(String)} for a full document with an {@code <import/>} prelude.
 *
 * <p>The grammar is deliberately tiny: exactly one root element (after the optional import
 * prelude), no expression language in bindings ({@code {ref}}/{@code {{ref}}} are references),
 * and loud positioned errors ({@link McsxParseException}) on anything malformed.
 */
public final class McsxParser {

    private final List<Token> tokens;
    private int index;

    private McsxParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Parses a document that must not declare any {@code <import/>}. */
    public static McsxElement parse(String source) {
        return new McsxParser(McsxTokenizer.tokenize(source)).parseSingleRoot();
    }

    /** Parses a full document: {@code <import/>} prelude followed by a single root element. */
    public static McsxDocument parseDocument(String source) {
        return new McsxParser(McsxTokenizer.tokenize(source)).parseFullDocument();
    }

    private McsxElement parseSingleRoot() {
        skipTrivia();
        if (isImportAhead()) {
            Token t = peek();
            throw new McsxParseException("<import> is not allowed here", t.line(), t.column());
        }
        McsxElement root = parseElement();
        skipTrivia();
        expectEof();
        return root;
    }

    private McsxDocument parseFullDocument() {
        Map<String, String> imports = new LinkedHashMap<>();
        skipTrivia();
        while (isImportAhead()) {
            parseImport(imports);
            skipTrivia();
        }
        McsxElement root = parseElement();
        skipTrivia();
        expectEof();
        return new McsxDocument(imports, root);
    }

    private void parseImport(Map<String, String> imports) {
        Token lt = expect(Token.Type.LT);
        expect(Token.Type.TAG_NAME);
        List<McsxAttribute> attributes = parseAttributes();
        expect(Token.Type.TAG_SELF_END, "<import> must be self-closing ('/>')");

        String name = null;
        String from = null;
        for (McsxAttribute attribute : attributes) {
            if (attribute.binding()) {
                throw new McsxParseException(
                        "<import> attribute '" + attribute.name() + "' must be a literal, not a binding",
                        lt.line(), lt.column());
            }
            switch (attribute.name()) {
                case "name" -> name = attribute.value();
                case "from" -> from = attribute.value();
                default -> throw new McsxParseException(
                        "unknown <import> attribute '" + attribute.name() + "'", lt.line(), lt.column());
            }
        }
        if (name == null || name.isEmpty()) {
            throw new McsxParseException("<import> requires a non-empty name=\"…\"", lt.line(), lt.column());
        }
        if (from == null || from.isEmpty()) {
            throw new McsxParseException("<import> requires a non-empty from=\"…\"", lt.line(), lt.column());
        }
        if (imports.containsKey(name)) {
            throw new McsxParseException("duplicate <import> name '" + name + "'", lt.line(), lt.column());
        }
        imports.put(name, from);
    }

    private McsxElement parseElement() {
        Token lt = expect(Token.Type.LT);
        Token name = expect(Token.Type.TAG_NAME);
        if (name.value().equals("import")) {
            throw new McsxParseException("<import> is only allowed in the document prelude",
                    lt.line(), lt.column());
        }
        List<McsxAttribute> attributes = parseAttributes();

        if (peek().type() == Token.Type.TAG_SELF_END) {
            next();
            return new McsxElement(name.value(), attributes, List.of(), lt.line(), lt.column());
        }
        expect(Token.Type.TAG_END);
        List<McsxContent> children = parseContent(name.value(), lt);
        return new McsxElement(name.value(), attributes, children, lt.line(), lt.column());
    }

    private List<McsxAttribute> parseAttributes() {
        List<McsxAttribute> attributes = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (peek().type() == Token.Type.ATTR_NAME) {
            Token name = next();
            if (!names.add(name.value())) {
                throw new McsxParseException("duplicate attribute '" + name.value() + "'",
                        name.line(), name.column());
            }
            if (peek().type() == Token.Type.EQ) {
                next();
                Token value = next();
                if (value.type() == Token.Type.STRING) {
                    attributes.add(new McsxAttribute(name.value(), value.value(), false));
                } else if (value.type() == Token.Type.BINDING) {
                    attributes.add(new McsxAttribute(name.value(), value.value(), true));
                } else {
                    throw new McsxParseException(
                            "expected a string or {binding} value after '=' for attribute '"
                                    + name.value() + "'", value.line(), value.column());
                }
            } else {
                attributes.add(new McsxAttribute(name.value(), "", false));
            }
        }
        return attributes;
    }

    private List<McsxContent> parseContent(String tag, Token open) {
        List<McsxContent> children = new ArrayList<>();
        while (true) {
            Token t = peek();
            switch (t.type()) {
                case EOF -> throw new McsxParseException(
                        "unterminated element <" + tag + ">, expected </" + tag + ">",
                        open.line(), open.column());
                case LT_SLASH -> {
                    next();
                    Token closeName = expect(Token.Type.TAG_NAME);
                    if (!closeName.value().equals(tag)) {
                        throw new McsxParseException(
                                "mismatched closing tag </" + closeName.value() + ">, expected </" + tag + ">",
                                closeName.line(), closeName.column());
                    }
                    expect(Token.Type.TAG_END);
                    return children;
                }
                case LT -> children.add(parseElement());
                case COMMENT -> next();
                case TEXT, INTERP -> {
                    McsxText text = parseText();
                    if (text != null) {
                        children.add(text);
                    }
                }
                default -> throw new McsxParseException(
                        "unexpected " + t.type() + " in content", t.line(), t.column());
            }
        }
    }

    private McsxText parseText() {
        List<McsxText.Part> raw = new ArrayList<>();
        while (peek().type() == Token.Type.TEXT || peek().type() == Token.Type.INTERP) {
            Token t = next();
            if (t.type() == Token.Type.TEXT) {
                raw.add(new McsxText.Part(t.value(), false));
            } else {
                raw.add(new McsxText.Part(t.value(), true));
            }
        }
        List<McsxText.Part> parts = normalize(raw);
        return parts.isEmpty() ? null : new McsxText(parts);
    }

    /**
     * Collapses each literal part's internal whitespace to a single space, strips leading
     * whitespace on the first part and trailing on the last, then drops any literal that
     * became empty. Binding parts pass through untouched.
     */
    private static List<McsxText.Part> normalize(List<McsxText.Part> raw) {
        List<McsxText.Part> collapsed = new ArrayList<>(raw.size());
        for (McsxText.Part part : raw) {
            if (part.binding()) {
                collapsed.add(part);
            } else {
                collapsed.add(new McsxText.Part(part.value().replaceAll("\\s+", " "), false));
            }
        }
        if (!collapsed.isEmpty()) {
            McsxText.Part first = collapsed.get(0);
            if (!first.binding()) {
                collapsed.set(0, new McsxText.Part(first.value().stripLeading(), false));
            }
            int last = collapsed.size() - 1;
            McsxText.Part end = collapsed.get(last);
            if (!end.binding()) {
                collapsed.set(last, new McsxText.Part(end.value().stripTrailing(), false));
            }
        }
        List<McsxText.Part> result = new ArrayList<>(collapsed.size());
        for (McsxText.Part part : collapsed) {
            if (part.binding() || !part.value().isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    private boolean isImportAhead() {
        return peek().type() == Token.Type.LT
                && peekAt(index + 1).type() == Token.Type.TAG_NAME
                && peekAt(index + 1).value().equals("import");
    }

    /**
     * Skips document-level trivia: comments and whitespace-only text runs (the whitespace
     * around the import prelude and the root element). Non-blank text at this level is left
     * in place so it surfaces as a positioned error.
     */
    private void skipTrivia() {
        while (true) {
            Token t = peek();
            if (t.type() == Token.Type.COMMENT
                    || (t.type() == Token.Type.TEXT && t.value().isBlank())) {
                next();
            } else {
                return;
            }
        }
    }

    private void expectEof() {
        if (peek().type() != Token.Type.EOF) {
            Token t = peek();
            throw new McsxParseException("unexpected content after root element", t.line(), t.column());
        }
    }

    private Token expect(Token.Type type) {
        return expect(type, "expected " + type);
    }

    private Token expect(Token.Type type, String message) {
        Token t = peek();
        if (t.type() != type) {
            throw new McsxParseException(message + " but found " + t.type(), t.line(), t.column());
        }
        return next();
    }

    private Token peek() {
        return peekAt(index);
    }

    private Token peekAt(int i) {
        return tokens.get(Math.min(i, tokens.size() - 1));
    }

    private Token next() {
        return tokens.get(index++);
    }
}
