package fr.lacaleche.glue.mcsx.client.style;

import fr.lacaleche.glue.mcsx.client.style.internal.StyleDeclaration;
import fr.lacaleche.glue.mcsx.client.style.internal.StyleRule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class Stylesheet {

    private static final int BASE_ORIGIN_OFFSET = 1_000_000;
    private static final Stylesheet EMPTY = new Stylesheet(List.of());

    private final List<StyleRule> rules;
    private final boolean includesDefaults;

    Stylesheet(List<StyleRule> rules) {
        this(rules, false);
    }

    private Stylesheet(List<StyleRule> rules, boolean includesDefaults) {
        this.rules = List.copyOf(rules);
        this.includesDefaults = includesDefaults;
    }

    public static Stylesheet empty() {
        return EMPTY;
    }

    public List<StyleRule> internalRules() {
        return this.rules;
    }

    boolean includesDefaults() {
        return this.includesDefaults;
    }

    static Stylesheet overlay(Stylesheet base, Stylesheet override) {
        List<StyleRule> merged = new ArrayList<>(base.rules.size() + override.rules.size());
        int ruleOrder = 0;
        int declarationOrder = 0;
        List<Stylesheet> origins = List.of(base, override);
        for (int origin = 0; origin < origins.size(); origin++) {
            Stylesheet stylesheet = origins.get(origin);
            boolean baseOrigin = origin == 0;
            for (StyleRule rule : stylesheet.rules) {
                List<StyleDeclaration> declarations = new ArrayList<>(rule.declarations().size());
                for (StyleDeclaration declaration : rule.declarations()) {
                    declarations.add(new StyleDeclaration(
                            declaration.property(),
                            declaration.value(),
                            declaration.line(),
                            declaration.column(),
                            declarationOrder++
                    ));
                }
                merged.add(new StyleRule(
                        rule.selector(),
                        List.copyOf(declarations),
                        rule.specificity() - (baseOrigin ? BASE_ORIGIN_OFFSET : 0),
                        ruleOrder++
                ));
            }
        }
        return new Stylesheet(merged, true);
    }
}
