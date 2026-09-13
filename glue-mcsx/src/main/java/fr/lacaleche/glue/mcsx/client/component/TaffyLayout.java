package fr.lacaleche.glue.mcsx.client.component;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.geometry.TaffyRect;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.LengthPercentage;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.tree.Layout;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import fr.lacaleche.glue.mcsx.Mcsx;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.ViewParent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provisional bridge that lets Taffy position direct ModernUI child Views.
 *
 * <p>This class is internal until intrinsic measurement and nested layout ownership are validated.
 */
@Environment(EnvType.CLIENT)
class TaffyLayout extends ViewGroup {

    private static final int DEFAULT_PADDING = 0;
    private static final int DEFAULT_GAP = 0;

    private final TaffyTree tree = new TaffyTree();
    private final NodeId root;
    private final Map<View, ChildBinding> bindings = new IdentityHashMap<>();
    private final FlexDirection direction;
    private final PropertyUpdateQueue propertyUpdates;
    private KeyedChildren<?, ?> keyedChildren;
    private ThemeScope themeScope;
    private StyleScope styleScope;
    private boolean replaceableDefaultStylesheet;
    private boolean reconcilingKeyedChildren;
    private int lastWidthMeasureSpec;
    private int lastHeightMeasureSpec;
    private boolean rootStyleDirty = true;
    /** Test instrumentation: layout passes are otherwise unobservable from outside the container. */
    private int layoutComputationCount;

    TaffyLayout(Context context) {
        this(context, FlexDirection.COLUMN);
    }

    TaffyLayout(Context context, FlexDirection direction) {
        this(
                context,
                direction,
                Core::postOnUiThread,
                exception -> Mcsx.LOGGER.error("Failed to apply MCSX property update", exception)
        );
    }

    TaffyLayout(
            Context context,
            FlexDirection direction,
            Consumer<Runnable> uiPoster,
            Consumer<RuntimeException> failureReporter
    ) {
        super(context);
        this.direction = direction;
        this.setClipChildren(false);
        this.propertyUpdates = new PropertyUpdateQueue(uiPoster, failureReporter);
        this.root = this.tree.newWithChildren(this.createRootStyle(0, 0));
    }

    @Override
    protected final void onViewAdded(View child) {
        super.onViewAdded(child);
        if (this.bindings.containsKey(child)) {
            throw new IllegalStateException("View already has a Taffy layout node");
        }

        LayoutParams layoutParams = child.getLayoutParams();
        boolean gone = child.getVisibility() == GONE;
        NodeId node = this.tree.newLeafWithMeasure(
                this.createChildStyle(child, layoutParams, gone),
                (knownDimensions, availableSpace) -> this.measureChild(
                        child,
                        knownDimensions,
                        availableSpace
                )
        );
        this.bindings.put(child, new ChildBinding(
                node,
                layoutParams.width,
                layoutParams.height,
                gone
        ));
        this.tree.insertChildAtIndex(this.root, this.indexOfChild(child), node);
        StyleScope scope = findStyleScope(this);
        if (scope != null) {
            scope.recomputeSubtree(child);
        }
    }

    @Override
    protected final void onViewRemoved(View child) {
        StyleScope scope = findStyleScope(this);
        if (scope != null) {
            scope.clearSubtree(child);
        }
        super.onViewRemoved(child);
        ChildBinding binding = this.bindings.remove(child);
        if (binding == null) {
            throw new IllegalStateException("Removed View has no Taffy layout node");
        }

        this.tree.removeChild(this.root, binding.node);
        this.tree.remove(binding.node);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        this.checkKeyedMutation();
        super.addView(child, index, params);
    }

    @Override
    public void removeView(View view) {
        this.checkKeyedMutation();
        super.removeView(view);
    }

    @Override
    public void removeViewInLayout(View view) {
        this.checkKeyedMutation();
        super.removeViewInLayout(view);
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        this.checkKeyedMutation();
        super.removeViewsInLayout(start, count);
    }

    @Override
    public void removeViewAt(int index) {
        this.checkKeyedMutation();
        super.removeViewAt(index);
    }

    @Override
    public void removeViews(int start, int count) {
        this.checkKeyedMutation();
        super.removeViews(start, count);
    }

    @Override
    public void removeAllViews() {
        this.checkKeyedMutation();
        super.removeAllViews();
    }

    @Override
    public void removeAllViewsInLayout() {
        this.checkKeyedMutation();
        super.removeAllViewsInLayout();
    }

    @Override
    public void bringChildToFront(View child) {
        this.checkKeyedMutation();
        super.bringChildToFront(child);
    }

    @Override
    protected final void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        this.synchronizeChildren();
        this.updateRootConstraints(widthMeasureSpec, heightMeasureSpec);
        this.tree.computeLayout(
                this.root,
                TaffySize.of(
                        this.toAvailableSpace(widthMeasureSpec),
                        this.toAvailableSpace(heightMeasureSpec)
                )
        );
        this.layoutComputationCount++;

        Layout rootLayout = this.tree.getLayout(this.root);
        int measuredWidth = resolveSize(this.ceil(rootLayout.size().width), widthMeasureSpec);
        int measuredHeight = resolveSize(this.ceil(rootLayout.size().height), heightMeasureSpec);
        this.measureFinalChildren();
        this.setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected final void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < this.getChildCount(); index++) {
            View child = this.getChildAt(index);
            if (child.getVisibility() == GONE) {
                child.layout(0, 0, 0, 0);
                continue;
            }

            Layout layout = this.tree.getLayout(this.requireBinding(child).node);
            int childLeft = Math.round(layout.location().x);
            int childTop = Math.round(layout.location().y);
            int childWidth = Math.round(layout.size().width);
            int childHeight = Math.round(layout.size().height);
            child.layout(
                    childLeft,
                    childTop,
                    childLeft + childWidth,
                    childTop + childHeight
            );
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        boolean themeMounted = false;
        boolean styleMounted = false;
        try {
            if (this.themeScope != null) {
                this.themeScope.mount();
                themeMounted = true;
            }
            if (this.styleScope != null) {
                this.styleScope.mount();
                styleMounted = true;
            }
            if (this.keyedChildren != null) {
                this.keyedChildren.mount();
            }
        } catch (RuntimeException exception) {
            // A partially attached root never receives a matching detach, so roll back what was
            // mounted before letting the failure propagate.
            RuntimeException failure = exception;
            if (styleMounted) {
                failure = LifecycleCleanup.attempt(failure, this.styleScope::unmount);
            }
            if (themeMounted) {
                failure = LifecycleCleanup.attempt(failure, this.themeScope::unmount);
            }
            throw failure;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        RuntimeException failure = null;
        if (this.keyedChildren != null) {
            failure = LifecycleCleanup.attempt(failure, this.keyedChildren::unmount);
        }
        if (this.styleScope != null) {
            failure = LifecycleCleanup.attempt(failure, this.styleScope::unmount);
        }
        if (this.themeScope != null) {
            failure = LifecycleCleanup.attempt(failure, this.themeScope::unmount);
        }
        failure = LifecycleCleanup.attempt(failure, this.propertyUpdates::clear);
        failure = LifecycleCleanup.attempt(failure, super::onDetachedFromWindow);
        LifecycleCleanup.finish(failure);
    }

    final void addChildren(View... children) {
        this.addChildren(Arrays.asList(Objects.requireNonNull(children, "children")));
    }

    final void addChildren(List<? extends View> children) {
        Objects.requireNonNull(children, "children");
        if (this.keyedChildren != null) {
            throw new IllegalStateException("Cannot add static children to a keyed container");
        }
        for (View child : children) {
            this.addView(Objects.requireNonNull(child, "child"));
        }
    }

    final <T, K> void bindChildren(
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super Value<T>, ? extends View> viewFactory
    ) {
        if (this.keyedChildren != null) {
            throw new IllegalStateException("Keyed children are already bound");
        }
        if (this.getChildCount() != 0) {
            throw new IllegalStateException("Keyed containers cannot contain static children");
        }

        this.keyedChildren = new KeyedChildren<>(this, items, keyExtractor, viewFactory);
        if (this.isAttachedToWindow()) {
            this.keyedChildren.mount();
        }
    }

    final void provideTheme(Theme theme) {
        this.provideTheme(Signal.of(Objects.requireNonNull(theme, "theme")));
    }

    final void provideTheme(Value<? extends Theme> theme) {
        if (this.themeScope != null) {
            throw new IllegalStateException("A theme is already provided by this root");
        }
        if (this.isAttachedToWindow()) {
            throw new IllegalStateException("A theme must be provided before the root is attached");
        }

        this.themeScope = new ThemeScope(theme);
    }

    final void provideStylesheet(Stylesheet stylesheet) {
        Stylesheet required = Objects.requireNonNull(stylesheet, "stylesheet");
        this.provideStylesheet(
                Signal.of(required),
                StyleDefaults.INCLUDE,
                required == Stylesheet.empty()
        );
    }

    final void provideStylesheet(Value<? extends Stylesheet> stylesheet) {
        this.provideStylesheet(stylesheet, StyleDefaults.INCLUDE, false);
    }

    final void provideRawStylesheet(Stylesheet stylesheet) {
        this.provideStylesheet(
                Signal.of(Objects.requireNonNull(stylesheet, "stylesheet")),
                StyleDefaults.OMIT,
                false
        );
    }

    final void provideRawStylesheet(Value<? extends Stylesheet> stylesheet) {
        this.provideStylesheet(stylesheet, StyleDefaults.OMIT, false);
    }

    private void provideStylesheet(
            Value<? extends Stylesheet> stylesheet,
            StyleDefaults defaults,
            boolean replaceableDefault
    ) {
        Value<? extends Stylesheet> required = Objects.requireNonNull(stylesheet, "stylesheet");
        if (this.styleScope != null && !this.replaceableDefaultStylesheet) {
            throw new IllegalStateException("A stylesheet is already provided by this root");
        }
        if (this.isAttachedToWindow()) {
            throw new IllegalStateException(
                    "A stylesheet must be provided before the root is attached"
            );
        }

        Value<? extends Stylesheet> source = defaults == StyleDefaults.INCLUDE
                ? required.map(Stylesheets::withDefaults)
                : required;
        this.styleScope = new StyleScope(this, source);
        this.replaceableDefaultStylesheet = replaceableDefault;
    }

    final void synchronizeChildOrder(List<View> orderedChildren) {
        if (orderedChildren.size() != this.getChildCount()) {
            throw new IllegalArgumentException("Child order does not contain every child");
        }

        boolean changed = false;
        for (int index = 0; index < orderedChildren.size(); index++) {
            if (this.getChildAt(index) != orderedChildren.get(index)) {
                changed = true;
                break;
            }
        }
        if (!changed) return;

        for (View child : orderedChildren) {
            if (child.getParent() != this) {
                throw new IllegalArgumentException("Ordered View is not a child of this container");
            }
            this.bringChildToFront(child);
        }
        for (View child : orderedChildren) {
            this.tree.removeChild(this.root, this.requireBinding(child).node);
        }
        for (int index = 0; index < orderedChildren.size(); index++) {
            this.tree.insertChildAtIndex(
                    this.root,
                    index,
                    this.requireBinding(orderedChildren.get(index)).node
            );
        }
        this.requestLayout();
    }

    final void reconcileKeyedChildren(Runnable reconciliation) {
        if (this.reconcilingKeyedChildren) {
            throw new IllegalStateException("Keyed child reconciliation is already running");
        }

        this.reconcilingKeyedChildren = true;
        try {
            reconciliation.run();
        } finally {
            this.reconcilingKeyedChildren = false;
        }
    }

    final int themeDependencyCount() {
        return this.themeScope == null ? 0 : this.themeScope.dependencyCount();
    }

    final void markStylesheetLayoutDirty() {
        this.rootStyleDirty = true;
        this.requestLayout();
    }

    final int layoutComputationCount() {
        return this.layoutComputationCount;
    }

    final int propertyApplicationCount() {
        return this.propertyUpdates.successfulApplicationCount();
    }

    static PropertyUpdateQueue findPropertyUpdateQueue(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof TaffyLayout layout) {
                return layout.propertyUpdates;
            }
            parent = parent.getParent();
        }
        return null;
    }

    static ThemeScope findThemeScope(View view) {
        View current = view;
        while (current != null) {
            if (current instanceof TaffyLayout layout && layout.themeScope != null) {
                return layout.themeScope;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return null;
    }

    static StyleScope findStyleScope(View view) {
        View current = view;
        while (current != null) {
            if (current instanceof TaffyLayout layout && layout.styleScope != null) {
                return layout.styleScope;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return null;
    }

    private void synchronizeChildren() {
        for (int index = 0; index < this.getChildCount(); index++) {
            View child = this.getChildAt(index);
            ChildBinding binding = this.requireBinding(child);
            if (child.isLayoutRequested()) {
                this.tree.markDirty(binding.node);
            }

            LayoutParams layoutParams = child.getLayoutParams();
            boolean gone = child.getVisibility() == GONE;
            this.updateChildStyle(child, binding, layoutParams, gone);
        }
    }

    private void checkKeyedMutation() {
        if (this.keyedChildren != null && !this.reconcilingKeyedChildren) {
            throw new IllegalStateException(
                    "Keyed children are controlled by their bound collection"
            );
        }
    }

    private void updateChildStyle(
            View child,
            ChildBinding binding,
            LayoutParams layoutParams,
            boolean gone
    ) {
        StyleMetadata metadata = StyleComponents.metadata(child);
        StyleValue width = metadata == null ? null : metadata.computedValue("width");
        StyleValue maxWidth = metadata == null ? null : metadata.computedValue("max-width");
        StyleValue flexGrow = metadata == null ? null : metadata.computedValue("flex-grow");
        if (binding.layoutWidth == layoutParams.width
                && binding.layoutHeight == layoutParams.height
                && binding.gone == gone
                && Objects.equals(binding.width, width)
                && Objects.equals(binding.maxWidth, maxWidth)
                && Objects.equals(binding.flexGrow, flexGrow)) return;

        binding.layoutWidth = layoutParams.width;
        binding.layoutHeight = layoutParams.height;
        binding.gone = gone;
        binding.width = width;
        binding.maxWidth = maxWidth;
        binding.flexGrow = flexGrow;
        this.tree.setStyle(binding.node, this.createChildStyle(child, layoutParams, gone));
    }

    private void updateRootConstraints(int widthMeasureSpec, int heightMeasureSpec) {
        if (!this.rootStyleDirty
                && this.lastWidthMeasureSpec == widthMeasureSpec
                && this.lastHeightMeasureSpec == heightMeasureSpec) return;

        this.rootStyleDirty = false;
        this.lastWidthMeasureSpec = widthMeasureSpec;
        this.lastHeightMeasureSpec = heightMeasureSpec;
        this.tree.setStyle(this.root, this.createRootStyle(widthMeasureSpec, heightMeasureSpec));
    }

    private void measureFinalChildren() {
        for (int index = 0; index < this.getChildCount(); index++) {
            View child = this.getChildAt(index);
            if (child.getVisibility() == GONE) continue;

            Layout layout = this.tree.getLayout(this.requireBinding(child).node);
            child.measure(
                    MeasureSpec.makeMeasureSpec(
                            Math.round(layout.size().width),
                            MeasureSpec.EXACTLY
                    ),
                    MeasureSpec.makeMeasureSpec(
                            Math.round(layout.size().height),
                            MeasureSpec.EXACTLY
                    )
            );
        }
    }

    private TaffyStyle createRootStyle(int widthMeasureSpec, int heightMeasureSpec) {
        TaffyStyle style = new TaffyStyle();
        style.display = TaffyDisplay.FLEX;
        style.flexDirection = this.direction;
        style.alignItems = this.alignItems("align-items", AlignItems.START);
        style.justifyContent = this.alignContent("justify-content", AlignContent.START);
        style.size = TaffySize.of(
                this.toRootDimension(widthMeasureSpec),
                this.toRootDimension(heightMeasureSpec)
        );
        style.padding = TaffyRect.all(LengthPercentage.length(
                this.pixelValue("padding", DEFAULT_PADDING)
        ));
        style.gap = TaffySize.all(LengthPercentage.length(
                this.pixelValue("gap", DEFAULT_GAP)
        ));
        return style;
    }

    private TaffyStyle createChildStyle(View child, LayoutParams layoutParams, boolean gone) {
        StyleMetadata metadata = StyleComponents.metadata(child);
        StyleValue width = metadata == null ? null : metadata.computedValue("width");
        StyleValue maxWidth = metadata == null ? null : metadata.computedValue("max-width");
        StyleValue flexGrow = metadata == null ? null : metadata.computedValue("flex-grow");
        TaffyStyle style = new TaffyStyle();
        style.display = gone ? TaffyDisplay.NONE : TaffyDisplay.BLOCK;
        style.size = TaffySize.of(
                this.toStyledDimension(width, this.toChildDimension(layoutParams.width)),
                this.toChildDimension(layoutParams.height)
        );
        style.maxSize = TaffySize.of(
                this.toStyledDimension(maxWidth, TaffyDimension.AUTO),
                TaffyDimension.AUTO
        );
        if (flexGrow instanceof StyleValue.Scalar scalar) {
            style.flexGrow = scalar.value();
        }
        return style;
    }

    private int pixelValue(String property, int fallback) {
        StyleMetadata metadata = StyleComponents.metadata(this);
        if (metadata != null
                && metadata.computedValue(property) instanceof StyleValue.Literal literal) {
            return literal.value();
        }
        return fallback;
    }

    private AlignItems alignItems(String property, AlignItems fallback) {
        String keyword = this.keywordValue(property);
        if (keyword == null) return fallback;

        return switch (keyword) {
            case "start" -> AlignItems.START;
            case "end" -> AlignItems.END;
            case "center" -> AlignItems.CENTER;
            case "stretch" -> AlignItems.STRETCH;
            default -> fallback;
        };
    }

    private AlignContent alignContent(String property, AlignContent fallback) {
        String keyword = this.keywordValue(property);
        if (keyword == null) return fallback;

        return switch (keyword) {
            case "start" -> AlignContent.START;
            case "end" -> AlignContent.END;
            case "center" -> AlignContent.CENTER;
            case "space-between" -> AlignContent.SPACE_BETWEEN;
            case "space-around" -> AlignContent.SPACE_AROUND;
            case "space-evenly" -> AlignContent.SPACE_EVENLY;
            default -> fallback;
        };
    }

    private String keywordValue(String property) {
        StyleMetadata metadata = StyleComponents.metadata(this);
        if (metadata != null
                && metadata.computedValue(property) instanceof StyleValue.Keyword keyword) {
            return keyword.value();
        }
        return null;
    }

    private TaffyDimension toStyledDimension(
            StyleValue value,
            TaffyDimension fallback
    ) {
        return switch (value) {
            case StyleValue.Literal literal -> TaffyDimension.length(literal.value());
            case StyleValue.Percent percent -> TaffyDimension.percent(percent.value());
            case null, default -> fallback;
        };
    }

    private FloatSize measureChild(
            View child,
            FloatSize knownDimensions,
            TaffySize<AvailableSpace> availableSpace
    ) {
        child.measure(
                this.toChildMeasureSpec(knownDimensions.width, availableSpace.width),
                this.toChildMeasureSpec(knownDimensions.height, availableSpace.height)
        );
        return new FloatSize(child.getMeasuredWidth(), child.getMeasuredHeight());
    }

    private TaffyDimension toChildDimension(int layoutDimension) {
        if (layoutDimension >= 0) {
            return TaffyDimension.length(layoutDimension);
        }
        if (layoutDimension == LayoutParams.MATCH_PARENT) {
            return TaffyDimension.percent(1);
        }
        return TaffyDimension.AUTO;
    }

    private int toChildMeasureSpec(float knownDimension, AvailableSpace availableSpace) {
        if (Float.isFinite(knownDimension)) {
            return MeasureSpec.makeMeasureSpec(
                    this.clampMeasureSize(Math.round(knownDimension)),
                    MeasureSpec.EXACTLY
            );
        }
        if (availableSpace.isDefinite()) {
            return MeasureSpec.makeMeasureSpec(
                    this.clampMeasureSize((int) Math.floor(availableSpace.getValue())),
                    MeasureSpec.AT_MOST
            );
        }
        return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }

    private TaffyDimension toRootDimension(int measureSpec) {
        if (MeasureSpec.getMode(measureSpec) == MeasureSpec.EXACTLY) {
            return TaffyDimension.length(MeasureSpec.getSize(measureSpec));
        }

        return TaffyDimension.AUTO;
    }

    private AvailableSpace toAvailableSpace(int measureSpec) {
        if (MeasureSpec.getMode(measureSpec) == MeasureSpec.UNSPECIFIED) {
            return AvailableSpace.MAX_CONTENT;
        }

        return AvailableSpace.definite(MeasureSpec.getSize(measureSpec));
    }

    private ChildBinding requireBinding(View child) {
        ChildBinding binding = this.bindings.get(child);
        if (binding == null) {
            throw new IllegalStateException("View has no Taffy layout node");
        }
        return binding;
    }

    private int ceil(float value) {
        return (int) Math.ceil(value);
    }

    private int clampMeasureSize(int value) {
        return Math.max(0, Math.min(value, 0x3fffffff));
    }

    private static final class ChildBinding {

        private final NodeId node;
        private int layoutWidth;
        private int layoutHeight;
        private boolean gone;
        private StyleValue width;
        private StyleValue maxWidth;
        private StyleValue flexGrow;

        private ChildBinding(NodeId node, int layoutWidth, int layoutHeight, boolean gone) {
            this.node = node;
            this.layoutWidth = layoutWidth;
            this.layoutHeight = layoutHeight;
            this.gone = gone;
        }
    }

    private enum StyleDefaults {
        INCLUDE,
        OMIT
    }
}
