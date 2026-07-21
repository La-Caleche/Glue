package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.style.TailwindParser;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The two appliers a reactive restyle re-runs on every tick. Neither reads anything off the Context,
 * so both are exercisable without a ModernUI runtime — {@code layoutParams} needs no View at all, and
 * {@code applyBox} needs only a detached one.
 */
class ViewStylesTest {

    /** {@code new View(context)} stores the Context and never calls it, so a stub is enough. */
    private static final class HeadlessContext extends Context {

        @Override
        public Resources getResources() {
            return null;
        }

        @Override
        public void setTheme(ResourceId resId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return null;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }

    /** Padding is read on the vertical edges: {@code getPaddingLeft/Right} resolve RTL, which needs a
     *  live ModernUI instance, while {@code getPaddingTop/Bottom} return the written value directly. */
    private static View view() {
        return new View(new HeadlessContext());
    }

    private static FlexLayout.LayoutParams params(String classes, Orientation parent) {
        return ViewStyles.layoutParams(TailwindParser.parse(classes), parent);
    }

    /**
     * The params are a complete description of the box, never a patch: whatever the previous class
     * string declared, the class string that replaces it fully determines the new params. This is
     * what lets {@code ViewTree} publish a fresh instance on every restyle and be done.
     */
    @Test
    void everyChannelIsDerivedFromTheSpec() {
        FlexLayout.LayoutParams grown = params("grow w-full min-w-8 max-w-32", Orientation.COLUMN);
        assertEquals(1f, grown.grow);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, grown.width);
        assertEquals(32, grown.minWidth);
        assertEquals(128, grown.maxWidth);

        FlexLayout.LayoutParams bare = params("", Orientation.COLUMN);
        assertEquals(0f, bare.grow, "a class string that drops grow must lay out as grow-0");
        assertEquals(0f, bare.shrink);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, bare.width);
        assertEquals(0, bare.minWidth);
        assertEquals(Integer.MAX_VALUE, bare.maxWidth);
        assertEquals(0f, bare.widthFraction);
        assertNull(bare.alignSelf);
        assertFalse(bare.absolute);
        assertEquals(FlexLayout.LayoutParams.INSET_UNSET, bare.left);
    }

    /** A fraction cannot be resolved without the container's size, so it rides on its own channel. */
    @Test
    void aFractionalWidthRidesTheFractionChannel() {
        FlexLayout.LayoutParams half = params("w-1/2", Orientation.COLUMN);
        assertEquals(0.5f, half.widthFraction);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, half.width);
    }

    /** {@code mx-auto} centers on the cross axis of a column, and means nothing on a row's main axis. */
    @Test
    void anAutoMarginCentersOnTheCrossAxisOnly() {
        assertEquals(Align.CENTER, params("mx-auto", Orientation.COLUMN).alignSelf);
        assertNull(params("mx-auto", Orientation.ROW).alignSelf);
        assertEquals(Align.END, params("mx-auto self-end", Orientation.COLUMN).alignSelf,
                "an explicit self-* beats the auto margin");
    }

    /**
     * A bound {@code w={fraction}} layers over the class-declared size in the same params instance —
     * the ordering {@code ViewTree.bindLayoutParams} relies on to give the binding the last word.
     */
    @Test
    void aBoundSizeOverridesTheClassDeclaredOne() {
        FlexLayout.LayoutParams progress = params("w-full h-2", Orientation.ROW);
        ViewStyles.applySize(progress, true, 0.25f);

        assertEquals(0.25f, progress.widthFraction);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, progress.width);
        assertEquals(8, progress.height, "the unbound axis keeps its class-declared size");
    }

    /** A zero fraction must collapse the fill, not degrade to WRAP and hug its own padding. */
    @Test
    void aZeroBoundFractionCollapsesTheAxis() {
        FlexLayout.LayoutParams progress = params("w-full", Orientation.ROW);
        ViewStyles.applySize(progress, true, 0f);

        assertEquals(0f, progress.widthFraction);
        assertEquals(0, progress.width);
    }

    /**
     * The reactive re-apply. When a bound class string changes from {@code "p-4 opacity-50 bg-surface"}
     * to {@code ""}, the spec is the whole truth about the box: the padding, the dim and the fill of
     * the string it replaced all have to go, or they stay on the View for the rest of its life.
     */
    @Test
    void aReapplyClearsTheBoxTheNewClassDropped() {
        View view = view();

        ViewStyles.applyBox(view, TailwindParser.parse("p-4 opacity-50 bg-surface"), true);
        assertEquals(16, view.getPaddingTop());
        assertEquals(16, view.getPaddingBottom());
        assertEquals(0.5f, view.getAlpha());
        assertNotNull(view.getBackground());

        ViewStyles.applyBox(view, TailwindParser.parse(""), true);
        assertEquals(0, view.getPaddingTop(), "a dropped p-4 must stop padding");
        assertEquals(0, view.getPaddingBottom());
        assertEquals(1f, view.getAlpha(), "a dropped opacity-50 must stop dimming");
        assertNull(view.getBackground(), "a dropped bg-* must stop painting");
    }

    /**
     * The two-arg form is the {@link NativeComponent} contract, and it is why clearing is opt-in: a
     * native paints its own box in Java and then layers the element's class over it, so a property the
     * class does not mention has to survive.
     */
    @Test
    void applyBoxLeavesABoxTheSpecDoesNotDeclareAlone() {
        View view = view();
        view.setPadding(3, 3, 3, 3);
        view.setAlpha(0.25f);

        ViewStyles.applyBox(view, TailwindParser.parse("rounded"));

        assertEquals(3, view.getPaddingTop(), "a native component's own padding survives");
        assertEquals(0.25f, view.getAlpha());
        assertNotNull(view.getBackground());
    }

    /** A declared value always wins; the flag only decides what an <em>absent</em> one means. */
    @Test
    void aDeclaredBoxIsWrittenUnderEitherContract() {
        View cleared = view();
        ViewStyles.applyBox(cleared, TailwindParser.parse("py-2 opacity-25"), true);
        assertEquals(8, cleared.getPaddingTop());
        assertEquals(0.25f, cleared.getAlpha());

        View kept = view();
        kept.setPadding(3, 3, 3, 3);
        ViewStyles.applyBox(kept, TailwindParser.parse("py-2 opacity-25"));
        assertEquals(8, kept.getPaddingTop(), "a declared value overwrites the native's own");
        assertEquals(0.25f, kept.getAlpha());
    }
}
