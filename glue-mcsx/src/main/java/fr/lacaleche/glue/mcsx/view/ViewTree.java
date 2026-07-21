package fr.lacaleche.glue.mcsx.view;

import fr.lacaleche.glue.mcsx.core.bind.ElementResolver;
import fr.lacaleche.glue.mcsx.core.bind.McsxBindException;
import fr.lacaleche.glue.mcsx.core.bind.Scope;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxText;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Reactive;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec;
import fr.lacaleche.glue.mcsx.core.style.StyleSpec.Orientation;
import fr.lacaleche.glue.mcsx.view.BuildContext.SlotContent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.attributeNamed;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.bindingAttribute;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.isVariantConfig;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.requireAttribute;
import static fr.lacaleche.glue.mcsx.core.bind.ElementResolver.requireBinding;

/** Builds structural tags, imported components, slots, and parent-child layout. */
final class ViewTree {

    private static final StyleSpec NO_STYLE = StyleSpec.builder().build();

    private ViewTree() {
    }

    static void appendChildren(ViewBinder binder, ViewGroup parent, McsxElement element,
                               Orientation orientation) {
        for (McsxContent child : element.children()) {
            appendChild(binder, parent, child, element, orientation);
        }
    }

    static View buildIf(ViewBinder binder, McsxElement element) {
        McsxAttribute cond = requireBinding(element, "cond", "<if> requires cond={…}");
        Supplier<Object> condition = binder.resolveBinding(cond.value(), element);
        FlexLayout container = styledFlowContainer(binder, element);
        Orientation orientation = container.orientation();
        List<Effect> body = new ArrayList<>();
        ViewBinder bodyBinder = binder.inContext(binder.buildContext().withEffects(body));

        Effect gate = Effect.of(() -> {
            boolean show = Boolean.TRUE.equals(condition.get());
            // The condition is the gate's ONLY dependency. Every read the build path performs —
            // resolveStyle, a bound attr, a component's <variants on> prop — would otherwise subscribe
            // the gate, so an unrelated signal would tear the subtree down and reset its <state>.
            Reactive.untracked(() -> {
                disposeAll(body);
                container.removeAllViews();
                if (show) {
                    appendChildren(bodyBinder, container, element, orientation);
                }
            });
        }, () -> disposeAll(body));
        binder.addEffect(gate);
        return container;
    }

    /**
     * {@code <for each={list} as="…">} — one row per item, keyed by item VALUE. A new item
     * {@code equals}-equal to a previous run's item takes over that row's Views and Effects
     * wholesale, so an unchanged row's subtree — its {@code <state>} signals included — survives a
     * list mutation; only rows whose item disappeared are torn down, and only new items build.
     * Matching is greedy in order, so duplicate items degrade to positional reuse instead of
     * double-claiming one row. A reused row's effects were created above the untracked sentinel on
     * their original build and stay subscribed — nothing re-tracks on reuse.
     */
    static View buildFor(ViewBinder binder, McsxElement element) {
        String as = requireAttribute(element, "as", "<for> requires a non-empty as=\"…\"");
        McsxAttribute each = requireBinding(element, "each", "<for> requires each={…}");
        Supplier<Object> list = binder.resolveBinding(each.value(), element);
        FlexLayout container = styledFlowContainer(binder, element);
        Orientation orientation = container.orientation();
        List<Row> rows = new ArrayList<>();

        Effect gate = Effect.of(() -> {
            // The list is the gate's ONLY dependency: a row's own signals belong to that row's
            // effects, not to the loop. Tracking them here made ticking one checkbox rebuild every
            // row, and each rebuild mints fresh <state> signals — silently wiping the siblings.
            Object value = list.get();
            Reactive.untracked(() -> {
                if (value != null && !(value instanceof List<?>)) {
                    throw new McsxBindException("<for each> must resolve to a List",
                            element.line(), element.column());
                }
                List<?> items = value == null ? List.of() : (List<?>) value;
                List<Row> unmatched = new ArrayList<>(rows);
                rows.clear();
                Row[] reused = matchRows(unmatched, items);
                disposeRows(unmatched);
                // removeAllViews clears every child's parent, so re-adding a reused row's same View
                // instances below is legal. A focused child of a reused row still loses focus here,
                // like any removed-then-re-added View: reuse preserves state, not focus.
                container.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    Row row = reused[i];
                    if (row == null) {
                        row = buildRow(binder, container, element, orientation, as, items.get(i));
                    } else {
                        for (View view : row.views()) {
                            container.addView(view);
                        }
                    }
                    rows.add(row);
                }
            });
        }, () -> disposeRows(rows));
        binder.addEffect(gate);
        return container;
    }

    /** One built {@code <for>} row: the item it was keyed on, the effects its subtree registered,
     *  and the Views its build appended to the loop container. */
    private record Row(Object item, List<Effect> effects, List<View> views) {
    }

    /** For each new item, the first still-unclaimed old row with an equal item (removed from
     *  {@code old} as it is claimed), or null where the item is new. */
    private static Row[] matchRows(List<Row> old, List<?> items) {
        Row[] matched = new Row[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            for (Iterator<Row> candidates = old.iterator(); candidates.hasNext();) {
                Row candidate = candidates.next();
                if (Objects.equals(candidate.item(), item)) {
                    matched[i] = candidate;
                    candidates.remove();
                    break;
                }
            }
        }
        return matched;
    }

    /** Builds one row in place — {@code appendChildren} appends, so walking the new items in order
     *  keeps the container's child order — and records what it appended by child-count delta. */
    private static Row buildRow(ViewBinder binder, ViewGroup container, McsxElement element,
                                Orientation orientation, String as, Object item) {
        List<Effect> effects = new ArrayList<>();
        Scope rowScope = Scope.item(as, item, binder.buildContext().scope());
        BuildContext rowContext = binder.buildContext().withEffects(effects).withScope(rowScope);
        int before = container.getChildCount();
        try {
            appendChildren(binder.inContext(rowContext), container, element, orientation);
        } catch (RuntimeException | Error failure) {
            disposeAll(effects);
            while (container.getChildCount() > before) {
                container.removeViewAt(container.getChildCount() - 1);
            }
            throw failure;
        }
        List<View> views = new ArrayList<>(container.getChildCount() - before);
        for (int child = before; child < container.getChildCount(); child++) {
            views.add(container.getChildAt(child));
        }
        return new Row(item, effects, views);
    }

    private static void disposeRows(List<Row> rows) {
        for (Row row : rows) {
            disposeAll(row.effects());
        }
        rows.clear();
    }

    static ElementView buildComponent(ViewBinder binder, McsxElement element) {
        BuildContext context = binder.buildContext();
        String source = context.imports().get(element.tag());
        McsxDocument component;
        try {
            component = binder.resolveDocument(source);
        } catch (RuntimeException e) {
            throw new McsxBindException("cannot resolve component '" + element.tag() + "' ("
                    + source + "): " + e.getMessage(), element.line(), element.column());
        }
        if (component == null) {
            throw new McsxBindException("cannot resolve component '" + element.tag() + "' ("
                    + source + ")", element.line(), element.column());
        }

        Scope params = null;
        ElementResolver elements = binder.elements();
        for (McsxAttribute attribute : element.attributes()) {
            Object value = attribute.binding()
                    ? elements.propValue(attribute.value(), context.scope(), element)
                    : attribute.value();
            params = Scope.of(attribute.name(), value, params);
        }
        SlotContent callerSlot = new SlotContent(
                element.children(), context.scope(), context.imports(), element);
        BuildContext componentContext = new BuildContext(
                params, component.imports(), callerSlot, context.effects(), context.inherited());
        ViewBinder componentBinder = binder.inContext(componentContext);

        // A component presents its own root style. Nested component metadata must not replace it.
        Supplier<StyleSpec> layoutStyle = () -> componentBinder.resolveStyle(component.root());
        View view = componentBinder.buildElement(component.root()).view();
        return new ElementView(view, layoutStyle);
    }

    static View buildSlot(ViewBinder binder, McsxElement element) {
        FlexLayout container = new FlexLayout(binder.context());
        container.setOrientation(Orientation.COLUMN);
        expandSlot(binder, container, Orientation.COLUMN);
        return container;
    }

    static View buildOverlay(ViewBinder binder, McsxElement element) {
        BuildContext context = binder.buildContext();
        ElementResolver elements = binder.elements();
        McsxAttribute openAttr = requireBinding(element, "open", "<overlay> requires open={…}");
        Supplier<Object> open = binder.resolveBinding(openAttr.value(), element);
        String modalValue = elements.stringAttribute(element, "modal", context.scope());
        if (modalValue != null && !modalValue.equals("true") && !modalValue.equals("false")) {
            throw new McsxBindException("modal must be 'true' or 'false'",
                    element.line(), element.column());
        }
        boolean modal = Boolean.parseBoolean(modalValue);
        String placement = elements.stringAttribute(element, "placement", context.scope());
        if (placement != null && !Set.of("center", "top", "bottom", "start", "end",
                "top-end", "bottom-end").contains(placement)) {
            throw new McsxBindException("unknown overlay placement '" + placement + "'",
                    element.line(), element.column());
        }
        String anchorId = elements.stringAttribute(element, "anchor", context.scope());
        McsxAttribute closeAttr = attributeNamed(element, "onClose");
        Runnable onClose = closeAttr == null
                ? elements.dismissWriter(openAttr, context.scope(), element)
                : elements.handlerOrNull(closeAttr.value(), context.scope());
        if (closeAttr != null && onClose == null) {
            throw new McsxBindException("no close handler '" + closeAttr.value() + "'",
                    element.line(), element.column());
        }

        View placeholder = new View(binder.context());
        placeholder.setVisibility(View.GONE);
        OverlayHost host = binder.overlays();
        List<Effect> body = new ArrayList<>();
        ViewBinder bodyBinder = binder.inContext(context.withEffects(body));
        Object layerKey = new Object();

        Effect gate = Effect.of(() -> {
            // open= is the gate's ONLY dependency. The nested restyle effect below pushes itself
            // ABOVE the untracked frame, so it still tracks its own reads and the panel restyles live.
            boolean show = Boolean.TRUE.equals(open.get());
            Reactive.untracked(() -> {
                disposeAll(body);
                host.close(layerKey);
                if (!show) {
                    return;
                }
                FlexLayout panel = new FlexLayout(binder.context());
                bodyBinder.addEffect(Effect.of(() -> {
                    StyleSpec live = bodyBinder.resolveStyle(element);
                    ViewStyles.applyContainer(panel, live, Orientation.COLUMN);
                    ViewStyles.applyBox(panel, live, true);
                    // The params live in the same effect as the box: a reactive class flipping
                    // w-96 <-> w-64 must resize the open panel, not just restyle it. Snapshotting
                    // them outside froze the panel's size for as long as it stayed open.
                    FrameLayout.LayoutParams params = ViewStyles.overlayLayoutParams(live, placement);
                    View anchor = anchorId == null ? null : binder.anchor(anchorId);
                    if (anchor != null && anchor.isAttachedToWindow()) {
                        host.anchorBelow(params, anchor);
                    }
                    panel.setLayoutParams(params);
                }));
                appendChildren(bodyBinder, panel, element, panel.orientation());
                host.open(layerKey, panel,
                        (FrameLayout.LayoutParams) panel.getLayoutParams(), modal, onClose);
            });
        }, () -> {
            disposeAll(body);
            host.close(layerKey);
        });
        binder.addEffect(gate);
        return placeholder;
    }

    /** The flow container an {@code <if>}/{@code <for>} renders into: styled like a {@code <div>}, but
     *  it wires no handler of its own, so its clickable flag is the spec's alone. */
    private static FlexLayout styledFlowContainer(ViewBinder binder, McsxElement element) {
        FlexLayout container = new FlexLayout(binder.context());
        binder.addEffect(Effect.of(() -> {
            StyleSpec spec = binder.resolveStyle(element);
            ViewStyles.applyContainer(container, spec, Orientation.COLUMN);
            ViewStyles.applyBox(container, spec, true);
            ViewStyles.applyStateFlags(container, spec, false);
        }));
        return container;
    }

    private static void appendChild(ViewBinder binder, ViewGroup parent, McsxContent child,
                                    McsxElement owner, Orientation orientation) {
        if (child instanceof McsxText run) {
            View text = binder.buildBareText(run, owner);
            // A bare run carries no class of its own, so its layout params can never change.
            text.setLayoutParams(ViewStyles.layoutParams(NO_STYLE, orientation));
            parent.addView(text);
            return;
        }
        if (!(child instanceof McsxElement childElement) || isVariantConfig(childElement)) {
            return;
        }
        if (childElement.tag().equals("slot")) {
            expandSlot(binder, parent, orientation);
            return;
        }
        ElementView built = binder.buildElement(childElement);
        bindLayoutParams(binder, built, childElement, orientation);
        parent.addView(built.view());
    }

    private static void expandSlot(ViewBinder binder, ViewGroup parent, Orientation orientation) {
        BuildContext context = binder.buildContext();
        if (context.slot() == null) {
            return;
        }
        SlotContent slot = context.slot();
        BuildContext slotContext = new BuildContext(
                slot.callerScope(), slot.callerImports(), null, context.effects(), context.inherited());
        ViewBinder slotBinder = binder.inContext(slotContext);
        for (McsxContent child : slot.children()) {
            appendChild(slotBinder, parent, child, slot.callSite(), orientation);
        }
    }

    /**
     * The child's layout params, re-derived whenever an input changes. Both inputs are live: the
     * element's {@code class} — a reactive one may flip {@code grow}, {@code w-*}, {@code self-*} —
     * and a bound {@code w=}/{@code h=}, which layers on top of the class-declared size. They share
     * one effect and one params instance on purpose: two effects each publishing their own instance
     * would clobber the other's writes, and neither would re-run to restore them. Publishing a fresh
     * instance per run is also what stops a dropped utility leaving stale layout state behind.
     *
     * <p>Most children have neither input: a literal {@code class} and no bound size resolve the same
     * params forever, so their effect records no dependency and can never re-run. Registering it anyway
     * would retain two sets and a dispose per node — thousands of them on one {@code <for>} tick — for
     * a callback that will never fire, so the effect is dropped once it proves it tracked nothing.
     * Source-counting rather than inspecting the markup because the style a child presents to its
     * parent may be a <em>component root's</em>, whose reactivity the call site cannot see.
     */
    private static void bindLayoutParams(ViewBinder binder, ElementView built, McsxElement element,
                                         Orientation orientation) {
        View view = built.view();
        Supplier<StyleSpec> style = built.layoutStyle();
        McsxAttribute width = bindingAttribute(element, "w");
        McsxAttribute height = bindingAttribute(element, "h");
        Supplier<Object> boundWidth = width == null
                ? null : binder.resolveBinding(width.value(), element);
        Supplier<Object> boundHeight = height == null
                ? null : binder.resolveBinding(height.value(), element);
        Effect layout = Effect.of(() -> {
            FlexLayout.LayoutParams params = ViewStyles.layoutParams(style.get(), orientation);
            if (boundWidth != null) {
                applyBoundSize(params, true, boundWidth.get(), element);
            }
            if (boundHeight != null) {
                applyBoundSize(params, false, boundHeight.get(), element);
            }
            // setLayoutParams requests a layout pass of its own.
            view.setLayoutParams(params);
        });
        if (layout.hasSources()) {
            binder.addEffect(layout);
        }
    }

    private static void applyBoundSize(FlexLayout.LayoutParams params, boolean horizontal,
                                       Object value, McsxElement element) {
        if (!(value instanceof Number)) {
            throw new McsxBindException("bound size must resolve to a number",
                    element.line(), element.column());
        }
        ViewStyles.applySize(params, horizontal, value);
    }

    private static void disposeAll(List<Effect> effects) {
        for (Effect effect : effects) {
            effect.dispose();
        }
        effects.clear();
    }
}
