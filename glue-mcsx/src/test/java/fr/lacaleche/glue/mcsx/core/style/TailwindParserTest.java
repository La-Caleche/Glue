package fr.lacaleche.glue.mcsx.core.style;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.ColorValue;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.FontWeight;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.TextAlign;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Variant;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TailwindParserTest {

    /** One strict representative for every Tailwind family MCSX supports fully or partially. */
    @Test
    void compatibilityMatrixRepresentativesRemainSupported() {
        String[] representatives = {
                "absolute", "inset-2",
                "flex-row", "flex-wrap", "grow", "shrink", "gap-2",
                "justify-center", "items-center", "self-center",
                "p-2", "m-auto",
                "w-20", "min-w-20", "max-w-20", "h-20", "min-h-20", "max-h-20",
                "text-sm", "font-semibold", "text-center", "text-accent",
                "bg-surface", "rounded-md", "border-2", "border-accent",
                "opacity-50", "transition", "animate-spin"
        };
        for (String utility : representatives) {
            assertDoesNotThrow(() -> TailwindParser.parseStrict(utility), utility);
        }
    }

    @Test
    void parsesLayoutUtilities() {
        StyleSpec s = TailwindParser.parse("flex-row items-center justify-end grow gap-2");
        assertEquals(Orientation.ROW, s.orientation());
        assertEquals(Align.CENTER, s.items());
        assertEquals(Justify.END, s.justify());
        assertTrue(s.grow());
        assertEquals(8, s.gapPx());
    }

    /** Distributed justification used to degrade silently to {@code start}; FlexLayout implements it. */
    @Test
    void parsesDistributedJustification() {
        assertEquals(Justify.BETWEEN, TailwindParser.parse("justify-between").justify());
        assertEquals(Justify.AROUND, TailwindParser.parse("justify-around").justify());
        assertEquals(Justify.EVENLY, TailwindParser.parse("justify-evenly").justify());
    }

    @Test
    void parsesShrinkAndAlignSelf() {
        assertEquals(1f, TailwindParser.parse("shrink").shrink());
        assertEquals(0f, TailwindParser.parse("shrink-0").shrink());
        assertNull(TailwindParser.parse("grow").shrink());
        assertEquals(Align.CENTER, TailwindParser.parse("self-center").alignSelf());
        assertEquals(Align.STRETCH, TailwindParser.parse("self-stretch").alignSelf());
        assertTrue(TailwindParser.parse("grow").grow());
        assertFalse(TailwindParser.parse("grow-0").grow());
    }

    @Test
    void appliesScaleToSpacing() {
        StyleSpec s = TailwindParser.parse("p-4");
        assertEquals(16, s.padLeft());
        assertEquals(16, s.padTop());
        assertEquals(16, s.padRight());
        assertEquals(16, s.padBottom());
    }

    @Test
    void axisPaddingOverridesAllSides() {
        StyleSpec s = TailwindParser.parse("p-2 px-4 pt-1");
        assertEquals(16, s.padLeft());   // from px-4
        assertEquals(16, s.padRight());  // from px-4
        assertEquals(4, s.padTop());     // from pt-1
        assertEquals(8, s.padBottom());  // still from p-2
    }

    @Test
    void parsesRadiusScale() {
        assertEquals(6, TailwindParser.parse("rounded").cornerPx());
        assertEquals(10, TailwindParser.parse("rounded-md").cornerPx());
        assertEquals(14, TailwindParser.parse("rounded-lg").cornerPx());
        assertEquals(999, TailwindParser.parse("rounded-full").cornerPx());
        assertEquals(8, TailwindParser.parse("rounded-[8px]").cornerPx());
        assertEquals(6, TailwindParser.parse("rounded-sm").cornerPx());
        assertEquals(0, TailwindParser.parse("rounded-none").cornerPx());
    }

    /** The uniform and per-corner forms read one scale, so a named size cannot mean two radii. */
    @Test
    void theRadiusScaleIsTheSameForUniformAndPerCornerForms() {
        for (String name : new String[]{"none", "sm", "md", "lg", "full"}) {
            assertEquals(TailwindParser.parse("rounded-" + name).cornerPx(),
                    TailwindParser.parse("rounded-t-" + name).cornerRadii()[0],
                    "rounded-" + name + " and rounded-t-" + name + " disagree");
        }
    }

    @Test
    void parsesBorderWidthTokenAndArbitraryColor() {
        assertEquals(1, TailwindParser.parse("border").borderWidthPx());
        assertEquals(2, TailwindParser.parse("border-2").borderWidthPx());
        assertEquals(new ColorValue.TokenRef(Tokens.BORDER_STRONG),
                TailwindParser.parse("border-strong").borderColor());
        assertEquals(new ColorValue.Literal(0xFF5BE49B),
                TailwindParser.parse("border-[#5be49b]").borderColor());
    }

    @Test
    void resolvesBackgroundTokensAndArbitraryHex() {
        assertEquals(new ColorValue.TokenRef(Tokens.SURFACE_1),
                TailwindParser.parse("bg-surface").background());
        assertEquals(new ColorValue.TokenRef(Tokens.SURFACE_2),
                TailwindParser.parse("bg-surface-2").background());
        assertEquals(new ColorValue.TokenRef(Tokens.ACCENT_HOVER),
                TailwindParser.parse("bg-accent-hover").background());
        assertEquals(new ColorValue.Literal(0xFF151A2E),
                TailwindParser.parse("bg-[#151a2e]").background());
    }

    @Test
    void resolvesTextSizeColorAndAlign() {
        assertEquals(20, TailwindParser.parse("text-2xl").fontSizePx());
        assertEquals(new ColorValue.TokenRef(Tokens.TEXT_MUTED),
                TailwindParser.parse("text-muted").textColor());
        assertEquals(TextAlign.CENTER, TailwindParser.parse("text-center").textAlign());
        assertEquals(new ColorValue.Literal(0xFFFFFFFF),
                TailwindParser.parse("text-[#ffffff]").textColor());
    }

    @Test
    void parsesSizingWithFullAndArbitrary() {
        assertTrue(TailwindParser.parse("w-full").width().fill());
        assertFalse(TailwindParser.parse("h-6").height().fill());
        assertEquals(24, TailwindParser.parse("h-6").height().px());
        assertEquals(120, TailwindParser.parse("w-[120px]").width().px());
    }

    @Test
    void parsesOpacityScaleAndArbitrary() {
        assertEquals(0.5f, TailwindParser.parse("opacity-50").opacity());
        assertEquals(0.25f, TailwindParser.parse("opacity-[0.25]").opacity());
    }

    @Test
    void expandsShortHexAndAlphaHex() {
        assertEquals(new ColorValue.Literal(0xFFFFFFFF), TailwindParser.parse("bg-[#fff]").background());
        // #rrggbbaa (web order) → packed ARGB
        assertEquals(new ColorValue.Literal(0x80112233), TailwindParser.parse("bg-[#11223380]").background());
    }

    @Test
    void collectsVariantsWithoutTouchingBase() {
        StyleSpec s = TailwindParser.parse("bg-surface hover:bg-surface-2 focus:border-accent");
        assertEquals(new ColorValue.TokenRef(Tokens.SURFACE_1), s.background());

        StyleSpec hover = s.variants().get(Variant.HOVER);
        assertEquals(new ColorValue.TokenRef(Tokens.SURFACE_2), hover.background());
        assertNull(hover.borderColor());

        StyleSpec focus = s.variants().get(Variant.FOCUS);
        assertEquals(new ColorValue.TokenRef(Tokens.ACCENT), focus.borderColor());
        assertNull(focus.background());
    }

    @Test
    void ignoresSelectionAndScrollbarUtilities() {
        StyleSpec s = TailwindParser.parse("selection-accent scrollbar-thin bg-surface");
        assertEquals(new ColorValue.TokenRef(Tokens.SURFACE_1), s.background());
    }

    @Test
    void parsesTheFourFontWeights() {
        assertEquals(FontWeight.NORMAL, TailwindParser.parse("font-normal").fontWeight());
        assertEquals(FontWeight.MEDIUM, TailwindParser.parse("font-medium").fontWeight());
        assertEquals(FontWeight.SEMIBOLD, TailwindParser.parse("font-semibold").fontWeight());
        assertEquals(FontWeight.BOLD, TailwindParser.parse("font-bold").fontWeight());
        assertNull(TailwindParser.parse("text-sm").fontWeight());
    }

    @Test
    void pxLengthIsOnePixel() {
        assertEquals(1, TailwindParser.parse("h-px").height().px());
        assertEquals(1, TailwindParser.parse("w-px").width().px());
    }

    @Test
    void statusSubtleAndContrastTokensResolve() {
        assertEquals(new ColorValue.TokenRef(Tokens.STATUS_DANGER_SUBTLE),
                TailwindParser.parse("bg-danger-subtle").background());
        assertEquals(new ColorValue.TokenRef(Tokens.BORDER), TailwindParser.parse("bg-border").background());
        assertEquals(new ColorValue.TokenRef(Tokens.STATUS_DANGER_CONTRAST),
                TailwindParser.parse("text-danger-contrast").textColor());
        assertEquals(new ColorValue.TokenRef(Tokens.STATUS_INFO),
                TailwindParser.parse("border-info").borderColor());
    }

    @Test
    void autoLengthHugsContent() {
        assertTrue(TailwindParser.parse("w-auto").width().wrap());
        assertTrue(TailwindParser.parse("h-auto").height().wrap());
        assertFalse(TailwindParser.parse("h-auto").height().fill());
    }

    @Test
    void perCornerRadiiOverrideTheUniformShorthand() {
        // rounded-md sets all four; rounded-r-none then squares the right pair (a button group).
        StyleSpec s = TailwindParser.parse("rounded-md rounded-r-none");
        assertArrayEquals(new int[]{10, 0, 0, 10}, s.cornerRadii());
        assertTrue(s.hasCorner());

        assertArrayEquals(new int[]{14, 14, 0, 0}, TailwindParser.parse("rounded-t-lg").cornerRadii());
        assertArrayEquals(new int[]{0, 0, 6, 0}, TailwindParser.parse("rounded-br-sm").cornerRadii());
        assertArrayEquals(new int[]{0, 0, 0, 0}, TailwindParser.parse("bg-surface").cornerRadii());
        assertFalse(TailwindParser.parse("bg-surface").hasCorner());
    }

    @Test
    void parsesMinAndMaxSizeBounds() {
        assertEquals(80, TailwindParser.parse("min-w-20").minWidthPx());
        assertEquals(200, TailwindParser.parse("max-w-[200px]").maxWidthPx());
        assertEquals(16, TailwindParser.parse("min-h-4").minHeightPx());
        assertNull(TailwindParser.parse("w-4").maxWidthPx());
    }

    @Test
    void parsesLoopingAnimations() {
        assertEquals(StyleSpec.Animation.SPIN, TailwindParser.parse("animate-spin").animation());
        assertEquals(StyleSpec.Animation.PULSE, TailwindParser.parse("animate-pulse").animation());
        assertEquals(StyleSpec.Animation.NONE, TailwindParser.parse("animate-none").animation());
        assertNull(TailwindParser.parse("bg-surface").animation());
        // animate-none must be able to switch an inherited animation off through merged().
        assertEquals(StyleSpec.Animation.NONE,
                TailwindParser.parse("animate-spin").merged(TailwindParser.parse("animate-none")).animation());
    }

    @Test
    void parsesTransitionAndCanTurnItOff() {
        assertEquals(Boolean.TRUE, TailwindParser.parse("transition").transition());
        assertEquals(Boolean.FALSE, TailwindParser.parse("transition-none").transition());
        assertNull(TailwindParser.parse("bg-surface").transition());
        // Boolean, not boolean: "unset" must stay distinguishable from "explicitly off" in merged().
        assertEquals(Boolean.FALSE,
                TailwindParser.parse("transition").merged(TailwindParser.parse("transition-none")).transition());
    }

    @Test
    void parsesFractionalWidths() {
        assertEquals(0.5f, TailwindParser.parse("w-1/2").width().fraction());
        assertEquals(StyleSpec.Length.Mode.FRACTION, TailwindParser.parse("w-1/2").width().mode());
        assertEquals(0.75f, TailwindParser.parse("h-3/4").height().fraction());
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("w-3/2"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("w-1/0"));
    }

    @Test
    void growZeroTurnsOffAnInheritedGrow() {
        // Boolean, not boolean: "unset" must stay distinguishable from "explicitly off" in merged().
        assertFalse(TailwindParser.parse("grow").merged(TailwindParser.parse("grow-0")).grow());
        // An overlay that says nothing about grow must not clear the base's.
        assertTrue(TailwindParser.parse("grow").merged(TailwindParser.parse("p-2")).grow());
        assertFalse(TailwindParser.parse("p-2").merged(TailwindParser.parse("grow-0")).grow());
    }

    @Test
    void autoMarginsCenterPerAxisAndSurviveAMerge() {
        assertTrue(TailwindParser.parse("mx-auto").autoMarginX());
        assertFalse(TailwindParser.parse("mx-auto").autoMarginY());
        assertTrue(TailwindParser.parse("my-auto").autoMarginY());
        assertFalse(TailwindParser.parse("my-auto").autoMarginX());
        assertTrue(TailwindParser.parse("m-auto").hasAutoMargin());
        assertFalse(TailwindParser.parse("w-96").hasAutoMargin());

        StyleSpec merged = TailwindParser.parse("mx-auto").merged(TailwindParser.parse("my-auto"));
        assertTrue(merged.autoMarginX());
        assertTrue(merged.autoMarginY());
    }

    @Test
    void emptyOrNullClassYieldsBlankSpec() {
        assertNull(TailwindParser.parse("").background());
        assertNull(TailwindParser.parse("   ").orientation());
        assertNull(TailwindParser.parse(null).background());
    }

    @Test
    void parseStrictThrowsOnStandardPaletteAndUnknownClasses() {
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("bg-slate-800"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("floaty-thing"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("md:flex-row"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("gap-huge"));
        // TailwindException must remain catchable as IllegalArgumentException (loud-fail contract)
        assertThrows(IllegalArgumentException.class, () -> TailwindParser.parseStrict("nonsense"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("gap-999999999999"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("opacity-150"));
        assertThrows(TailwindException.class, () -> TailwindParser.parseStrict("opacity-[NaN]"));
    }

    /**
     * The runtime parser skips what it cannot express rather than taking the screen down with it.
     * Every valid utility in the string must still land, including ones after the bad token.
     */
    @Test
    void parseSkipsUnsupportedClassesAndKeepsTheRest() {
        StyleSpec s = assertDoesNotThrow(
                () -> TailwindParser.parse("flex-row floaty-thing bg-slate-800 gap-2 md:flex-col text-muted"));
        assertEquals(Orientation.ROW, s.orientation());
        assertEquals(8, s.gapPx());
        assertEquals(new ColorValue.TokenRef(Tokens.TEXT_MUTED), s.textColor());
        assertNull(s.width(), "the unknown utility must not set a width");
        assertNull(s.background(), "the rejected palette class must not set a background");
    }

    /** A skipped {@code hover:} utility must not leave an empty variant behind. */
    @Test
    void skippingAVariantUtilityRegistersNoVariant() {
        StyleSpec s = TailwindParser.parse("bg-surface hover:bg-slate-800");
        assertTrue(s.variants().isEmpty(), "an empty hover variant would make the view clickable");
    }

    @Test
    void parsesWrapAndCanTurnItOff() {
        assertEquals(Boolean.TRUE, TailwindParser.parse("flex-row flex-wrap").wrap());
        assertEquals(Boolean.FALSE, TailwindParser.parse("flex-nowrap").wrap());
        assertNull(TailwindParser.parse("flex-row").wrap(), "wrap must stay unset by default");
    }

    /** {@code flex-nowrap} has to beat an inherited {@code flex-wrap}, so unset and false differ. */
    @Test
    void nowrapOverridesAnInheritedWrap() {
        StyleSpec merged = TailwindParser.parse("flex-wrap").merged(TailwindParser.parse("flex-nowrap"));
        assertEquals(Boolean.FALSE, merged.wrap());
        assertEquals(Boolean.TRUE, TailwindParser.parse("flex-wrap").merged(
                TailwindParser.parse("gap-2")).wrap(), "an unrelated overlay must not clear it");
    }

    @Test
    void parsesPositionKeywords() {
        assertTrue(TailwindParser.parse("absolute").absolute());
        assertFalse(TailwindParser.parse("relative").absolute());
        assertFalse(TailwindParser.parse("static").absolute());
        assertNull(TailwindParser.parse("flex-row").position(), "position must stay unset by default");
    }

    @Test
    void insetShorthandPinsAllFourEdges() {
        StyleSpec s = TailwindParser.parse("absolute inset-0");
        assertEquals(0, s.insetLeft());
        assertEquals(0, s.insetTop());
        assertEquals(0, s.insetRight());
        assertEquals(0, s.insetBottom());
    }

    @Test
    void parsesPerEdgeInsetsOnTheScaleAndArbitrary() {
        StyleSpec s = TailwindParser.parse("absolute left-[13px] bottom-3");
        assertEquals(13, s.insetLeft());
        assertEquals(12, s.insetBottom());
        assertNull(s.insetTop());
        assertNull(s.insetRight());
    }

    /** {@code right-*}/{@code bottom-*} must not be swallowed by the {@code rounded-}/{@code border-} prefixes. */
    @Test
    void edgeInsetsDoNotCollideWithOtherPrefixes() {
        StyleSpec s = TailwindParser.parse("absolute right-2 bottom-2 border-2 rounded-md");
        assertEquals(8, s.insetRight());
        assertEquals(8, s.insetBottom());
        assertEquals(2, s.borderWidthPx());
        assertEquals(10, s.cornerPx());
    }

    /** {@code inset-0} on a later spec must override an earlier per-edge inset, like any other property. */
    @Test
    void insetsMergeWithOverlayWinning() {
        StyleSpec base = TailwindParser.parse("absolute left-4");
        StyleSpec merged = base.merged(TailwindParser.parse("left-8 top-2"));
        assertTrue(merged.absolute(), "position must survive a merge that does not restate it");
        assertEquals(32, merged.insetLeft());
        assertEquals(8, merged.insetTop());
    }

    /**
     * The lenient path memoizes on the class string; past the cap parsing must keep working
     * (uncached), never fail or evict. One method: filling the shared cache would otherwise
     * break the identity assertion depending on test order.
     */
    @Test
    void lenientParseIsCachedAndSurvivesTheCap() {
        assertSame(TailwindParser.parse("flex-row items-center p-4 gap-2"),
                TailwindParser.parse("flex-row items-center p-4 gap-2"));
        for (int i = 0; i < 4200; i++) {
            TailwindParser.parse("w-[" + (100_000 + i) + "px]");
        }
        StyleSpec s = TailwindParser.parse("h-[777001px]");
        assertEquals(777001, s.height().px());
        assertEquals(s, TailwindParser.parse("h-[777001px]"));
    }

    /** A failing variant token must not register an empty variant (would make elements hoverable for nothing). */
    @Test
    void badVariantTokenRegistersNoVariant() {
        StyleSpec s = TailwindParser.parse("p-2 hover:not-a-utility-xyzzy");
        assertFalse(s.variants().containsKey(Variant.HOVER));
        assertEquals(8, s.padLeft());
    }

    /** A failing variant token must not disturb utilities already applied to the same variant. */
    @Test
    void badVariantTokenLeavesEarlierVariantUtilitiesIntact() {
        StyleSpec s = TailwindParser.parse("hover:p-2 hover:not-a-utility-xyzzy");
        assertEquals(8, s.variants().get(Variant.HOVER).padLeft());
    }

    /** A failing base token must leave the base spec exactly as if it were never written. */
    @Test
    void badBaseTokenLeavesBaseUntouched() {
        assertEquals(TailwindParser.parse("p-4"), TailwindParser.parse("p-4 not-a-utility-xyzzy"));
    }

    /**
     * The resolved box a reactive re-apply writes. When a bound class string changes from
     * {@code "p-4 opacity-50"} to {@code ""} the applier re-runs against the new spec, so that spec
     * has to say "no padding, fully opaque" and not merely "nothing declared" — otherwise the
     * previous string's padding and alpha stay on the View forever.
     */
    @Test
    void aDroppedBoxUtilityResolvesToItsDefault() {
        StyleSpec padded = TailwindParser.parse("p-4 opacity-50");
        assertTrue(padded.hasPadding());
        assertArrayEquals(new int[]{16, 16, 16, 16}, padded.paddingPx());
        assertEquals(0.5f, padded.opacity());

        StyleSpec dropped = TailwindParser.parse("");
        assertFalse(dropped.hasPadding());
        assertArrayEquals(new int[]{0, 0, 0, 0}, dropped.paddingPx());
        assertNull(dropped.opacity());
    }

    /** Padding resolves per edge: an edge no utility set is zero, never the neighbouring edge's value. */
    @Test
    void paddingPxResolvesEachEdgeIndependently() {
        assertArrayEquals(new int[]{0, 8, 0, 0}, TailwindParser.parse("pt-2").paddingPx());
        assertArrayEquals(new int[]{4, 8, 4, 8}, TailwindParser.parse("px-1 py-2").paddingPx());
    }

    @Test
    void namedLengthKeywords() {
        assertEquals(StyleSpec.Length.fillParent(), StyleSpec.Length.named("full"));
        assertEquals(StyleSpec.Length.wrapContent(), StyleSpec.Length.named("auto"));
        assertNull(StyleSpec.Length.named("12"));
        assertEquals(StyleSpec.Length.fillParent(), TailwindParser.parse("w-full").width());
        assertEquals(StyleSpec.Length.wrapContent(), TailwindParser.parse("h-auto").height());
    }
}
