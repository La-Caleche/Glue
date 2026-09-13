package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleDeclaration;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleRule;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleValue;
import icyllis.modernui.view.View;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class StyleMetadata {

    private static final Set<String> LAYOUT_PROPERTIES = Set.of(
            "width",
            "max-width",
            "flex-grow",
            "padding",
            "gap",
            "align-items",
            "justify-content"
    );
    private static final Set<String> CONTAINER_LAYOUT_PROPERTIES = Set.of(
            "padding",
            "gap",
            "align-items",
            "justify-content"
    );

    private final View view;
    private final Set<String> classes = new LinkedHashSet<>();
    private final Map<String, StateBinding> states = new LinkedHashMap<>();
    private final Map<String, Winner> computed = new HashMap<>();
    private String part;
    private boolean mounted;
    private StyleScope computedScope;

    StyleMetadata(View view) {
        this.view = view;
    }

    void classes(String... names) {
        Objects.requireNonNull(names, "names");
        boolean changed = false;
        for (String name : names) {
            changed |= this.classes.add(this.requireName(name, "class"));
        }
        if (changed) this.invalidate();
    }

    void part(String name) {
        String next = this.requireName(name, "part");
        if (Objects.equals(this.part, next)) return;

        this.part = next;
        this.invalidate();
    }

    void state(String name, Value<Boolean> value) {
        String requiredName = this.requireName(name, "state");
        StateBinding replacement = new StateBinding(
                Objects.requireNonNull(value, "value")
        );
        StateBinding previous = this.states.put(requiredName, replacement);
        if (previous != null) previous.unmount();
        if (this.mounted) replacement.mount();
        this.invalidate();
    }

    void mount() {
        if (this.mounted) return;

        this.mounted = true;
        try {
            for (StateBinding binding : this.states.values()) {
                binding.mount();
            }
        } catch (RuntimeException exception) {
            this.mounted = false;
            for (StateBinding binding : this.states.values()) {
                try {
                    binding.unmount();
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    void unmount() {
        if (!this.mounted && this.computedScope == null) return;

        boolean wasMounted = this.mounted;
        this.mounted = false;
        RuntimeException failure = null;
        if (wasMounted) {
            for (StateBinding binding : this.states.values()) {
                failure = LifecycleCleanup.attempt(failure, binding::unmount);
            }
        }
        if (this.computedScope != null) {
            failure = LifecycleCleanup.attempt(
                    failure,
                    () -> this.recompute(Stylesheet.empty(), null)
            );
        }
        LifecycleCleanup.finish(failure);
    }

    void clear(StyleScope scope) {
        if (this.computedScope == scope) {
            this.recompute(Stylesheet.empty(), null);
        }
    }

    void recompute(Stylesheet stylesheet, StyleScope scope) {
        Map<String, Winner> next = new HashMap<>();
        for (StyleRule rule : stylesheet.internalRules()) {
            if (!StyleScope.matches(this.view, rule.selector())) continue;

            for (StyleDeclaration declaration : rule.declarations()) {
                Winner candidate = new Winner(
                        declaration.value(),
                        rule.specificity(),
                        declaration.sourceOrder()
                );
                Winner existing = next.get(declaration.property());
                if (existing == null || candidate.outranks(existing)) {
                    next.put(declaration.property(), candidate);
                }
            }
        }

        Set<String> changedProperties = new LinkedHashSet<>(this.computed.keySet());
        changedProperties.addAll(next.keySet());
        for (String property : changedProperties) {
            Winner previous = this.computed.get(property);
            Winner replacement = next.get(property);
            if (Objects.equals(previous, replacement)) continue;

            StyleComponents.apply(
                    this.view,
                    property,
                    replacement == null ? null : replacement.value
            );
            if (LAYOUT_PROPERTIES.contains(property)) {
                if (this.view instanceof TaffyLayout layout
                        && CONTAINER_LAYOUT_PROPERTIES.contains(property)) {
                    layout.markStylesheetLayoutDirty();
                } else {
                    this.view.requestLayout();
                }
            }
        }
        this.computed.clear();
        this.computed.putAll(next);
        this.computedScope = scope;
    }

    boolean hasClass(String name) {
        return this.classes.contains(name);
    }

    boolean hasPart(String name) {
        return Objects.equals(this.part, name);
    }

    boolean hasState(String name) {
        return switch (name) {
            case "hover" -> this.view.isHovered();
            case "focus" -> this.view.isFocused();
            case "pressed" -> this.view instanceof Button button
                    ? button.isVisuallyPressed()
                    : this.view.isPressed();
            case "disabled" -> !this.view.isEnabled();
            case "checked" -> this.view instanceof Checkbox checkbox && checkbox.isChecked();
            default -> {
                StateBinding binding = this.states.get(name);
                yield binding != null && binding.current;
            }
        };
    }

    boolean hasType(String name) {
        return this.view.getClass().getSimpleName().equals(name);
    }

    StyleValue computedValue(String property) {
        Winner winner = this.computed.get(property);
        return winner == null ? null : winner.value;
    }

    void interactionChanged() {
        this.invalidate();
    }

    private void invalidate() {
        StyleScope scope = TaffyLayout.findStyleScope(this.view);
        if (scope != null) {
            scope.recomputeSubtree(this.view);
        }
    }

    private String requireName(String name, String role) {
        String required = Objects.requireNonNull(name, role).trim();
        if (required.isEmpty()) throw new IllegalArgumentException(role + " name cannot be blank");
        for (int index = 0; index < required.length(); index++) {
            char character = required.charAt(index);
            if (!Character.isLetterOrDigit(character)
                    && character != '-'
                    && character != '_') {
                throw new IllegalArgumentException("Invalid " + role + " name '" + required + "'");
            }
        }
        return required;
    }

    private record Winner(StyleValue value, int specificity, int sourceOrder) {

        private boolean outranks(Winner other) {
            return this.specificity > other.specificity
                    || this.specificity == other.specificity
                    && this.sourceOrder > other.sourceOrder;
        }
    }

    private final class StateBinding {

        private final Value<Boolean> value;
        private boolean current;
        private Subscription subscription;

        private StateBinding(Value<Boolean> value) {
            this.value = value;
            this.current = Boolean.TRUE.equals(value.get());
        }

        private void mount() {
            if (this.subscription != null) return;

            Subscription acquired = this.value.subscribe(this::changed);
            try {
                this.changed(this.value.get());
                this.subscription = acquired;
            } catch (RuntimeException exception) {
                try {
                    acquired.close();
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        }

        private void unmount() {
            Subscription acquired = this.subscription;
            if (acquired == null) return;

            this.subscription = null;
            acquired.close();
        }

        private void changed(Boolean next) {
            boolean nextValue = Boolean.TRUE.equals(next);
            if (this.current == nextValue) return;

            this.current = nextValue;
            StyleMetadata.this.invalidate();
        }
    }
}
