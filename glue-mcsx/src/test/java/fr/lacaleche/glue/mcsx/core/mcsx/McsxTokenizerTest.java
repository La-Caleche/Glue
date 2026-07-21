package fr.lacaleche.glue.mcsx.core.mcsx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McsxTokenizerTest {

    private static List<Token> types(String source) {
        return McsxTokenizer.tokenize(source);
    }

    private static Token.Type[] kinds(String source) {
        return McsxTokenizer.tokenize(source).stream().map(Token::type).toArray(Token.Type[]::new);
    }

    @Test
    void tokenizesOpenTagWithText() {
        assertArrayEqualsTypes(new Token.Type[]{
                Token.Type.LT, Token.Type.TAG_NAME, Token.Type.TAG_END,
                Token.Type.TEXT,
                Token.Type.LT_SLASH, Token.Type.TAG_NAME, Token.Type.TAG_END,
                Token.Type.EOF
        }, kinds("<div>hello</div>"));
    }

    @Test
    void tokenizesLiteralBooleanAndBindingAttributes() {
        List<Token> tokens = types("<input value=\"x\" disabled onInput={fn}/>");
        assertEquals(Token.Type.TAG_NAME, tokens.get(1).type());
        assertEquals("input", tokens.get(1).value());
        // value="x"
        assertEquals(Token.Type.ATTR_NAME, tokens.get(2).type());
        assertEquals(Token.Type.EQ, tokens.get(3).type());
        assertEquals(Token.Type.STRING, tokens.get(4).type());
        assertEquals("x", tokens.get(4).value());
        // disabled  (boolean, no '=')
        assertEquals(Token.Type.ATTR_NAME, tokens.get(5).type());
        assertEquals("disabled", tokens.get(5).value());
        // onInput={fn}
        assertEquals(Token.Type.ATTR_NAME, tokens.get(6).type());
        assertEquals(Token.Type.EQ, tokens.get(7).type());
        assertEquals(Token.Type.BINDING, tokens.get(8).type());
        assertEquals("fn", tokens.get(8).value());
        assertEquals(Token.Type.TAG_SELF_END, tokens.get(9).type());
    }

    @Test
    void tokenizesInterpolationAndKeepsLoneBraceAsText() {
        List<Token> tokens = types("<t>a { b {{ref}} c</t>");
        // "a { b " is one text run (lone '{' stays text), then INTERP, then " c"
        assertEquals(Token.Type.TEXT, tokens.get(3).type());
        assertEquals("a { b ", tokens.get(3).value());
        assertEquals(Token.Type.INTERP, tokens.get(4).type());
        assertEquals("ref", tokens.get(4).value());
        assertEquals(Token.Type.TEXT, tokens.get(5).type());
        assertEquals(" c", tokens.get(5).value());
    }

    @Test
    void trimsInterpolationAndBindingReferences() {
        assertEquals("a.b", types("<t>{{  a.b  }}</t>").get(3).value());
        // LT, TAG_NAME(b), ATTR_NAME(x), EQ, BINDING(fn) → the binding is index 4
        assertEquals("fn", types("<b x={  fn  }/>").get(4).value());
    }

    @Test
    void tokenizesComment() {
        List<Token> tokens = types("<!-- hi --><div/>");
        assertEquals(Token.Type.COMMENT, tokens.get(0).type());
        assertEquals(" hi ", tokens.get(0).value());
        assertEquals(Token.Type.LT, tokens.get(1).type());
    }

    @Test
    void tracksLineAndColumnOneBased() {
        List<Token> tokens = types("<div>\n  <t/>\n</div>");
        Token lt = tokens.get(0);
        assertEquals(1, lt.line());
        assertEquals(1, lt.column());
        // the nested <t/> opener is on line 2, after two spaces → column 3
        Token nested = tokens.stream().filter(t -> t.type() == Token.Type.LT).skip(1).findFirst().orElseThrow();
        assertEquals(2, nested.line());
        assertEquals(3, nested.column());
    }

    @Test
    void throwsOnUnterminatedConstructs() {
        assertPositioned("unterminated tag", () -> types("<div"));
        assertPositioned("unterminated string", () -> types("<a b=\"x"));
        assertPositioned("unterminated binding", () -> types("<a b={x"));
        assertPositioned("unterminated interpolation", () -> types("<t>{{x"));
        assertPositioned("unterminated comment", () -> types("<!-- x"));
        assertPositioned("empty binding", () -> types("<a b={}/>"));
        assertPositioned("empty interpolation", () -> types("<t>{{}}</t>"));
    }

    @Test
    void rejectsAnythingOtherThanDottedIdentifiersAsBindings() {
        assertPositioned("invalid binding reference", () -> types("<a b={item.}/>"));
        assertPositioned("invalid binding reference", () -> types("<a b={.item}/>"));
        assertPositioned("invalid binding reference", () -> types("<a b={item..name}/>"));
        assertPositioned("invalid binding reference", () -> types("<a b={item + 1}/>"));
        assertPositioned("invalid binding reference", () -> types("<t>{{item name}}</t>"));
    }

    private static void assertPositioned(String fragment, Runnable action) {
        McsxParseException ex = assertThrows(McsxParseException.class, action::run);
        assertTrue(ex.getMessage().contains(fragment),
                () -> "expected message to contain '" + fragment + "' but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("line"), "message should carry a position");
    }

    private static void assertArrayEqualsTypes(Token.Type[] expected, Token.Type[] actual) {
        assertEquals(List.of(expected), List.of(actual));
    }
}
