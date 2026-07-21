package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.bind.BindingResolver;
import fr.lacaleche.glue.mcsx.core.bind.ElementResolver;
import fr.lacaleche.glue.mcsx.core.bind.McsxBindException;
import fr.lacaleche.glue.mcsx.core.bind.Scope;
import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxText;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.core.style.VariantStyleResolver;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.attributeNamed;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.bindingAttribute;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.requireAttribute;
import static fr.lacaleche.glue.mcsx.core.style.VariantStyleResolver.optionalInt;

/**
 * Turns a {@code .mcsx} document plus a controller into a live ModernUI {@code View} tree (§9). One
 * binder instance builds one subtree, synchronously, on the UI/render thread. Context-bound binder
 * instances share screen services while carrying an immutable lexical and lifecycle context.
 *
 * <p>Reactivity is the update model: each {@code {{binding}}} / two-way {@code value={}} is one
 * {@link Effect} updating one View property; {@code <if>}/{@code <for>} rebuild their subtree inside
 * an effect, disposing the nested effects on each toggle. There is no diffing.
 */
public final class ViewBinder {

    /**
     * The builder behind each {@link Tags#BUILT_IN} name. The names live in {@code core} so the
     * linter can read them without a window; {@code ViewBinderVocabularyTest} pins the two together,
     * because a tag present here but not there (or the reverse) is a screen that lints clean and
     * fails in-game.
     */
    static final Map<String, ElementBuilder> BUILT_INS = Map.ofEntries(
            Map.entry("if", view(ViewTree::buildIf)),
            Map.entry("for", view(ViewTree::buildFor)),
            Map.entry("slot", view(ViewTree::buildSlot)),
            Map.entry("overlay", view(ViewTree::buildOverlay)),
            Map.entry("text", view(ViewBinder::buildText)),
            Map.entry("input", view(ViewBinder::buildInput)),
            Map.entry("scroll", view(ViewBinder::buildScroll)),
            Map.entry("icon", view(ViewBinder::buildIcon)),
            Map.entry("key", view(ViewBinder::buildKeyBinding)),
            Map.entry("div", view(ViewBinder::buildContainer)),
            Map.entry("button", view(ViewBinder::buildContainer)));

    /** Resolves a component id (from an {@code <import from="…"/>}) to its parsed document. */
    @FunctionalInterface
    public interface DocumentResolver {
        McsxDocument resolve(String id);
    }

    private final ScreenController controller;
    private final Context context;
    private final ComponentRegistry registry;
    private final DocumentResolver resolver;
    private final VariantStyleResolver styles;
    /** Element × scope → what it binds to: handlers, writable signals, the scopes a tag introduces. */
    private final ElementResolver elements;

    private final BuildContext buildContext;

    private static final StyleSpec NO_BASE = StyleSpec.builder().build();

    /** The z-layer overlays render into, above the content. */
    private final OverlayHost overlays;
    private final KeyBindings keys;
    /** Views that declared an {@code id="…"}, so an {@code <overlay anchor="…">} can find them. */
    private final Map<String, View> anchors;

    private ViewBinder(ScreenController controller, Context context,
                       ComponentRegistry registry, DocumentResolver resolver,
                       OverlayHost overlays, BuildContext buildContext) {
        this.controller = controller;
        this.context = context;
        this.registry = registry;
        this.resolver = resolver;
        this.overlays = overlays;
        this.keys = overlays.keyBindings();
        this.anchors = new HashMap<>();
        this.buildContext = buildContext;
        BindingResolver bindings = new BindingResolver(controller);
        this.styles = new VariantStyleResolver(bindings);
        this.elements = new ElementResolver(bindings, styles);
    }

    private ViewBinder(ViewBinder source, BuildContext buildContext) {
        this.controller = source.controller;
        this.context = source.context;
        this.registry = source.registry;
        this.resolver = source.resolver;
        this.styles = source.styles;
        this.elements = source.elements;
        this.overlays = source.overlays;
        this.keys = source.keys;
        this.anchors = source.anchors;
        this.buildContext = buildContext;
    }

    public static ViewInstance bind(McsxDocument document, ScreenController controller, Context context,
                                    ComponentRegistry registry, DocumentResolver resolver) {
        return bind(document, controller, context, registry, resolver, null);
    }

    /** Binds a document whose transient layers should render in a host owned by a larger workspace. */
    public static ViewInstance bind(McsxDocument document, ScreenController controller, Context context,
                                    ComponentRegistry registry, DocumentResolver resolver,
                                    OverlayHost sharedOverlays) {
        List<Effect> effects = new ArrayList<>();
        OverlayHost overlays = sharedOverlays != null ? sharedOverlays : new OverlayHost(context);
        BuildContext buildContext = new BuildContext(
                null, document.imports(), null, effects, InheritedText.ROOT);
        ViewBinder binder = new ViewBinder(
                controller, context, registry, resolver, overlays, buildContext);

        try {
            ElementView built = binder.buildElement(document.root());
            View content = built.view();
            // Same contract as ViewTree.bindLayoutParams: a reactive root class must be able to resize
            // and re-centre the root, and a static one must not pay for an effect that can never re-run.
            Effect rootLayout = Effect.of(() ->
                    content.setLayoutParams(ViewStyles.rootLayoutParams(built.layoutStyle().get())));
            if (rootLayout.hasSources()) {
                effects.add(rootLayout);
            }

            FrameLayout root = new FrameLayout(context);
            root.addView(content);
            if (sharedOverlays == null) {
                root.addView(binder.overlays, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                root.setFocusable(true);
                root.setFocusableInTouchMode(true);
                root.setOnKeyListener(binder.keys);
                root.requestFocus();
            }
            return new ViewInstance(root, effects);
        } catch (RuntimeException | Error failure) {
            for (Effect effect : effects) {
                effect.dispose();
            }
            effects.clear();
            throw failure;
        }
    }

    public Context context() {
        return context;
    }

    public void addEffect(Effect effect) {
        buildContext.effects().add(effect);
    }

    /** Builds a subtree on behalf of a {@link NativeComponent}. */
    public View buildView(McsxElement element) {
        return buildElement(element).view();
    }

    ElementView buildElement(McsxElement element) {
        ElementBuilder builtIn = BUILT_INS.get(element.tag());
        if (builtIn != null) {
            return builtIn.build(this, element);
        }
        if (buildContext.imports().containsKey(element.tag())) {
            return ViewTree.buildComponent(this, element);
        }
        return new ElementView(buildNative(element), () -> resolveStyle(element));
    }

    private static ElementBuilder view(ViewBuilder builder) {
        return (binder, element) -> new ElementView(
                builder.build(binder, element), () -> binder.resolveStyle(element));
    }

    @FunctionalInterface
    private interface ElementBuilder {
        ElementView build(ViewBinder binder, McsxElement element);
    }

    @FunctionalInterface
    private interface ViewBuilder {
        View build(ViewBinder binder, McsxElement element);
    }

    ViewBinder inContext(BuildContext context) {
        return new ViewBinder(this, context);
    }

    BuildContext buildContext() {
        return buildContext;
    }

    ElementResolver elements() {
        return elements;
    }

    McsxDocument resolveDocument(String id) {
        return resolver.resolve(id);
    }

    OverlayHost overlays() {
        return overlays;
    }

    View anchor(String id) {
        return anchors.get(id);
    }

    /** {@code {ref}} → a live value supplier (dotted paths ok); reading it tracks the dependency. */
    public Supplier<Object> resolveBinding(String ref, McsxElement element) {
        return elements.binding(ref, buildContext.scope(), element);
    }

    /** {@code {ref}} → the raw writable {@link Signal} at the leaf, or {@code null} if read-only. */
    public Signal<?> resolveSignal(String ref, McsxElement element) {
        return elements.signal(ref, buildContext.scope(), element);
    }

    /** {@code onClick={name}} → a controller invocation (with loop item for 1-arg methods, §9.7). */
    public Runnable resolveHandler(String name, McsxElement element) {
        Runnable handler = elements.handlerOrNull(name, buildContext.scope());
        if (handler == null) {
            throw new McsxBindException("no click handler '" + name + "' on "
                    + controller.getClass().getSimpleName(), element.line(), element.column());
        }
        return handler;
    }

    private View buildContainer(McsxElement element) {
        Orientation dflt = "button".equals(element.tag()) || "row".equals(element.attribute("dir"))
                ? Orientation.ROW : Orientation.COLUMN;
        return buildStyledContainer(element, NO_BASE, dflt);
    }

    /**
     * Builds a {@link FlexLayout} for an element, layering {@code base} (a component's recipe style)
     * <em>under</em> the element's own {@code class}/attrs — so a call-site {@code class=} still wins.
     * Shared by the base {@code <div>}/{@code <button>} tags (with an empty base) and the recipe-driven
     * library components. The container's own text color / font size are CSS-inherited: they become
     * the subtree default for its children, restored afterwards. Public so components can reuse it.
     */
    public View buildStyledContainer(McsxElement element, StyleSpec base, Orientation defaultOrientation) {
        FlexLayout layout = new FlexLayout(context);
        StyleSpec spec = base.merged(resolveStyle(element));
        Orientation orientation = spec.orientation() != null ? spec.orientation() : defaultOrientation;
        boolean interactive = isInteractive(element);
        // One apply function, run either once or inside an effect. Splitting the two paths is what
        // let `applyContainer` be forgotten from the reactive one, so a switch recoloured its track
        // while its thumb stayed put; and what let a dropped background keep painting.
        applyReactively(() -> {
            StyleSpec live = base.merged(resolveStyle(element));
            ViewStyles.applyContainer(layout, live, defaultOrientation);
            ViewStyles.applyBox(layout, live, true);
            // hasOnClickListeners covers the third owner of the flag: a native component that reused
            // this container and wired its own listener, which the markup-derived check cannot see.
            ViewStyles.applyStateFlags(layout, live, interactive || layout.hasOnClickListeners());
        });
        Scope subtree = elements.stateScope(
                element, elements.selectionScope(element, buildContext.scope()));
        // Re-resolved, not baked: a child <text>/<icon> reads this from inside its own restyle effect,
        // which is what keeps a reactive text-* on this container reaching a colourless label.
        Supplier<InheritedText> outer = buildContext.inherited();
        Supplier<InheritedText> childText =
                () -> outer.get().overlaidBy(base.merged(resolveStyle(element)),
                        elements.stringAttribute(element, "font", buildContext.scope()));
        BuildContext childContext = new BuildContext(
                subtree, buildContext.imports(), buildContext.slot(), buildContext.effects(),
                childText);
        ViewTree.appendChildren(inContext(childContext), layout, element, orientation);
        animate(layout, spec);
        applyEnabled(layout, element);
        applyTooltip(layout, element);
        registerAnchor(layout, element);
        wireClick(layout, element);
        wireDrag(layout, element);
        return layout;
    }

    /**
     * Starts an {@code animate-*} loop and ties its lifetime to the subtree. The animator is started
     * once from the initial spec, never from a reactive re-apply — restarting a spin on every hover
     * would make it stutter. A {@link ValueAnimator} strongly references its listener, so an
     * {@code <if>} rebuilding this subtree must cancel it or it spins forever against a dead view.
     */
    private void animate(View view, StyleSpec spec) {
        ValueAnimator animator = Animations.start(view, spec);
        if (animator != null) {
            onTeardown(animator::cancel);
        }
    }

    /**
     * {@code <div drag={signal} min="0" max="100">} — pointer position along the element's width
     * becomes the signal's value, on press and while dragging. The last gesture MCSX could not
     * express, and the reason {@code slider} was the final native component.
     *
     * <p>The gesture asks its ancestors not to intercept, or a surrounding {@code <scroll>} would
     * steal the drag the moment the pointer moved.
     */
    private void wireDrag(View view, McsxElement element) {
        McsxAttribute drag = bindingAttribute(element, "drag");
        if (drag == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Signal<Integer> value = (Signal<Integer>) elements.writableSignal(
                drag, buildContext.scope(), element);
        // stringAttribute, not attribute: a component forwards these as min={min}.
        Integer low = optionalInt(elements.stringAttribute(element, "min", buildContext.scope()));
        Integer high = optionalInt(elements.stringAttribute(element, "max", buildContext.scope()));
        int min = low != null ? low : 0;
        int max = high != null ? high : 100;
        if (max <= min) {
            throw new McsxBindException("drag requires max > min", element.line(), element.column());
        }
        view.setClickable(true);
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
                return false;
            }
            if (action == MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            int width = v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
            if (width <= 0) {
                return true;
            }
            float fraction = (event.getX() - v.getPaddingLeft()) / width;
            fraction = Math.clamp(fraction, 0f, 1f);
            value.set(min + Math.round(fraction * (max - min)));
            return true;
        });
    }

    /**
     * Whether the element wires an interaction of its own — the second owner of the clickable channel
     * that {@link ViewStyles#applyStateFlags} needs, since {@code wireClick}/{@code wireDrag} run once
     * at build while the restyle effect re-runs without them. A {@code tooltip=} does not count:
     * ModernUI shows it from the hover dispatch, not from the hovered state, so it needs no flag.
     */
    private boolean isInteractive(McsxElement element) {
        return elements.clickAction(element, buildContext.scope()) != null
                || bindingAttribute(element, "drag") != null;
    }

    /** {@code id="…"} makes a view addressable by {@code <overlay anchor="…">}. */
    private void registerAnchor(View view, McsxElement element) {
        String id = elements.stringAttribute(element, "id", buildContext.scope());
        if (id != null && !id.isBlank()) {
            anchors.put(id, view);
        }
    }

    /**
     * {@code tooltip="…"} hands the label to ModernUI, which already owns the hover delay, the popup
     * window and its dismissal — so MCSX has no tooltip primitive of its own. A bound
     * {@code tooltip={signal}} is read once, like {@code disabled}.
     */
    private void applyTooltip(View view, McsxElement element) {
        String tooltip = elements.stringAttribute(element, "tooltip", buildContext.scope());
        if (tooltip != null && !tooltip.isBlank()) {
            view.setTooltipText(tooltip);
        }
    }

    /**
     * {@code disabled="true"} switches the view to the disabled state, which is what makes a
     * {@code disabled:} class variant fire and stops the click listener. Static: a bound
     * {@code disabled={signal}} is not re-evaluated.
     */
    private void applyEnabled(View view, McsxElement element) {
        String disabled = elements.stringAttribute(element, "disabled", buildContext.scope());
        if (disabled != null) {
            if (!disabled.equals("true") && !disabled.equals("false")) {
                throw new McsxBindException("disabled must be 'true' or 'false'",
                        element.line(), element.column());
            }
            view.setEnabled(!Boolean.parseBoolean(disabled));
        }
    }

    private View buildText(McsxElement element) {
        TextView text = new TextView(context);
        StyleSpec initial = resolveStyle(element);
        boolean interactive = isInteractive(element);
        applyReactively(() -> styleText(text, element, resolveStyle(element), interactive));
        animate(text, initial);
        bindText(text, element);
        applyTooltip(text, element);
        wireClick(text, element);
        return text;
    }

    /** Inherited colour/size/weight first, then the element's own classes on top. */
    private void styleText(TextView text, McsxElement element, StyleSpec spec, boolean interactive) {
        applyInheritedText(text, element);
        applyOwnFont(text, element);
        ViewStyles.applyBox(text, spec, true);
        ViewStyles.applyStateFlags(text, spec, interactive);
        ViewStyles.applyText(text, spec, true);
    }

    /**
     * Applies styling in an {@link Effect}. Class bindings and the active theme can both invalidate
     * it, while literal styles with no token dependencies simply run once.
     */
    private void applyReactively(Runnable apply) {
        addEffect(Effect.of(apply));
    }

    /**
     * {@code EditText(Context)} paints no box of its own — it only makes itself focusable, clickable
     * and vertically centred — so the class string is the whole truth about an input's box, exactly as
     * it is for a {@code <div>}. It is styled reset-then-layer for the same reason a {@code <text>} is:
     * a bound class dropping {@code text-sm} or a validation {@code border-danger} has to take the
     * value it painted with it. The interaction flags are the one channel left alone — an input is
     * focusable because it is editable, not because of anything in its class.
     */
    private View buildInput(McsxElement element) {
        EditText input = new EditText(context);
        applyReactively(() -> {
            StyleSpec spec = resolveStyle(element);
            applyInheritedText(input, element);
            applyOwnFont(input, element);
            ViewStyles.applyBox(input, spec, true);
            ViewStyles.applyText(input, spec, true);
        });
        String placeholder = elements.stringAttribute(element, "placeholder", buildContext.scope());
        if (placeholder != null) {
            input.setHint(placeholder);
        }
        McsxAttribute value = bindingAttribute(element, "value");
        if (value != null) {
            bindInputValue(input, value.value(), element);
        }
        wireClick(input, element);
        return input;
    }

    /**
     * {@code ScrollView(Context)} sets a scroller, edge effects and the scrollbar drawables — which
     * ride their own channel, not {@code setBackground} — but no background and no padding, so here
     * too the class string is the whole truth about the box.
     */
    private View buildScroll(McsxElement element) {
        ScrollView scroll = new ScrollView(context);
        FlexLayout inner = new FlexLayout(context);
        applyReactively(() -> {
            StyleSpec spec = resolveStyle(element);
            ViewStyles.applyBox(scroll, spec, true);
            // Clickable only: applyStateFlags would also lower focusable, which the constructor set
            // for keyboard scrolling. The flag's owners here are the spec and the click wiring.
            scroll.setClickable(ViewStyles.needsClickable(spec) || scroll.hasOnClickListeners());
            ViewStyles.applyContainer(inner, spec, Orientation.COLUMN);
        });
        ViewTree.appendChildren(this, inner, element, Orientation.COLUMN);
        scroll.addView(inner);
        wireClick(scroll, element);
        return scroll;
    }

    private View buildNative(McsxElement element) {
        NativeComponent component = registry.get(element.tag());
        if (component == null) {
            throw new McsxBindException("unknown element <" + element.tag() + ">",
                    element.line(), element.column());
        }
        return component.create(context, element, this);
    }

    /** Icons match the surrounding text size by default, as they do in every icon-and-label row. */
    private static final int DEFAULT_ICON_SIZE_PX = 16;

    /**
     * {@code <icon name="check" size="16" class="text-muted"/>} — a glyph from a resource font.
     * With no {@code text-*} class it inherits the subtree's colour, so an icon inside a destructive
     * menu item turns red with its label and needs no styling of its own.
     */
    private View buildIcon(McsxElement element) {
        String name = elements.stringAttribute(element, "name", buildContext.scope());
        String glyph = elements.stringAttribute(element, "glyph", buildContext.scope());
        boolean hasName = name != null && !name.isBlank();
        boolean hasGlyph = glyph != null && !glyph.isBlank();
        if (hasName == hasGlyph) {
            throw new McsxBindException("<icon> requires exactly one of name=\"…\" or glyph=\"…\"",
                    element.line(), element.column());
        }
        Integer size = optionalInt(element.attribute("size"));
        IconView view = new IconView(context, size != null ? size : DEFAULT_ICON_SIZE_PX);
        StyleSpec initial = resolveStyle(element);
        boolean interactive = isInteractive(element);
        applyReactively(() -> styleIcon(view, element, resolveStyle(element), interactive));
        animate(view, initial);
        wireClick(view, element);
        return view;
    }

    private void styleIcon(IconView view, McsxElement element, StyleSpec spec, boolean interactive) {
        String font = elements.stringAttribute(element, "font", buildContext.scope());
        String inheritedFont = buildContext.inherited().get().font();
        String effectiveFont = font != null ? font
                : inheritedFont != null ? inheritedFont : FontRegistry.DEFAULT_ICONS;
        String name = elements.stringAttribute(element, "name", buildContext.scope());
        String glyph = elements.stringAttribute(element, "glyph", buildContext.scope());
        boolean hasName = name != null && !name.isBlank();
        boolean hasGlyph = glyph != null && !glyph.isBlank();
        if (hasName == hasGlyph) {
            throw new McsxBindException("<icon> requires exactly one of name=\"…\" or glyph=\"…\"",
                    element.line(), element.column());
        }
        try {
            view.setIcon(effectiveFont, name, glyph);
        } catch (IllegalArgumentException e) {
            throw new McsxBindException(e.getMessage(), element.line(), element.column());
        }
        view.setColor(spec.textColor() != null
                ? ViewStyles.resolve(spec.textColor())
                : buildContext.inherited().get().resolvedColor());
        ViewStyles.applyBox(view, spec, true);
        ViewStyles.applyStateFlags(view, spec, interactive);
    }

    private void applyOwnFont(TextView view, McsxElement element) {
        String font = elements.stringAttribute(element, "font", buildContext.scope());
        if (font == null) {
            return;
        }
        try {
            FontRegistry.getInstance().bindText(view, font);
        } catch (IllegalArgumentException e) {
            throw new McsxBindException(e.getMessage(), element.line(), element.column());
        }
    }

    private void applyInheritedText(TextView view, McsxElement element) {
        try {
            buildContext.inherited().get().applyTo(view);
        } catch (IllegalArgumentException e) {
            throw new McsxBindException(e.getMessage(), element.line(), element.column());
        }
    }

    /** {@code <key combo="ctrl+k" onPress={handler}/>} — a screen-wide shortcut; renders nothing. */
    private View buildKeyBinding(McsxElement element) {
        String combo = requireAttribute(element, "combo", "<key> requires combo=\"…\"");
        McsxAttribute press = attributeNamed(element, "onPress");
        if (press == null) {
            throw new McsxBindException("<key> requires onPress={…}", element.line(), element.column());
        }
        Runnable action = resolveHandler(press.value(), element);
        try {
            // the binding unregisters when its subtree is torn down or the document closes — a
            // shared workspace KeyBindings would otherwise accumulate every document ever bound
            onTeardown(keys.register(combo, action));
        } catch (IllegalArgumentException e) {
            throw new McsxBindException(e.getMessage(), element.line(), element.column());
        }
        return gone();
    }

    /** Ties a cleanup to the current subtree: it runs on an {@code <if>}/{@code <for>} teardown. */
    private void onTeardown(Runnable cleanup) {
        Effect lifecycle = Effect.of(() -> { });
        lifecycle.onDispose(cleanup);
        addEffect(lifecycle);
    }

    /** A tag that binds behaviour but renders nothing still has to return a View to the tree. */
    private View gone() {
        View placeholder = new View(context);
        placeholder.setVisibility(View.GONE);
        return placeholder;
    }

    /**
     * A text run written directly inside a container ({@code <button>Save</button>}) renders as an
     * implicit {@code <text>}: it carries no style of its own and inherits colour and size from the
     * enclosing subtree, exactly as an explicit {@code <text>} with no classes would.
     */
    View buildBareText(McsxText run, McsxElement owner) {
        TextView view = new TextView(context);
        applyReactively(() -> applyInheritedText(view, owner));
        List<Object> parts = new ArrayList<>();
        elements.collectParts(run, owner, buildContext.scope(), parts);
        bindParts(view, parts);
        return view;
    }

    private void bindText(TextView view, McsxElement element) {
        List<Object> parts = new ArrayList<>();
        for (McsxContent child : element.children()) {
            if (child instanceof McsxText text) {
                elements.collectParts(text, element, buildContext.scope(), parts);
            }
        }
        bindParts(view, parts);
    }

    /** Re-renders through an {@link Effect} only when some part is a live {@code {{binding}}}. */
    private void bindParts(TextView view, List<Object> parts) {
        if (ElementResolver.isDynamic(parts)) {
            addEffect(Effect.of(() -> view.setText(ElementResolver.compose(parts))));
        } else {
            view.setText(ElementResolver.compose(parts));
        }
    }

    private void bindInputValue(EditText input, String ref, McsxElement element) {
        Signal<?> raw = resolveSignal(ref, element);
        if (raw == null) {
            Supplier<Object> value = resolveBinding(ref, element);
            addEffect(Effect.of(() -> setTextIfChanged(input, String.valueOf(value.get()))));
            return;
        }
        @SuppressWarnings("unchecked")
        Signal<String> signal = (Signal<String>) raw;
        addEffect(Effect.of(() -> setTextIfChanged(input, signal.get())));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                signal.set(editable.toString());
            }
        });
    }

    private void wireClick(View view, McsxElement element) {
        Runnable action = elements.clickAction(element, buildContext.scope());
        if (action != null) {
            view.setOnClickListener(v -> action.run());
            view.setClickable(true);
        }
    }

    /** Resolves an element's styling ({@code class} + raw box attrs), merged. Base tags carry no
     *  design of their own — they are raw platform primitives; design lives in the component library
     *  ({@code .mcsx} files) built on top. Public so native components can style themselves
     *  consistently via {@code ViewStyles}; the mechanics live in {@link VariantStyleResolver}. */
    public StyleSpec resolveStyle(McsxElement element) {
        return styles.resolveStyle(element, buildContext.scope());
    }

    private static void setTextIfChanged(EditText input, String value) {
        if (!input.getText().toString().equals(value)) {
            input.setText(value);
        }
    }

}
