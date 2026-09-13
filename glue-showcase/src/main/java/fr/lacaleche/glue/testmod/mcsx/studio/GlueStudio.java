package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.Row;
import fr.lacaleche.glue.mcsx.client.dock.DockContent;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.reactive.Values;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * The showcase's dockspace demo: a lighting workbench docked over the running world.
 *
 * <p>Every pane drives something the showcase already owns &mdash; Lumos demo lights, the post-effect
 * registries and the demo blocks &mdash; so the workspace is edited against live world state rather
 * than a mock document. The workspace itself demonstrates the dock features: a non-closable native
 * pane, trailing header tools, tab groups, splits, floating windows, maximize, resource theme and
 * stylesheet, resource default layout and per-user persistence.</p>
 *
 * <p>One instance opens one workspace; create a new one after the workspace closes.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlueStudio {

    public static final String TAG_VIEWPORT = "mcsx.studio.viewport";
    public static final String TAG_VIEWPORT_RELEASE = "mcsx.studio.viewport-release";
    public static final String TAG_RADAR = "mcsx.studio.radar";
    public static final String TAG_LIGHT_LIST = "mcsx.studio.light-list";
    public static final String TAG_CONSOLE = "mcsx.studio.console";
    public static final String TAG_STATUS = "mcsx.studio.status";
    public static final String TAG_SPAWN_POINT = "mcsx.studio.spawn-point";
    public static final String TAG_STRESS_RING = "mcsx.studio.stress-ring";
    public static final String TAG_RED = "mcsx.studio.red";
    public static final String TAG_GREEN = "mcsx.studio.green";
    public static final String TAG_BLUE = "mcsx.studio.blue";
    public static final String TAG_INTENSITY = "mcsx.studio.intensity";
    public static final String TAG_RANGE = "mcsx.studio.range";
    public static final String TAG_SHADOW = "mcsx.studio.shadow";
    public static final String TAG_APPLY = "mcsx.studio.apply";
    public static final String TAG_DELETE = "mcsx.studio.delete";
    public static final String TAG_EFFECT_PREFIX = "mcsx.studio.effect.";
    public static final String TAG_EMITTERS_CONTENT = "mcsx.studio.emitters-content";
    public static final String TAG_PALETTE_CONTENT = "mcsx.studio.palette-content";

    private static final String CONSOLE_PANE = "console";
    private static final String KEY_VIEWPORT_CAPTURED = "mcsx.studio.viewport.hint_release";
    private static final String KEY_VIEWPORT_RELEASED = "mcsx.studio.viewport.hint_capture";
    private static final int[][] COLOR_PRESETS = {
            {255, 236, 202}, {202, 226, 255}, {255, 176, 84}, {255, 96, 74},
            {124, 236, 148}, {98, 152, 255}, {226, 122, 255}
    };
    private static final int PRESET_SIZE = 20;
    private static final int PREVIEW_SIZE = 34;
    private static final int ZOOM_STEP = 16;

    private final StudioSession session = StudioSession.INSTANCE;
    private final Value<Theme> theme = Themes.resource(TestmodClient.id("studio"));
    private final Signal<Integer> openPanes = Signal.of(0);
    private final Dockspace dockspace;

    public GlueStudio(boolean persistent) {
        this.dockspace = Dockspace.builder(TestmodClient.id("glue-studio"))
                .pane(this.viewportPane())
                .pane(this.radarPane())
                .pane(this.lightsPane())
                .pane(this.pane("inspector", "INS", this::inspectorContent).build())
                .pane(this.pane("emitters", "EMT", this::emittersContent).build())
                .pane(this.pane("effects", "FX", this::effectsContent).build())
                .pane(this.pane("palette", "BLK", this::paletteContent).build())
                .pane(this.pane(CONSOLE_PANE, "LOG", this::consoleContent).build())
                .defaultLayout(TestmodClient.id("glue-studio"))
                .persistence(persistent)
                .theme(this.theme)
                .stylesheet(Stylesheets.resource(TestmodClient.id("glue-studio")))
                .header(DockContent.ui(this::header))
                .footer(DockContent.ui(this::footer))
                .onOpenPanesChanged(panes -> this.openPanes.set(panes.size()))
                .onLayoutChanged(ignored -> this.session.status("Workspace layout updated"))
                .onMount(() -> this.session.mount(this))
                // The world must leave the pane's rectangle on the same frame the workspace unmounts;
                // the pane's own detach clears the same claims later as the idempotent fallback.
                .onUnmount(() -> this.session.closeWorldClaims(this))
                .onClose(() -> this.session.unmount(this))
                .build();
    }

    public Dockspace dockspace() {
        return this.dockspace;
    }

    public void open() {
        this.dockspace.open();
    }

    /** A pane titled and keyed under the studio's translation root, with a literal icon. */
    private DockPane.Builder pane(String id, String icon, Function<? super Ui, ? extends View> content) {
        return DockPane.builderTranslatable(id, "mcsx.studio.pane." + id, content)
                .icon(Component.literal(icon));
    }

    /**
     * The viewport: a transparent, non-closable pane. The dockspace is hosted without a background,
     * so the running world shows through the hole this pane leaves, and clicking it hands the player
     * to {@link ViewportController}.
     */
    private DockPane viewportPane() {
        return this.pane("viewport", "VIEW", ui -> Ui.tagged(
                        new ViewportPaneView(ui.context(), this.theme, this.session, this), TAG_VIEWPORT))
                .closable(false)
                .transparent(true)
                .trailingHeaderUi(ui -> this.paneTools(ui,
                        this.localized(this.session.viewport().captured(), GlueStudio::viewportHint),
                        ui.secondaryButton("mcsx.studio.viewport.release", this.session.viewport()::release)
                                .enabled(this.session.viewport().captured())
                                .tag(TAG_VIEWPORT_RELEASE)
                                .classes("studio-tool")))
                .build();
    }

    private DockPane radarPane() {
        return this.pane("radar", "RAD", ui -> Ui.tagged(new LightRadarView(
                        ui.context(),
                        this.theme,
                        this.session.lights(),
                        this.session.selected(),
                        this.session.pose(),
                        this.session.radarSpan(),
                        this.session::select
                ), TAG_RADAR))
                .closable(false)
                .trailingHeaderUi(ui -> this.paneTools(ui,
                        this.localized(this.session.radarSpan(),
                                span -> Component.translatable("mcsx.studio.radar.span", span)),
                        ui.literalSecondaryButton("-", () -> this.session.zoom(ZOOM_STEP))
                                .classes("studio-step"),
                        ui.literalSecondaryButton("+", () -> this.session.zoom(-ZOOM_STEP))
                                .classes("studio-step")))
                .build();
    }

    private DockPane lightsPane() {
        return this.pane("lights", "LGT", this::lightsContent)
                .trailingHeaderUi(ui -> this.paneTools(ui,
                        this.localized(this.session.lights(),
                                lights -> Component.translatable("mcsx.studio.lights.count", lights.size())),
                        ui.dangerButton("mcsx.studio.lights.clear", this.session::clearVisualLights)
                                .classes("studio-tool")))
                .build();
    }

    /** The trailing tools every pane header shares: one measurement, then its buttons. */
    private Row paneTools(Ui ui, Value<String> span, View... tools) {
        Row row = ui.row(ui.text(span).classes("studio-span"));
        row.add(tools);
        return row.classes("studio-pane-tools");
    }

    private Column emptyState(Ui ui, String messageKey, Value<Boolean> empty) {
        return ui.column(ui.copy(messageKey)).visible(empty).classes("studio-empty");
    }

    private View header(Ui ui) {
        return ui.row(
                ui.column(
                        ui.heading("mcsx.studio.title"),
                        ui.copy("mcsx.studio.subtitle")
                ).classes("studio-brand"),
                ui.button("mcsx.studio.action.fly", this.session.viewport()::capture)
                        .enabled(Values.not(this.session.viewport().captured())),
                ui.secondaryButton("mcsx.studio.action.console",
                        () -> this.dockspace.togglePane(CONSOLE_PANE)),
                ui.secondaryButton("mcsx.studio.action.save", this.dockspace::saveLayout),
                ui.quietButton("mcsx.studio.action.reset", this.dockspace::resetLayout)
        ).classes("studio-header");
    }

    private View footer(Ui ui) {
        return ui.row(
                ui.text(this.session.status()).tag(TAG_STATUS).classes("studio-status"),
                ui.text(this.localized(this.session.lights(),
                        lights -> Component.translatable("mcsx.studio.footer.visual", lights.size()))),
                ui.text(this.localized(this.session.worldLights(),
                        count -> Component.translatable("mcsx.studio.footer.world", count))),
                ui.text(this.session.pose().map(StudioSession.Pose::text)),
                ui.text(this.localized(this.openPanes,
                        count -> Component.translatable("mcsx.studio.footer.panes", count)))
        ).classes("studio-footer");
    }

    private View lightsContent(Ui ui) {
        return this.scrollingPane(ui,
                ui.copy("mcsx.studio.lights.hint").classes("studio-hint"),
                this.emptyState(ui, "mcsx.studio.lights.empty", this.session.lights().map(List::isEmpty)),
                ui.boundColumn(this.session.lights(), StudioLight::light, entry -> this.lightRow(ui, entry))
                        .tag(TAG_LIGHT_LIST)
                        .classes("studio-list")
        );
    }

    /**
     * One light row. The key is the {@link Light} instance, so it stays fixed for the row's life
     * while every measurement the row shows follows its bound entry.
     */
    private View lightRow(Ui ui, Value<StudioLight> entry) {
        Light light = entry.get().light();
        Column row = ui.column(
                ui.row(
                        new SwatchView(ui.context(), entry.map(StudioLight::color), PRESET_SIZE),
                        ui.text(entry.map(StudioLight::label)).classes("studio-row-title"),
                        ui.text(entry.map(StudioLight::distanceText)).classes("studio-row-distance")
                ).classes("studio-row-head"),
                ui.text(entry.map(StudioLight::detail)).classes("studio-row-detail")
        ).classes("studio-row").state("selected", this.session.selected().map(picked -> picked == light));
        row.setOnClickListener(ignored -> this.session.select(light));
        return row;
    }

    private View inspectorContent(Ui ui) {
        Value<Boolean> hasSelection = this.session.selected().map(Objects::nonNull);
        Value<Boolean> valid = this.session.draftValid();
        return this.scrollingPane(ui,
                this.emptyState(ui, "mcsx.studio.inspector.empty", Values.not(hasSelection)),
                ui.column(
                        ui.row(
                                new SwatchView(ui.context(), this.session.draftColor(), PREVIEW_SIZE),
                                ui.column(
                                        ui.text(this.session.selected().map(GlueStudio::titleOf))
                                                .classes("studio-selected-title"),
                                        ui.text(this.session.selected().map(GlueStudio::positionOf))
                                                .classes("studio-selected-position")
                                ).classes("studio-row-text")
                        ).classes("studio-selected"),
                        ui.section("mcsx.studio.inspector.colour",
                                ui.row(this.presets(ui)).classes("studio-swatches"),
                                ui.row(
                                        this.number(ui, "R", this.session.draftRed(), TAG_RED),
                                        this.number(ui, "G", this.session.draftGreen(), TAG_GREEN),
                                        this.number(ui, "B", this.session.draftBlue(), TAG_BLUE)
                                ).classes("studio-numbers")
                        ),
                        ui.section("mcsx.studio.inspector.shape",
                                this.stepper(ui, "mcsx.studio.inspector.power", TAG_INTENSITY,
                                        this.session.draftIntensity(),
                                        () -> this.session.stepIntensity(-0.5f),
                                        () -> this.session.stepIntensity(0.5f)),
                                this.stepper(ui, "mcsx.studio.inspector.range", TAG_RANGE,
                                        this.session.draftRange(),
                                        () -> this.session.stepRange(-2f),
                                        () -> this.session.stepRange(2f)),
                                ui.checkbox("mcsx.studio.inspector.shadow")
                                        .checked(this.session.draftShadow())
                                        .tag(TAG_SHADOW)
                        ),
                        ui.copy("mcsx.studio.inspector.invalid")
                                .visible(Values.not(valid))
                                .classes("studio-validation"),
                        ui.actions(
                                ui.button("mcsx.studio.inspector.apply", this.session::applyDraft)
                                        .enabled(valid)
                                        .tag(TAG_APPLY),
                                ui.secondaryButton("mcsx.studio.inspector.revert", this.session::revertDraft)
                        ),
                        ui.actions(
                                ui.secondaryButton("mcsx.studio.inspector.move",
                                        this.session::moveSelectedToPlayer),
                                ui.dangerButton("mcsx.studio.inspector.delete", this.session::deleteSelected)
                                        .tag(TAG_DELETE)
                        )
                ).visible(hasSelection).classes("studio-form")
        );
    }

    private List<View> presets(Ui ui) {
        List<View> swatches = new ArrayList<>(COLOR_PRESETS.length);
        for (int[] preset : COLOR_PRESETS) {
            SwatchView swatch = new SwatchView(
                    ui.context(),
                    Values.constant(0xff000000 | preset[0] << 16 | preset[1] << 8 | preset[2]),
                    PRESET_SIZE
            );
            swatch.setOnClickListener(ignored -> this.session.selectPreset(preset[0], preset[1], preset[2]));
            swatches.add(swatch);
        }
        return swatches;
    }

    private Column number(Ui ui, String label, Signal<String> value, String tag) {
        return ui.column(
                ui.literalCopy(label).classes("studio-field-label"),
                ui.field(Component.empty()).text(value).tag(tag)
        ).classes("studio-number");
    }

    private Row stepper(Ui ui, String labelKey, String tag, Signal<String> value,
                        Runnable decrease, Runnable increase) {
        return ui.row(
                ui.copy(labelKey).classes("studio-stepper-label"),
                ui.literalSecondaryButton("-", decrease).classes("studio-step"),
                ui.field(Component.empty()).text(value).tag(tag).classes("studio-step-field"),
                ui.literalSecondaryButton("+", increase).classes("studio-step")
        ).classes("studio-stepper");
    }

    private View emittersContent(Ui ui) {
        return Ui.tagged(this.scrollingPane(ui,
                ui.section("mcsx.studio.emitters.visual",
                        ui.copy("mcsx.studio.emitters.visual_hint").classes("studio-hint"),
                        ui.actions(
                                ui.button("mcsx.studio.emitters.point", this.session::spawnPoint)
                                        .tag(TAG_SPAWN_POINT),
                                ui.secondaryButton("mcsx.studio.emitters.spot", this.session::spawnSpot)
                        ),
                        ui.actions(
                                ui.secondaryButton("mcsx.studio.emitters.aimed",
                                        this.session::spawnAtAimedBlock)
                        ),
                        ui.actions(
                                ui.secondaryButton("mcsx.studio.emitters.ring", this.session::toggleStressRing)
                                        .pressed(this.session.stressRing())
                                        .tag(TAG_STRESS_RING),
                                ui.secondaryButton("mcsx.studio.emitters.flashlight",
                                                this.session::toggleFlashlight)
                                        .pressed(this.session.flashlight())
                        )
                ),
                ui.section("mcsx.studio.emitters.world",
                        ui.copy("mcsx.studio.emitters.world_hint").classes("studio-hint"),
                        ui.text(this.localized(this.session.worldLights(),
                                        count -> Component.translatable("mcsx.studio.emitters.world_count", count)))
                                .classes("studio-hint"),
                        ui.actions(
                                ui.button("mcsx.studio.emitters.place", this.session::placeWorldLight),
                                ui.dangerButton("mcsx.studio.emitters.clear_world",
                                        this.session::clearWorldLights)
                        )
                )
        ), TAG_EMITTERS_CONTENT);
    }

    private View effectsContent(Ui ui) {
        return this.scrollingPane(ui,
                ui.section("mcsx.studio.effects.timed", this.effectButtons(ui, false)),
                ui.section("mcsx.studio.effects.toggles", this.effectButtons(ui, true)),
                ui.copy("mcsx.studio.effects.hint").classes("studio-hint")
        );
    }

    private List<? extends View> effectButtons(Ui ui, boolean toggle) {
        return this.session.effects().stream()
                .filter(effect -> effect.toggle() == toggle)
                .map(effect -> ui.secondaryButton(effect.labelKey(), () -> this.session.trigger(effect))
                        .pressed(this.session.activeEffects().map(active -> active.contains(effect.id())))
                        .tag(TAG_EFFECT_PREFIX + effect.id())
                        .classes("studio-effect"))
                .toList();
    }

    private View paletteContent(Ui ui) {
        return Ui.tagged(this.scrollingPane(ui,
                ui.copy("mcsx.studio.palette.hint").classes("studio-hint"),
                ui.column(this.paletteRows(ui)).classes("studio-list")
        ), TAG_PALETTE_CONTENT);
    }

    private List<? extends View> paletteRows(Ui ui) {
        return StudioBlock.all().stream()
                .map(block -> ui.column(
                        ui.row(
                                ui.text(block.block().getName()).classes("studio-row-title"),
                                ui.secondaryButton("mcsx.studio.palette.give", () -> this.session.give(block))
                                        .classes("studio-tool")
                        ).classes("studio-row-head"),
                        ui.copy(block.detailKey()).classes("studio-row-detail")
                ).classes("studio-row"))
                .toList();
    }

    private View consoleContent(Ui ui) {
        return this.scrollingPane(ui,
                this.emptyState(ui, "mcsx.studio.console.empty", this.session.log().map(List::isEmpty)),
                ui.keyedColumn(this.session.log(), StudioSession.LogEntry::id,
                                entry -> ui.column(ui.literalText(entry.message()))
                                        .classes("studio-log-entry"))
                        .tag(TAG_CONSOLE)
                        .classes("studio-list"),
                ui.actions(ui.dangerButton("mcsx.studio.console.clear", this.session::clearLog))
        );
    }

    private ScrollView scrollingPane(Ui ui, View... children) {
        Column content = ui.column(children).classes("studio-pane");
        content.theme(this.theme);
        content.stylesheet(Stylesheets.resource(TestmodClient.id("glue-studio")));
        ScrollView scroll = new ScrollView(ui.context());
        scroll.setFillViewport(true);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private static Component viewportHint(boolean captured) {
        return Component.translatable(captured ? KEY_VIEWPORT_CAPTURED : KEY_VIEWPORT_RELEASED);
    }

    /**
     * Binds text that a plain translation key cannot carry: a count, a span or a state folded into a
     * translated sentence. The sentence stays a {@link Component} until the bind, because
     * {@link Ui} takes reactive text as a {@code CharSequence} and only a {@code Component} passed
     * whole re-translates itself; the studio's theme handle is folded in so the resolved string is
     * recomputed on every resource reload &mdash; switching language performs one, and without that
     * source a mounted label would keep the old language until its own value happened to change.
     */
    private <T> Value<String> localized(Value<T> source, Function<? super T, ? extends Component> text) {
        return Value.combine(this.theme, source, (ignored, value) -> text.apply(value).getString());
    }

    private static String titleOf(Light light) {
        if (light == null) return "";

        return switch (light.type) {
            case POINT -> "Point emitter";
            case SPOT -> "Spot emitter";
            case GOBO -> "Gobo emitter";
        };
    }

    private static String positionOf(Light light) {
        if (light == null) return "";

        return String.format(Locale.ROOT, "%.1f  %.1f  %.1f", light.x, light.y, light.z);
    }
}
