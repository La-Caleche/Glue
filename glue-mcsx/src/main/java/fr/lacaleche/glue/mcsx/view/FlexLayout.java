package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.layout.FlexEngine;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Align;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Justify;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A flex container: the real {@code flex-row}/{@code flex-col} that {@code LinearLayout} only
 * approximated. It supports all six {@code justify-content} values, {@code align-items} with a
 * per-child {@code align-self} override, a true {@code gap} (not emulated with margins),
 * {@code grow}/{@code shrink}, {@code flex-wrap}, and {@code position: absolute} children.
 *
 * <p>All the main-axis arithmetic lives in {@link FlexEngine}, which is pure and headless-tested;
 * this class only translates between that and ModernUI's measure/layout protocol. Children are
 * measured twice, as flexbox requires: once with an unbounded main axis to discover each one's
 * natural base size, then again at the size the engine assigned, so text re-wraps to its final width.
 *
 * <p>Wrapped lines pack against the cross start and keep their own extents; only an unwrapped line
 * absorbs the container's spare cross space. {@code align-content} is therefore not modelled.
 */
public class FlexLayout extends ViewGroup {

    private Orientation orientation = Orientation.COLUMN;
    private Justify justify = Justify.START;
    private Align alignItems = Align.STRETCH;
    private int gap;
    private boolean wrap;

    private final List<FlexEngine.Item> items = new ArrayList<>();
    private final List<View> flowing = new ArrayList<>();
    /** Children with {@code position: absolute}: outside the line, placed by their insets. */
    private final List<View> positioned = new ArrayList<>();
    /** Indices into {@link #flowing}, one array per line; a single line unless {@code flex-wrap}. */
    private List<int[]> groups = List.of();
    /** The lines solved during {@code onMeasure}; {@code onLayout} places children from them. */
    private final List<FlexEngine.Line> lines = new ArrayList<>();
    /** Each line's cross extent, in the same order as {@link #groups}. */
    private int[] lineCross = new int[0];

    public FlexLayout(Context context) {
        super(context);
    }

    public void setOrientation(Orientation value) {
        if (orientation != value) {
            orientation = value;
            requestLayout();
        }
    }

    public void setJustify(Justify value) {
        if (justify != value) {
            justify = value;
            requestLayout();
        }
    }

    public void setAlignItems(Align value) {
        if (alignItems != value) {
            alignItems = value;
            requestLayout();
        }
    }

    public void setGap(int value) {
        if (gap != value) {
            gap = value;
            requestLayout();
        }
    }

    public void setWrap(boolean value) {
        if (wrap != value) {
            wrap = value;
            requestLayout();
        }
    }

    public Orientation orientation() {
        return orientation;
    }

    public Justify justify() {
        return justify;
    }

    public Align alignItems() {
        return alignItems;
    }

    public int gap() {
        return gap;
    }

    public boolean wrap() {
        return wrap;
    }

    private boolean isRow() {
        return orientation == Orientation.ROW;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mainSpec = isRow() ? widthMeasureSpec : heightMeasureSpec;
        int crossSpec = isRow() ? heightMeasureSpec : widthMeasureSpec;
        int mainPadding = isRow() ? getPaddingLeft() + getPaddingRight() : getPaddingTop() + getPaddingBottom();
        int crossPadding = isRow() ? getPaddingTop() + getPaddingBottom() : getPaddingLeft() + getPaddingRight();

        // Only an EXACTLY container has free space to distribute; an AT_MOST one shrinks to fit,
        // which leaves nothing for justify-content or grow to act on (CSS shrink-to-fit).
        boolean bounded = MeasureSpec.getMode(mainSpec) == MeasureSpec.EXACTLY;
        int availableMain = Math.max(0, MeasureSpec.getSize(mainSpec) - mainPadding);
        // Wrapping breaks against the space on offer, which an AT_MOST container also has — it just
        // has no *free* space afterwards to grow or justify into. Only a truly unbounded main axis
        // (a scroll viewport) has no width to break against, so nothing there can wrap.
        boolean mainConstrained = MeasureSpec.getMode(mainSpec) != MeasureSpec.UNSPECIFIED;
        int availableCross = Math.max(0, MeasureSpec.getSize(crossSpec) - crossPadding);
        boolean crossBounded = MeasureSpec.getMode(crossSpec) != MeasureSpec.UNSPECIFIED;
        // Stretch eagerly only when our own cross size is already fixed. Under AT_MOST we are sizing
        // to our content, so stretching a child to the space *offered* would inflate us to fill it —
        // a wrap-width popover would become as wide as the window. stretchToLine grows them after.
        boolean crossExactly = MeasureSpec.getMode(crossSpec) == MeasureSpec.EXACTLY;

        items.clear();
        flowing.clear();
        positioned.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.absolute) {
                positioned.add(child);
                continue;
            }
            measureChildAgainstCross(child, lp, availableCross, crossBounded, crossExactly,
                    MeasureSpec.UNSPECIFIED, 0);
            flowing.add(child);
            items.add(itemFor(child, lp, availableMain, bounded));
        }

        groups = wrap && mainConstrained
                ? FlexEngine.breakLines(items, availableMain, gap)
                : List.of(FlexEngine.allIndices(items.size()));

        lines.clear();
        int mainContent = 0;
        for (int[] group : groups) {
            FlexEngine.Line solved = FlexEngine.solve(
                    items, group, availableMain, bounded, gap, justify);
            lines.add(solved);
            mainContent = Math.max(mainContent, solved.contentSize());
        }

        // A wrapped child stretches to its own line, not to the container, so it can only be told its
        // cross size exactly when there is exactly one line.
        boolean singleLine = groups.size() == 1;
        // The pass-2 cross spec differs from pass 1 only for stretch children when crossExactly
        // flips off for wrapped lines; when it can't differ, a child already measured with the very
        // same specs need not be measured again.
        boolean crossSpecUnchanged = singleLine || !crossExactly;
        lineCross = new int[groups.size()];
        int crossContent = 0;
        for (int g = 0; g < groups.size(); g++) {
            int[] group = groups.get(g);
            int maxCross = 0;
            for (int k = 0; k < group.length; k++) {
                View child = flowing.get(group[k]);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int solvedMain = lines.get(g).sizes()[k];
                int childMain = isRow() ? lp.width : lp.height;
                // Pass 1 measured the main axis EXACTLY only for a declared pixel size; everything
                // else it measured UNSPECIFIED. A measurement that merely happens to equal the
                // solved size was taken under a different mode, and a child left with an unbounded
                // main axis reports itself unbounded to ITS children — which silently drops any
                // percentage size nested inside it.
                boolean mainSpecUnchanged = childMain >= 0 && childMain == solvedMain;
                if (!(crossSpecUnchanged && mainSpecUnchanged)) {
                    measureChildAgainstCross(child, lp, availableCross, crossBounded,
                            crossExactly && singleLine, MeasureSpec.EXACTLY, solvedMain);
                }
                maxCross = Math.max(maxCross, isRow() ? child.getMeasuredHeight() : child.getMeasuredWidth());
            }
            lineCross[g] = maxCross;
            crossContent += maxCross;
        }
        crossContent += gap * (groups.size() - 1);

        int measuredMain = resolveSize(mainContent + mainPadding, mainSpec);
        int measuredCross = resolveSize(crossContent + crossPadding, crossSpec);
        // Only a single line absorbs the container's spare cross space (CSS align-content: stretch);
        // wrapped lines pack against the cross start and keep their own heights.
        if (singleLine) {
            lineCross[0] = measuredCross - crossPadding;
        }
        for (int g = 0; g < groups.size(); g++) {
            stretchToLine(lineCross[g], groups.get(g), lines.get(g));
        }
        setMeasuredDimension(isRow() ? measuredMain : measuredCross, isRow() ? measuredCross : measuredMain);

        // Absolute children resolve against the content box, so they can only be measured once this
        // container's own size is final — and they never feed back into it.
        int innerWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        int innerHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        for (View child : positioned) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.measure(
                    positionedSpec(lp.width, lp.left, lp.right, innerWidth, lp.minWidth, lp.maxWidth),
                    positionedSpec(lp.height, lp.top, lp.bottom, innerHeight, lp.minHeight, lp.maxHeight));
            // A hug-content child measured under AT_MOST can land below its minimum; grow it back.
            int minned = Math.max(child.getMeasuredWidth(), lp.minWidth);
            int minnedH = Math.max(child.getMeasuredHeight(), lp.minHeight);
            if (minned != child.getMeasuredWidth() || minnedH != child.getMeasuredHeight()) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(minned, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(minnedH, MeasureSpec.EXACTLY));
            }
        }
    }

    /**
     * The measure spec for one axis of an absolute child. Opposing insets pin both edges and so fix
     * the size; otherwise an explicit size wins, and a child with neither hugs its content.
     */
    private static int positionedSpec(int declared, int start, int end, int extent, int min, int max) {
        if (declared == LayoutParams.MATCH_PARENT) {
            return MeasureSpec.makeMeasureSpec(clamp(extent, min, max), MeasureSpec.EXACTLY);
        }
        if (declared >= 0) {
            return MeasureSpec.makeMeasureSpec(clamp(declared, min, max), MeasureSpec.EXACTLY);
        }
        if (start != LayoutParams.INSET_UNSET && end != LayoutParams.INSET_UNSET) {
            return MeasureSpec.makeMeasureSpec(
                    clamp(Math.max(0, extent - start - end), min, max), MeasureSpec.EXACTLY);
        }
        return MeasureSpec.makeMeasureSpec(Math.min(extent, max), MeasureSpec.AT_MOST);
    }

    /** Places an absolute child on one axis: {@code start} wins, then {@code end}, else the content edge. */
    private static int positionedOffset(int padding, int start, int end, int extent, int size) {
        if (start != LayoutParams.INSET_UNSET) {
            return padding + start;
        }
        if (end != LayoutParams.INSET_UNSET) {
            return padding + extent - end - size;
        }
        return padding;
    }

    /**
     * Grows every {@code align-items: stretch} child to the line's final cross size. A stretched child
     * can only be measured exactly once that size is known, and when the container's own cross axis is
     * unbounded it isn't known until every child has been measured — so equal-height cards in a
     * wrap-height row need this third pass. Children already at the line size are left alone.
     */
    private void stretchToLine(int cross, int[] group, FlexEngine.Line solved) {
        for (int k = 0; k < group.length; k++) {
            View child = flowing.get(group[k]);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childCross = isRow() ? lp.height : lp.width;
            float crossFraction = isRow() ? lp.heightFraction : lp.widthFraction;
            boolean stretches = alignmentOf(lp) == Align.STRETCH || childCross == LayoutParams.MATCH_PARENT;
            if (!stretches || childCross >= 0 || crossFraction > 0f) {
                continue;
            }
            int measured = isRow() ? child.getMeasuredHeight() : child.getMeasuredWidth();
            if (measured >= cross) {
                continue;
            }
            int crossSpec = MeasureSpec.makeMeasureSpec(cross, MeasureSpec.EXACTLY);
            int mainSpec = MeasureSpec.makeMeasureSpec(solved.sizes()[k], MeasureSpec.EXACTLY);
            child.measure(isRow() ? mainSpec : crossSpec, isRow() ? crossSpec : mainSpec);
        }
    }

    /**
     * Measures one child at a given main-axis constraint, sizing its cross axis from its layout
     * params and the effective alignment. A stretched child is told its cross size exactly, which is
     * how {@code items-stretch} works without the old {@code MATCH_PARENT} trick.
     */
    private void measureChildAgainstCross(View child, LayoutParams lp, int availableCross,
                                          boolean crossBounded, boolean crossExactly,
                                          int mainMode, int mainSize) {
        int childCross = isRow() ? lp.height : lp.width;
        int childMain = isRow() ? lp.width : lp.height;
        float crossFraction = isRow() ? lp.heightFraction : lp.widthFraction;

        int crossMin = isRow() ? lp.minHeight : lp.minWidth;
        int crossMax = isRow() ? lp.maxHeight : lp.maxWidth;

        int crossSpec;
        if (crossFraction > 0f && crossBounded) {
            crossSpec = MeasureSpec.makeMeasureSpec(
                    clamp(Math.round(availableCross * crossFraction), crossMin, crossMax),
                    MeasureSpec.EXACTLY);
        } else if (childCross >= 0) {
            crossSpec = MeasureSpec.makeMeasureSpec(clamp(childCross, crossMin, crossMax), MeasureSpec.EXACTLY);
        } else if (!crossBounded) {
            crossSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (childCross == LayoutParams.MATCH_PARENT || alignmentOf(lp) == Align.STRETCH) {
            crossSpec = MeasureSpec.makeMeasureSpec(clamp(availableCross, crossMin, crossMax),
                    crossExactly ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
        } else {
            crossSpec = MeasureSpec.makeMeasureSpec(Math.min(availableCross, crossMax), MeasureSpec.AT_MOST);
        }

        int mainSpec;
        if (mainMode == MeasureSpec.EXACTLY) {
            mainSpec = MeasureSpec.makeMeasureSpec(mainSize, MeasureSpec.EXACTLY);
        } else if (childMain >= 0) {
            mainSpec = MeasureSpec.makeMeasureSpec(childMain, MeasureSpec.EXACTLY);
        } else {
            mainSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }

        child.measure(isRow() ? mainSpec : crossSpec, isRow() ? crossSpec : mainSpec);

        // The AT_MOST/UNSPECIFIED cross branches bound only from above, so a hug-content child can
        // land below its declared min-w/min-h; grow it back — the same fixup positioned children get.
        if (crossMin > 0) {
            int measuredCross = isRow() ? child.getMeasuredHeight() : child.getMeasuredWidth();
            if (measuredCross < crossMin) {
                int fix = MeasureSpec.makeMeasureSpec(crossMin, MeasureSpec.EXACTLY);
                child.measure(isRow() ? mainSpec : fix, isRow() ? fix : mainSpec);
            }
        }
    }

    /**
     * The child's flex item. A {@code MATCH_PARENT} main size means "fill the line", which flexbox
     * expresses as a zero basis that grows — so it competes for space with any explicit {@code grow}
     * rather than overflowing the container.
     *
     * <p>The basis is never clamped to the container: an item is allowed to overflow (that is what
     * {@code shrink} exists to resolve), and an unbounded parent reports a main size of zero anyway,
     * so clamping there would collapse every child to nothing.
     */
    private FlexEngine.Item itemFor(View child, LayoutParams lp, int availableMain, boolean bounded) {
        int childMain = isRow() ? lp.width : lp.height;
        float mainFraction = isRow() ? lp.widthFraction : lp.heightFraction;
        float grow = lp.grow;
        int basis;
        if (mainFraction > 0f && bounded) {
            basis = Math.round(availableMain * mainFraction);
        } else if (childMain == LayoutParams.MATCH_PARENT) {
            basis = 0;
            grow = Math.max(grow, 1f);
        } else if (childMain >= 0) {
            basis = childMain;
        } else {
            basis = isRow() ? child.getMeasuredWidth() : child.getMeasuredHeight();
        }
        int min = isRow() ? lp.minWidth : lp.minHeight;
        int max = isRow() ? lp.maxWidth : lp.maxHeight;
        return new FlexEngine.Item(Math.max(basis, min), grow, lp.shrink, min, Math.max(min, max));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Align alignmentOf(LayoutParams lp) {
        return lp.alignSelf != null ? lp.alignSelf : alignItems;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int mainPadding = isRow() ? getPaddingLeft() : getPaddingTop();
        int crossCursor = isRow() ? getPaddingTop() : getPaddingLeft();

        for (int g = 0; g < groups.size(); g++) {
            int[] group = groups.get(g);
            FlexEngine.Line solved = lines.get(g);
            for (int k = 0; k < group.length; k++) {
                View child = flowing.get(group[k]);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int mainSize = solved.sizes()[k];
                int mainOffset = mainPadding + solved.offsets()[k];
                int childCross = isRow() ? child.getMeasuredHeight() : child.getMeasuredWidth();
                int crossOffset = crossCursor + crossOffsetFor(alignmentOf(lp), childCross, lineCross[g]);

                if (isRow()) {
                    child.layout(mainOffset, crossOffset, mainOffset + mainSize, crossOffset + childCross);
                } else {
                    child.layout(crossOffset, mainOffset, crossOffset + childCross, mainOffset + mainSize);
                }
            }
            crossCursor += lineCross[g] + gap;
        }

        int innerWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        int innerHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        for (View child : positioned) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            int x = positionedOffset(getPaddingLeft(), lp.left, lp.right, innerWidth, width);
            int y = positionedOffset(getPaddingTop(), lp.top, lp.bottom, innerHeight, height);
            child.layout(x, y, x + width, y + height);
        }
    }

    private static int crossOffsetFor(Align align, int childCross, int crossExtent) {
        return switch (align) {
            case CENTER -> Math.max(0, (crossExtent - childCross) / 2);
            case END -> Math.max(0, crossExtent - childCross);
            case START, STRETCH -> 0;
        };
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams source) {
        return source instanceof LayoutParams flex ? flex : new LayoutParams(source);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
        return params instanceof LayoutParams;
    }

    /** Per-child flex properties: {@code grow}, {@code shrink} and an {@code align-self} override. */
    public static class LayoutParams extends ViewGroup.LayoutParams {

        /** An inset that was never declared — distinct from an inset of zero, which pins the edge. */
        public static final int INSET_UNSET = Integer.MIN_VALUE;

        /** {@code absolute}: placed by the insets below, contributing nothing to the container's size. */
        public boolean absolute;
        public int left = INSET_UNSET;
        public int top = INSET_UNSET;
        public int right = INSET_UNSET;
        public int bottom = INSET_UNSET;

        public float grow;
        public float shrink;
        public Align alignSelf;
        /** {@code w-1/2} → 0.5; zero means the axis is not fractional. */
        public float widthFraction;
        public float heightFraction;
        /** {@code min-w-*}/{@code max-w-*} bounds; the flex solver clamps growth and shrink to them. */
        public int minWidth;
        public int maxWidth = Integer.MAX_VALUE;
        public int minHeight;
        public int maxHeight = Integer.MAX_VALUE;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
}
