package fr.lacaleche.glue.mcsx.client.style;

import fr.lacaleche.glue.mcsx.client.style.internal.CompoundSelector;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleDeclaration;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleRule;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleSelector;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeTokenRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class StylesheetParser {

    private StylesheetParser() {
    }

    public static Stylesheet parse(ResourceLocation resource, String source) {
        return new Parser(resource, source).parse();
    }

    private enum ValueKind {
        COLOR,
        LENGTH,
        DIMENSION,
        NUMBER,
        ALIGN_ITEMS,
        JUSTIFY_CONTENT,
        FONT_WEIGHT,
        INDICATOR_TINT
    }

    private record PropertyDefinition(String name, ValueKind kind) {
    }

    private static final class Parser {

        private final ResourceLocation resource;
        private final String source;
        private int offset;
        private int line = 1;
        private int column = 1;
        private int sourceOrder;
        private int declarationOrder;

        private Parser(ResourceLocation resource, String source) {
            this.resource = resource;
            this.source = source;
        }

        private Stylesheet parse() {
            List<StyleRule> rules = new ArrayList<>();
            if (this.peek() == '\ufeff') {
                this.advance();
            }
            this.skipTrivia();
            while (!this.atEnd()) {
                rules.add(this.parseRule());
                this.skipTrivia();
            }
            return new Stylesheet(rules);
        }

        private StyleRule parseRule() {
            CompoundSelector first = this.parseCompoundSelector();
            this.skipTrivia();
            CompoundSelector parent = null;
            CompoundSelector target = first;
            if (this.consumeIf('>')) {
                parent = first;
                this.skipTrivia();
                target = this.parseCompoundSelector();
                this.skipTrivia();
            }
            this.expect('{', "Expected '{' after selector");

            List<StyleDeclaration> declarations = new ArrayList<>();
            this.skipTrivia();
            while (!this.consumeIf('}')) {
                if (this.atEnd()) throw this.error("Unterminated rule");

                int declarationLine = this.line;
                int declarationColumn = this.column;
                String propertyName = this.identifier("Expected property name").toLowerCase(Locale.ROOT);
                PropertyDefinition property = this.property(propertyName);
                this.skipTrivia();
                this.expect(':', "Expected ':' after property name");
                this.skipTrivia();
                int valueLine = this.line;
                int valueColumn = this.column;
                String rawValue = this.readValue();
                declarations.add(new StyleDeclaration(
                        property.name,
                        this.parseValue(property, rawValue, valueLine, valueColumn),
                        declarationLine,
                        declarationColumn,
                        this.declarationOrder++
                ));
                this.expect(';', "Expected ';' after property value");
                this.skipTrivia();
            }
            if (declarations.isEmpty()) throw this.error("A rule must contain a declaration");
            this.validateApplicability(target, declarations);

            StyleSelector selector = new StyleSelector(parent, target);
            return new StyleRule(
                    selector,
                    List.copyOf(declarations),
                    this.specificity(selector),
                    this.sourceOrder++
            );
        }

        private CompoundSelector parseCompoundSelector() {
            String type = null;
            Set<String> classes = new LinkedHashSet<>();
            Set<String> states = new LinkedHashSet<>();
            String part = null;
            if (this.identifierStart(this.peek())) {
                type = this.identifier("Expected component type");
            }

            boolean hasSelector = type != null;
            while (!this.atEnd()) {
                if (this.consumeIf('.')) {
                    if (!classes.add(this.identifier("Expected class name after '.'"))) {
                        throw this.error("Duplicate class selector");
                    }
                    hasSelector = true;
                    continue;
                }
                if (this.consumeIf(':')) {
                    if (this.consumeIf(':')) {
                        if (part != null) throw this.error("A selector can declare only one part");
                        part = this.identifier("Expected part name after '::'");
                    } else if (!states.add(this.identifier("Expected state name after ':'"))) {
                        throw this.error("Duplicate state selector");
                    }
                    hasSelector = true;
                    continue;
                }
                break;
            }
            if (!hasSelector) throw this.error("Expected selector");

            return new CompoundSelector(
                    type,
                    Set.copyOf(classes),
                    Set.copyOf(states),
                    part
            );
        }

        private StyleValue parseValue(
                PropertyDefinition property,
                String rawValue,
                int valueLine,
                int valueColumn
        ) {
            if (rawValue.startsWith("@")) {
                ThemeTokenRegistry.Entry token = this.token(rawValue.substring(1), valueLine, valueColumn);
                if (!this.tokenMatches(property, token)) {
                    throw this.errorAt(
                            valueLine,
                            valueColumn,
                            "Token '" + rawValue + "' is not valid for " + property.name
                    );
                }
                return new StyleValue.TokenReference(token.integerToken());
            }
            return switch (property.kind) {
                case COLOR -> new StyleValue.Literal(
                        this.parseColor(rawValue, valueLine, valueColumn)
                );
                case LENGTH -> new StyleValue.Literal(
                        this.parseLength(rawValue, valueLine, valueColumn)
                );
                case DIMENSION -> this.parseDimension(rawValue, valueLine, valueColumn);
                case NUMBER -> new StyleValue.Scalar(
                        this.parseNumber(rawValue, valueLine, valueColumn)
                );
                case ALIGN_ITEMS -> new StyleValue.Keyword(
                        this.parseKeyword(
                                rawValue,
                                Set.of("start", "end", "center", "stretch"),
                                valueLine,
                                valueColumn
                        )
                );
                case JUSTIFY_CONTENT -> new StyleValue.Keyword(
                        this.parseKeyword(
                                rawValue,
                                Set.of(
                                        "start",
                                        "end",
                                        "center",
                                        "space-between",
                                        "space-around",
                                        "space-evenly"
                                ),
                                valueLine,
                                valueColumn
                        )
                );
                case FONT_WEIGHT -> new StyleValue.Keyword(
                        this.parseKeyword(
                                rawValue,
                                Set.of("normal", "bold"),
                                valueLine,
                                valueColumn
                        )
                );
                case INDICATOR_TINT -> new StyleValue.Keyword(
                        this.parseKeyword(
                                rawValue,
                                Set.of("native", "theme"),
                                valueLine,
                                valueColumn
                        )
                );
            };
        }

        private int parseColor(String value, int valueLine, int valueColumn) {
            if (!value.startsWith("#")) {
                if (value.equals("transparent")) return 0;
                throw this.errorAt(valueLine, valueColumn, "Expected a hexadecimal color or token");
            }
            String digits = value.substring(1);
            // Integer.parseInt and Long.parseLong both accept a leading sign, so the digits are
            // validated up front instead of relying on NumberFormatException.
            for (int index = 0; index < digits.length(); index++) {
                char digit = digits.charAt(index);
                boolean hexadecimal = (digit >= '0' && digit <= '9')
                        || (digit >= 'a' && digit <= 'f')
                        || (digit >= 'A' && digit <= 'F');
                if (!hexadecimal) {
                    throw this.errorAt(valueLine, valueColumn, "Invalid hexadecimal color");
                }
            }
            try {
                return switch (digits.length()) {
                    case 3 -> 0xff000000
                            | Integer.parseInt("" + digits.charAt(0) + digits.charAt(0), 16) << 16
                            | Integer.parseInt("" + digits.charAt(1) + digits.charAt(1), 16) << 8
                            | Integer.parseInt("" + digits.charAt(2) + digits.charAt(2), 16);
                    case 6 -> 0xff000000 | Integer.parseInt(digits, 16);
                    case 8 -> (int) Long.parseLong(digits, 16);
                    default -> throw this.errorAt(
                            valueLine,
                            valueColumn,
                            "Colors use #rgb, #rrggbb or #aarrggbb"
                    );
                };
            } catch (NumberFormatException exception) {
                throw this.errorAt(valueLine, valueColumn, "Invalid hexadecimal color");
            }
        }

        private int parseLength(String value, int valueLine, int valueColumn) {
            if (!value.endsWith("px")) {
                throw this.errorAt(valueLine, valueColumn, "Expected a pixel length such as 12px");
            }
            try {
                int result = Integer.parseInt(value.substring(0, value.length() - 2));
                if (result < 0) {
                    throw this.errorAt(valueLine, valueColumn, "Lengths cannot be negative");
                }
                return result;
            } catch (NumberFormatException exception) {
                throw this.errorAt(valueLine, valueColumn, "Invalid pixel length");
            }
        }

        private StyleValue parseDimension(String value, int valueLine, int valueColumn) {
            if (value.endsWith("%")) {
                try {
                    float percent = Float.parseFloat(value.substring(0, value.length() - 1));
                    if (!Float.isFinite(percent) || percent < 0) {
                        throw this.errorAt(valueLine, valueColumn, "Percentages cannot be negative");
                    }
                    return new StyleValue.Percent(percent / 100.0f);
                } catch (NumberFormatException exception) {
                    throw this.errorAt(valueLine, valueColumn, "Invalid percentage");
                }
            }
            return new StyleValue.Literal(this.parseLength(value, valueLine, valueColumn));
        }

        private float parseNumber(String value, int valueLine, int valueColumn) {
            try {
                float result = Float.parseFloat(value);
                if (!Float.isFinite(result) || result < 0) {
                    throw this.errorAt(valueLine, valueColumn, "Numbers cannot be negative");
                }
                return result;
            } catch (NumberFormatException exception) {
                throw this.errorAt(valueLine, valueColumn, "Invalid number");
            }
        }

        private String parseKeyword(
                String value,
                Set<String> supported,
                int valueLine,
                int valueColumn
        ) {
            if (!supported.contains(value)) {
                throw this.errorAt(
                        valueLine,
                        valueColumn,
                        "Unsupported keyword '" + value + "'"
                );
            }
            return value;
        }

        private PropertyDefinition property(String name) {
            return switch (name) {
                case "color", "background", "hint-color", "top-highlight" ->
                        new PropertyDefinition(name, ValueKind.COLOR);
                case "corner-radius", "control-height", "text-size", "elevation",
                     "top-highlight-height", "padding-horizontal", "padding-vertical" ->
                        new PropertyDefinition(name, ValueKind.LENGTH);
                case "padding", "gap" -> new PropertyDefinition(name, ValueKind.LENGTH);
                case "width", "max-width" -> new PropertyDefinition(name, ValueKind.DIMENSION);
                case "flex-grow" -> new PropertyDefinition(name, ValueKind.NUMBER);
                case "align-items" -> new PropertyDefinition(name, ValueKind.ALIGN_ITEMS);
                case "font-weight" -> new PropertyDefinition(name, ValueKind.FONT_WEIGHT);
                case "indicator-tint" -> new PropertyDefinition(name, ValueKind.INDICATOR_TINT);
                case "justify-content" -> new PropertyDefinition(
                        name,
                        ValueKind.JUSTIFY_CONTENT
                );
                default -> throw this.error("Unknown property '" + name + "'");
            };
        }

        private void validateApplicability(
                CompoundSelector selector,
                List<StyleDeclaration> declarations
        ) {
            if (selector.type() == null || selector.part() != null) return;

            Set<String> supported = switch (selector.type()) {
                case "Text" -> Set.of("color", "text-size", "width", "max-width", "flex-grow");
                case "Checkbox" -> Set.of(
                        "color",
                        "indicator-tint",
                        "control-height",
                        "text-size",
                        "width",
                        "max-width",
                        "flex-grow"
                );
                case "Button" -> Set.of(
                        "color",
                        "background",
                        "corner-radius",
                        "control-height",
                        "text-size",
                        "font-weight",
                        "elevation",
                        "top-highlight",
                        "top-highlight-height",
                        "width",
                        "max-width",
                        "flex-grow"
                );
                case "TextField" -> Set.of(
                        "color",
                        "hint-color",
                        "background",
                        "corner-radius",
                        "control-height",
                        "text-size",
                        "padding-horizontal",
                        "padding-vertical",
                        "width",
                        "max-width",
                        "flex-grow"
                );
                case "Column", "Row" -> Set.of(
                        "background",
                        "corner-radius",
                        "width",
                        "max-width",
                        "flex-grow",
                        "padding",
                        "gap",
                        "align-items",
                        "justify-content"
                );
                default -> null;
            };
            if (supported == null) return;

            for (StyleDeclaration declaration : declarations) {
                if (!supported.contains(declaration.property())) {
                    throw this.errorAt(
                            declaration.line(),
                            declaration.column(),
                            "Property '" + declaration.property()
                                    + "' is not supported by " + selector.type()
                    );
                }
            }
        }

        private ThemeTokenRegistry.Entry token(String name, int valueLine, int valueColumn) {
            ThemeTokenRegistry.Entry token = ThemeTokenRegistry.find(name);
            if (token == null) {
                throw this.errorAt(valueLine, valueColumn, "Unknown theme token '@" + name + "'");
            }
            return token;
        }

        private boolean tokenMatches(PropertyDefinition property, ThemeTokenRegistry.Entry token) {
            return switch (property.kind) {
                case COLOR -> token.kind() == ThemeTokenRegistry.Kind.COLOR;
                case LENGTH, DIMENSION -> token.kind() == ThemeTokenRegistry.Kind.DIMENSION;
                case NUMBER, ALIGN_ITEMS, JUSTIFY_CONTENT, FONT_WEIGHT, INDICATOR_TINT -> false;
            };
        }

        private int specificity(StyleSelector selector) {
            return this.specificity(selector.target())
                    + (selector.parent() == null ? 0 : this.specificity(selector.parent()));
        }

        private int specificity(CompoundSelector selector) {
            int classWeight = selector.classes().size() + selector.states().size();
            if (selector.part() != null) classWeight++;
            return classWeight * 100 + (selector.type() == null ? 0 : 1);
        }

        private String readValue() {
            StringBuilder value = new StringBuilder();
            while (!this.atEnd() && this.peek() != ';' && this.peek() != '}') {
                if (this.peek() == '/' && this.peek(1) == '*') {
                    this.advance();
                    this.advance();
                    while (!this.atEnd() && !(this.peek() == '*' && this.peek(1) == '/')) {
                        this.advance();
                    }
                    if (this.atEnd()) throw this.error("Unterminated comment");
                    this.advance();
                    this.advance();
                    value.append(' ');
                    continue;
                }
                value.append(this.peek());
                this.advance();
            }
            String result = value.toString().trim();
            if (result.isEmpty()) throw this.error("Property value cannot be empty");
            if (this.peek() == '}') throw this.error("Expected ';' after property value");
            return result;
        }

        private String identifier(String message) {
            if (!this.identifierStart(this.peek())) throw this.error(message);

            int start = this.offset;
            this.advance();
            while (this.identifierPart(this.peek())) {
                this.advance();
            }
            return this.source.substring(start, this.offset);
        }

        private void skipTrivia() {
            while (!this.atEnd()) {
                if (Character.isWhitespace(this.peek())) {
                    this.advance();
                    continue;
                }
                if (this.peek() == '/' && this.peek(1) == '*') {
                    this.advance();
                    this.advance();
                    while (!this.atEnd() && !(this.peek() == '*' && this.peek(1) == '/')) {
                        this.advance();
                    }
                    if (this.atEnd()) throw this.error("Unterminated comment");
                    this.advance();
                    this.advance();
                    continue;
                }
                break;
            }
        }

        private boolean consumeIf(char expected) {
            if (this.peek() != expected) return false;

            this.advance();
            return true;
        }

        private void expect(char expected, String message) {
            if (!this.consumeIf(expected)) throw this.error(message);
        }

        private char peek() {
            return this.peek(0);
        }

        private char peek(int lookahead) {
            int index = this.offset + lookahead;
            return index >= this.source.length() ? '\0' : this.source.charAt(index);
        }

        private void advance() {
            char current = this.source.charAt(this.offset++);
            if (current == '\n') {
                this.line++;
                this.column = 1;
            } else {
                this.column++;
            }
        }

        private boolean atEnd() {
            return this.offset >= this.source.length();
        }

        private boolean identifierStart(char value) {
            return Character.isLetter(value) || value == '_';
        }

        private boolean identifierPart(char value) {
            return this.identifierStart(value) || Character.isDigit(value) || value == '-';
        }

        private StylesheetParseException error(String message) {
            return this.errorAt(this.line, this.column, message);
        }

        private StylesheetParseException errorAt(int line, int column, String message) {
            return new StylesheetParseException(this.resource, line, column, message);
        }
    }
}
