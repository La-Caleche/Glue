package fr.lacaleche.glue.mcsx.core.lint;

import fr.lacaleche.glue.mcsx.core.mcsx.McsxAttribute;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxText;
import fr.lacaleche.glue.mcsx.core.mcsx.Tags;
import fr.lacaleche.glue.mcsx.core.style.TailwindException;
import fr.lacaleche.glue.mcsx.core.style.TailwindParser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Statically checks a {@code .mcsx} document against the controller it will be bound to. Everything
 * the binder resolves by name — {@code class} utilities, {@code {ref}} bindings, {@code {{text}}}
 * interpolations, {@code onClick} handlers — is data, so it can be verified without a window.
 *
 * <p>This exists because {@code view.*} needs a live ModernUI {@code Context} and therefore has no
 * unit tests: a screen that names a handler the controller lacks, or a class the parser rejects,
 * used to surface only as a stack trace in-game. The linter is deliberately conservative — it
 * reports what it can prove wrong, and stays silent about anything it cannot resolve statically.
 */
public final class McsxLinter {

    private final Class<?> controller;
    private final Set<String> nativeTags;
    private final List<String> problems = new ArrayList<>();
    private final Deque<String> scope = new ArrayDeque<>();

    private McsxLinter(Class<?> controller, Set<String> nativeTags) {
        this.controller = controller;
        this.nativeTags = nativeTags;
    }

    /**
     * Lints one document. {@code controller} may be null for a component file, which has no
     * controller of its own — its refs resolve against the caller's props at bind time, so only its
     * classes can be checked.
     */
    public static List<String> lint(McsxDocument document, Class<?> controller) {
        return lint(document, controller, Set.of());
    }

    /**
     * @param nativeTags the components registered in the {@code ComponentRegistry} this document will
     *     bind against. Without them an unknown tag cannot be told from a native one — which is how
     *     a {@code <checkbox>} survived being deleted and only failed in-game.
     */
    public static List<String> lint(McsxDocument document, Class<?> controller, Set<String> nativeTags) {
        McsxLinter linter = new McsxLinter(controller, nativeTags);
        linter.walk(document.root(), document.imports().keySet());
        return List.copyOf(linter.problems);
    }

    private void walk(McsxElement element, Set<String> components) {
        checkTag(element, components);
        checkClasses(element);

        boolean isComponent = components.contains(element.tag());
        // A component's props become the scope its own file resolves against; the call site's refs
        // are ours to check, but what the component does with them is checked when IT is linted.
        for (McsxAttribute attribute : element.attributes()) {
            checkAttribute(element, attribute, isComponent);
        }
        String loopVar = "for".equals(element.tag()) ? element.attribute("as") : null;
        if (loopVar != null && !loopVar.isBlank()) {
            scope.push(loopVar);
        }
        // <state name="…"> declares a subtree name: visible to the element's DESCENDANTS, not to
        // its own attributes — the runtime hands the state scope to the child context only. Pushed
        // after the attribute loop so the linter draws the same line.
        int stateNames = 0;
        for (McsxContent child : element.children()) {
            if (child instanceof McsxElement state && "state".equals(state.tag())) {
                String name = state.attribute("name");
                if (name != null && !name.isBlank()) {
                    scope.push(name);
                    stateNames++;
                }
            }
        }
        for (McsxContent child : element.children()) {
            if (child instanceof McsxText text) {
                checkText(element, text);
            } else if (child instanceof McsxElement childElement
                    && !Tags.CONFIG.contains(childElement.tag())) {
                walk(childElement, components);
            } else if (child instanceof McsxElement config) {
                checkClasses(config);
                for (McsxContent option : config.children()) {
                    if (option instanceof McsxElement optionElement) {
                        checkClasses(optionElement);
                    }
                }
            }
        }

        for (int popped = 0; popped < stateNames; popped++) {
            scope.pop();
        }
        if (loopVar != null && !loopVar.isBlank()) {
            scope.pop();
        }
    }

    private void checkTag(McsxElement element, Set<String> components) {
        String tag = element.tag();
        if (Tags.KNOWN.contains(tag) || components.contains(tag) || nativeTags.contains(tag)) {
            return;
        }
        report(element, "unknown element <" + tag + "> (not a base tag, an <import>, or a native component)");
    }

    /** Every literal utility must parse. {@code {hole}} segments are resolved at bind time. */
    private void checkClasses(McsxElement element) {
        String classes = element.attribute("class");
        if (classes == null) {
            return;
        }
        String literal = classes.replaceAll("\\{[^}]*}", " ").trim();
        if (literal.isEmpty()) {
            return;
        }
        try {
            TailwindParser.parseStrict(literal);
        } catch (TailwindException e) {
            report(element, e.getMessage());
        }
    }

    private void checkAttribute(McsxElement element, McsxAttribute attribute, boolean isComponent) {
        if (!attribute.binding()) {
            return;
        }
        String ref = attribute.value();
        if (Tags.HANDLER_ATTRIBUTES.contains(attribute.name())) {
            checkHandler(element, ref);
            return;
        }
        if (Tags.LITERAL_ATTRIBUTES.contains(attribute.name())) {
            return;
        }
        checkRef(element, ref, isComponent);
    }

    private void checkText(McsxElement owner, McsxText text) {
        for (McsxText.Part part : text.parts()) {
            if (part.binding()) {
                checkRef(owner, part.value(), false);
            }
        }
    }

    /**
     * The head of a {@code {ref}} must be a loop variable in scope or a controller field. Deeper
     * path segments are not checked: they resolve against a runtime value's type, not the controller.
     */
    private void checkRef(McsxElement element, String ref, boolean componentProp) {
        if (controller == null) {
            return;
        }
        String head = ref.split("\\.")[0];
        if (scope.contains(head) || hasField(head)
                || componentProp && (hasMethod(head, 0) || hasMethod(head, 1))) {
            return;
        }
        report(element, "cannot resolve '" + head + "' on " + controller.getSimpleName()
                + " (no field, and not a <for> variable in scope)");
    }

    /**
     * A handler names a no-arg or one-arg controller method. Inside a component the name may instead
     * be a forwarded prop, which only the call site can supply — so a null controller checks nothing.
     */
    private void checkHandler(McsxElement element, String ref) {
        if (controller == null || scope.contains(ref)) {
            return;
        }
        if (hasMethod(ref, 0) || hasMethod(ref, 1)) {
            return;
        }
        report(element, "no handler '" + ref + "' on " + controller.getSimpleName()
                + " (expected a method taking 0 or 1 argument)");
    }

    private boolean hasField(String name) {
        for (Class<?> c = controller; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMethod(String name, int parameterCount) {
        for (Class<?> c = controller; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private void report(McsxElement element, String message) {
        problems.add("line " + element.line() + ", column " + element.column()
                + " <" + element.tag() + ">: " + message);
    }
}
