package fr.lacaleche.glue.mcsx.client.style;

import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylesheetParserTest {

    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath(
            "test",
            "screen"
    );

    @Test
    void parsesSupportedSelectorsAndTypedValues() {
        Stylesheet stylesheet = StylesheetParser.parse(RESOURCE, """
                Button.primary:hover {
                    background: @accent;
                    corner-radius: 8px;
                }

                .toolbar > Text::label {
                    color: #123abc;
                }
                """);

        assertEquals(2, stylesheet.internalRules().size());
        assertEquals("Button", stylesheet.internalRules().get(0).selector().target().type());
        assertTrue(stylesheet.internalRules().get(0).selector().target().classes().contains("primary"));
        assertTrue(stylesheet.internalRules().get(0).selector().target().states().contains("hover"));
        assertEquals("label", stylesheet.internalRules().get(1).selector().target().part());
        assertEquals("toolbar", stylesheet.internalRules().get(1).selector().parent().classes().iterator().next());
        assertEquals(
                new StyleValue.TokenReference(ThemeTokens.ACCENT),
                stylesheet.internalRules().get(0).declarations().get(0).value()
        );
    }

    @Test
    void reportsResourceLineAndColumnForInvalidValue() {
        StylesheetParseException exception = assertThrows(
                StylesheetParseException.class,
                () -> StylesheetParser.parse(RESOURCE, """
                        Text {
                            color: 12px;
                        }
                        """)
        );

        assertSame(RESOURCE, exception.resource());
        assertEquals(2, exception.line());
        assertTrue(exception.column() > 0);
        assertTrue(exception.getMessage().contains("test:screen:2:"));
    }

    @Test
    void rejectsPropertyUnsupportedByConcreteComponentType() {
        StylesheetParseException exception = assertThrows(
                StylesheetParseException.class,
                () -> StylesheetParser.parse(RESOURCE, "Text { background: #ffffff; }")
        );

        assertTrue(exception.getMessage().contains("not supported by Text"));
    }

    @Test
    void rejectsBackgroundThatWouldReplaceNativeCheckboxFeedback() {
        StylesheetParseException exception = assertThrows(
                StylesheetParseException.class,
                () -> StylesheetParser.parse(RESOURCE, "Checkbox { background: #ffffff; }")
        );

        assertTrue(exception.getMessage().contains("not supported by Checkbox"));
    }

    @Test
    void rejectsSignedAndNonHexadecimalColors() {
        for (String color : new String[]{"#-fffff", "#-ffffff", "#+ff", "#gggggg", "#ff fff"}) {
            StylesheetParseException exception = assertThrows(
                    StylesheetParseException.class,
                    () -> StylesheetParser.parse(RESOURCE, "Text { color: " + color + "; }"),
                    color
            );

            assertTrue(exception.getMessage().contains("color"), color);
        }
    }

    @Test
    void parsesEverySupportedColorLength() {
        Stylesheet stylesheet = StylesheetParser.parse(RESOURCE, """
                Text.short { color: #fff; }
                Text.full { color: #0fffff; }
                Text.alpha { color: #80ffffff; }
                """);

        assertEquals(
                new StyleValue.Literal(0xffffffff),
                stylesheet.internalRules().get(0).declarations().getFirst().value()
        );
        assertEquals(
                new StyleValue.Literal(0xff0fffff),
                stylesheet.internalRules().get(1).declarations().getFirst().value()
        );
        assertEquals(
                new StyleValue.Literal(0x80ffffff),
                stylesheet.internalRules().get(2).declarations().getFirst().value()
        );
    }

    @Test
    void resolvesAllRegistryKindsForCompatibleProperties() {
        Stylesheet stylesheet = StylesheetParser.parse(RESOURCE, """
                Button { background: @dock-border; control-height: @control-height; }
                Column { gap: @corner-radius; width: @control-height; }
                """);

        assertEquals(
                new StyleValue.TokenReference(ThemeTokens.DOCK_BORDER),
                stylesheet.internalRules().getFirst().declarations().getFirst().value()
        );
        assertEquals(
                new StyleValue.TokenReference(ThemeTokens.CORNER_RADIUS),
                stylesheet.internalRules().get(1).declarations().getFirst().value()
        );
    }

    @Test
    void rejectsDockMetricsForScalarProperties() {
        StylesheetParseException exception = assertThrows(
                StylesheetParseException.class,
                () -> StylesheetParser.parse(RESOURCE, "Text { text-size: @dock-metrics; }")
        );

        assertTrue(exception.getMessage().contains("not valid for text-size"));
    }

    @Test
    void acceptsBomAndCommentsAroundPropertyValues() {
        Stylesheet stylesheet = StylesheetParser.parse(
                RESOURCE,
                "\ufeffText { color: #ffffff /* readable note */; }"
        );

        assertEquals(1, stylesheet.internalRules().size());
    }

    @Test
    void parsesResponsiveAndFlexLayoutValues() {
        Stylesheet stylesheet = StylesheetParser.parse(RESOURCE, """
                Column.panel {
                    width: 100%;
                    max-width: 720px;
                    padding: 24px;
                    gap: 12px;
                    align-items: stretch;
                    justify-content: space-between;
                    flex-grow: 1;
                }
                """);

        assertEquals(
                new StyleValue.Percent(1.0f),
                stylesheet.internalRules().getFirst().declarations().get(0).value()
        );
        assertEquals(
                new StyleValue.Keyword("stretch"),
                stylesheet.internalRules().getFirst().declarations().get(4).value()
        );
        assertEquals(
                new StyleValue.Scalar(1.0f),
                stylesheet.internalRules().getFirst().declarations().get(6).value()
        );
    }

    @Test
    void parsesButtonChromeAndNativeControlProperties() {
        Stylesheet stylesheet = StylesheetParser.parse(RESOURCE, """
                Button.ghost {
                    font-weight: bold;
                    elevation: 3px;
                    top-highlight: #20ffffff;
                    top-highlight-height: 2px;
                }
                TextField { hint-color: @text-muted; padding-horizontal: 13px; }
                Checkbox { indicator-tint: native; }
                """);

        assertEquals(
                new StyleValue.Keyword("bold"),
                stylesheet.internalRules().getFirst().declarations().getFirst().value()
        );
        assertEquals(
                new StyleValue.Literal(3),
                stylesheet.internalRules().getFirst().declarations().get(1).value()
        );
        assertEquals(
                new StyleValue.Keyword("native"),
                stylesheet.internalRules().get(2).declarations().getFirst().value()
        );
    }

    @Test
    void invalidCandidateDoesNotReplaceInstalledStylesheet() {
        Value<Stylesheet> handle = Stylesheets.resource(RESOURCE);
        Stylesheet valid = StylesheetParser.parse(RESOURCE, "Text { color: #ffffff; }");
        Stylesheets.install(Map.of(RESOURCE, valid));

        assertThrows(
                StylesheetParseException.class,
                () -> StylesheetParser.parse(RESOURCE, "Text { color: nope; }")
        );

        assertSame(valid, handle.get());
    }

    @Test
    void listenerFailureDoesNotLeaveResourceHandlesOnMixedGenerations() {
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "second");
        Value<Stylesheet> first = Stylesheets.resource(firstId);
        Value<Stylesheet> second = Stylesheets.resource(secondId);
        Stylesheet firstSheet = StylesheetParser.parse(firstId, "Text { color: #111111; }");
        Stylesheet secondSheet = StylesheetParser.parse(secondId, "Text { color: #222222; }");
        Subscription failing = first.subscribe(value -> {
            throw new IllegalStateException("consumer failed");
        });

        try {
            assertThrows(IllegalStateException.class, () -> Stylesheets.install(Map.of(
                    firstId,
                    firstSheet,
                    secondId,
                    secondSheet
            )));
        } finally {
            failing.close();
        }

        assertSame(firstSheet, first.get());
        assertSame(secondSheet, second.get());
    }

    @Test
    void resourceListenersObserveOneCompleteInstalledGeneration() {
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "atomic-first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "atomic-second");
        Value<Stylesheet> first = Stylesheets.resource(firstId);
        Value<Stylesheet> second = Stylesheets.resource(secondId);
        Stylesheet firstSheet = StylesheetParser.parse(firstId, "Text { color: #112233; }");
        Stylesheet secondSheet = StylesheetParser.parse(secondId, "Text { color: #445566; }");
        Stylesheet[] observedSecond = new Stylesheet[1];
        Subscription subscription = first.subscribe(ignored -> observedSecond[0] = second.get());

        try {
            Stylesheets.install(Map.of(firstId, firstSheet, secondId, secondSheet));
        } finally {
            subscription.close();
        }

        assertSame(secondSheet, observedSecond[0]);
    }
}
