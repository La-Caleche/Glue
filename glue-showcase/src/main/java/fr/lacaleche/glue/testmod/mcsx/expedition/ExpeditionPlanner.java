package fr.lacaleche.glue.testmod.mcsx.expedition;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.reactive.Values;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.mcsx.ShowcaseUiScreen;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;

public final class ExpeditionPlanner extends ShowcaseUiScreen {

    public static final String TAG_ROOT = "mcsx.expedition.planner";
    public static final String TAG_NAME = "mcsx.expedition.name";
    public static final String TAG_TRAVEL_ENABLED = "mcsx.expedition.travel-enabled";
    public static final String TAG_TRAVEL_TARGET = "mcsx.expedition.travel-target";
    public static final String TAG_ELEVATION_ENABLED = "mcsx.expedition.elevation-enabled";
    public static final String TAG_ELEVATION_TARGET = "mcsx.expedition.elevation-target";
    public static final String TAG_SUPPLIES_ENABLED = "mcsx.expedition.supplies-enabled";
    public static final String TAG_SUPPLIES_TARGET = "mcsx.expedition.supplies-target";
    public static final String TAG_START = "mcsx.expedition.start";
    public static final String TAG_ABORT = "mcsx.expedition.abort";
    public static final String TAG_RESET_DRAFT = "mcsx.expedition.reset-draft";
    public static final String TAG_RESET_PROGRESS = "mcsx.expedition.reset-progress";
    public static final String TAG_CLOSE = "mcsx.expedition.close";
    public static final String TAG_PROGRESS = "mcsx.expedition.progress";

    private final ExpeditionSession session = ExpeditionSession.INSTANCE;
    private final Signal<Boolean> validationVisible = Signal.of(false);
    private final Value<Boolean> valid = this.createValid();

    @Override
    protected View create(Ui ui) {
        Value<Boolean> editable = this.session.stageValue().map(stage ->
                stage == ExpeditionSession.Stage.DRAFT || stage == ExpeditionSession.Stage.ABORTED);
        Value<Boolean> active = this.session.stageValue().map(stage -> stage == ExpeditionSession.Stage.ACTIVE);
        Value<Boolean> completed = this.session.stageValue().map(stage -> stage == ExpeditionSession.Stage.COMPLETED);
        Value<Boolean> tracked = this.session.stageValue().map(stage ->
                stage == ExpeditionSession.Stage.ACTIVE || stage == ExpeditionSession.Stage.COMPLETED);

        return ui.screen(
                Signal.of(Themes.mcsx()),
                Stylesheets.resource(TestmodClient.id("expedition-planner")),
                ui.card(
                        ui.heading("mcsx.expedition.title"),
                        ui.copy("mcsx.expedition.description"),
                        this.createConfiguration(ui, editable),
                        this.createProgress(ui, tracked, active, completed),
                        ui.actions(
                                ui.quietButton("mcsx.expedition.close", this::back)
                                        .tag(TAG_CLOSE)
                        ).classes("planner-footer")
                ).classes("planner-card")
        ).tag(TAG_ROOT).classes("expedition-planner");
    }

    private Column createConfiguration(Ui ui, Value<Boolean> visible) {
        return ui.section(
                "mcsx.expedition.configuration",
                ui.field("mcsx.expedition.name_hint")
                        .text(this.session.draftName())
                        .onSubmit(this::start)
                        .tag(TAG_NAME),
                this.createObjective(
                        ui,
                        "mcsx.expedition.travel",
                        "mcsx.expedition.travel_hint",
                        this.session.draftTravelEnabled(),
                        this.session.draftTravelTarget(),
                        TAG_TRAVEL_ENABLED,
                        TAG_TRAVEL_TARGET
                ),
                this.createObjective(
                        ui,
                        "mcsx.expedition.elevation",
                        "mcsx.expedition.elevation_hint",
                        this.session.draftElevationEnabled(),
                        this.session.draftElevationTarget(),
                        TAG_ELEVATION_ENABLED,
                        TAG_ELEVATION_TARGET
                ),
                this.createObjective(
                        ui,
                        "mcsx.expedition.supplies",
                        "mcsx.expedition.supplies_hint",
                        this.session.draftSuppliesEnabled(),
                        this.session.draftSuppliesTarget(),
                        TAG_SUPPLIES_ENABLED,
                        TAG_SUPPLIES_TARGET
                ),
                ui.copy("mcsx.expedition.validation")
                        .visible(Values.all(this.validationVisible, Values.not(this.valid)))
                        .classes("validation-error"),
                ui.actions(
                        ui.button("mcsx.expedition.start", this::start)
                                .enabled(this.valid)
                                .tag(TAG_START),
                        ui.secondaryButton("mcsx.expedition.reset", this::reset)
                                .tag(TAG_RESET_DRAFT)
                )
        ).visible(visible).classes("configuration");
    }

    private Column createObjective(
            Ui ui,
            String label,
            String hint,
            Signal<Boolean> enabled,
            Signal<String> target,
            String enabledTag,
            String targetTag
    ) {
        return ui.column(
                ui.checkbox(label).checked(enabled).tag(enabledTag),
                ui.field(hint)
                        .text(target)
                        .enabled(enabled)
                        .tag(targetTag)
        ).classes("objective-editor");
    }

    private Column createProgress(
            Ui ui,
            Value<Boolean> visible,
            Value<Boolean> active,
            Value<Boolean> completed
    ) {
        Value<ExpeditionSession.Configuration> configuration = this.session.configurationValue();
        return ui.section(
                "mcsx.expedition.progress_title",
                ui.text(configuration.map(ExpeditionSession.Configuration::name))
                        .classes("ui-heading", "expedition-name"),
                ui.copy("mcsx.expedition.active").visible(active),
                ui.copy("mcsx.expedition.completed").visible(completed).classes("completed-copy"),
                ui.text(this.session.progressSummary())
                        .tag(TAG_PROGRESS)
                        .classes("progress-summary"),
                this.createProgressRow(
                        ui,
                        "mcsx.expedition.travel",
                        configuration.map(ExpeditionSession.Configuration::travelEnabled),
                        this.session.travelProgress()
                ),
                this.createProgressRow(
                        ui,
                        "mcsx.expedition.elevation",
                        configuration.map(ExpeditionSession.Configuration::elevationEnabled),
                        this.session.elevationProgress()
                ),
                this.createProgressRow(
                        ui,
                        "mcsx.expedition.supplies",
                        configuration.map(ExpeditionSession.Configuration::suppliesEnabled),
                        this.session.suppliesProgress()
                ),
                ui.actions(
                        ui.dangerButton("mcsx.expedition.abort", this::abort)
                                .visible(active)
                                .tag(TAG_ABORT),
                        ui.button("mcsx.expedition.reset", this::reset)
                                .visible(completed)
                                .tag(TAG_RESET_PROGRESS)
                )
        ).visible(visible).classes("progress-card");
    }

    private Column createProgressRow(
            Ui ui,
            String label,
            Value<Boolean> visible,
            Value<? extends CharSequence> progress
    ) {
        return ui.column(
                ui.copy(label),
                ui.text(progress).classes("objective-value")
        ).visible(visible).classes("objective-progress");
    }

    private Value<Boolean> createValid() {
        Value<Boolean> nameValid = this.session.draftName().map(name -> !name.trim().isEmpty());
        Value<Boolean> travelValid = Value.combine(
                this.session.draftTravelEnabled(),
                this.session.draftTravelTarget(),
                (enabled, target) -> !enabled || positive(target)
        );
        Value<Boolean> elevationValid = Value.combine(
                this.session.draftElevationEnabled(),
                this.session.draftElevationTarget(),
                (enabled, target) -> !enabled || integer(target)
        );
        Value<Boolean> suppliesValid = Value.combine(
                this.session.draftSuppliesEnabled(),
                this.session.draftSuppliesTarget(),
                (enabled, target) -> !enabled || positive(target)
        );
        Value<Boolean> anyObjective = Values.any(
                this.session.draftTravelEnabled(),
                this.session.draftElevationEnabled(),
                this.session.draftSuppliesEnabled()
        );
        return Values.all(nameValid, travelValid, elevationValid, suppliesValid, anyObjective);
    }

    private void start() {
        if (!this.valid.get()) {
            this.validationVisible.set(true);
            return;
        }

        boolean travelEnabled = this.session.draftTravelEnabled().get();
        boolean elevationEnabled = this.session.draftElevationEnabled().get();
        boolean suppliesEnabled = this.session.draftSuppliesEnabled().get();
        ExpeditionSession.Configuration configuration = new ExpeditionSession.Configuration(
                this.session.draftName().get(),
                travelEnabled,
                target(travelEnabled, this.session.draftTravelTarget().get()),
                elevationEnabled,
                target(elevationEnabled, this.session.draftElevationTarget().get()),
                suppliesEnabled,
                target(suppliesEnabled, this.session.draftSuppliesTarget().get())
        );
        Minecraft minecraft = Minecraft.getInstance();
        // Only close over a start that happened: a refused one leaves the planner up rather than
        // pretending an expedition is running.
        minecraft.schedule(() -> {
            if (this.session.start(configuration)) minecraft.setScreen(null);
        });
    }

    private void abort() {
        Minecraft.getInstance().schedule(this.session::abort);
    }

    private void reset() {
        this.validationVisible.set(false);
        Minecraft.getInstance().schedule(this.session::reset);
    }

    private static boolean positive(String value) {
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean integer(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int target(boolean enabled, String value) {
        return enabled ? Integer.parseInt(value.trim()) : 0;
    }
}
