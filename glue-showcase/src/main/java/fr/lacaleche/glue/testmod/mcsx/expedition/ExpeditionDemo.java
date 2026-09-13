package fr.lacaleche.glue.testmod.mcsx.expedition;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class ExpeditionDemo {

    public static final ExpeditionDemo INSTANCE = new ExpeditionDemo();
    public static final String TAG_HUD_ROOT = "mcsx.expedition.hud";
    public static final String TAG_HUD_TITLE = "mcsx.expedition.hud-title";
    public static final String TAG_HUD_PROGRESS = "mcsx.expedition.hud-progress";

    private final ExpeditionSession session = ExpeditionSession.INSTANCE;
    private final UiOverlay.Hud<HudFragment> hud = UiOverlay.hud(HudFragment::new);
    private ExpeditionPlanner planner;
    private boolean initialized;

    private ExpeditionDemo() {
    }

    public void init() {
        if (this.initialized) return;

        this.initialized = true;
        this.session.init();
    }

    /**
     * Opens the planner unless a foreign overlay owns the exclusive MCSX slot. Starting an expedition
     * mounts this demo's HUD into that slot, so a planner opened under someone else's workspace could
     * only ever fail on submit. The guard belongs on this method rather than on the showcase control
     * because every entry point (screen, test, command) goes
     * through it, and it lets this demo's own HUD through: reopening the planner over a running
     * expedition to inspect or abort it is a supported flow.
     */
    public void openPlanner(Minecraft minecraft) {
        if (UiOverlay.isOccupied() && !this.hud.isMounted()) return;

        ExpeditionPlanner next = new ExpeditionPlanner();
        this.planner = next;
        next.show(minecraft.screen);
    }

    public UiOverlay.Hud<HudFragment> hud() {
        return this.hud;
    }

    public ExpeditionPlanner planner() {
        if (this.planner == null) throw new IllegalStateException("Expedition planner has not been opened");
        return this.planner;
    }

    public static final class HudFragment extends Fragment {

        private final ExpeditionSession session = ExpeditionSession.INSTANCE;

        @Override
        public View onCreateView(
                LayoutInflater inflater,
                ViewGroup container,
                DataSet savedInstanceState
        ) {
            Ui ui = Ui.with(this.requireContext());
            Value<ExpeditionSession.Configuration> configuration = this.session.configurationValue();
            Value<Boolean> completed = this.session.stageValue().map(stage ->
                    stage == ExpeditionSession.Stage.COMPLETED);
            Column root = ui.column(
                    ui.column(
                            ui.text(configuration.map(ExpeditionSession.Configuration::name))
                                    .classes("ui-heading")
                                    .tag(TAG_HUD_TITLE),
                            ui.text(this.session.progressSummary())
                                    .tag(TAG_HUD_PROGRESS)
                                    .classes("hud-summary"),
                            ui.copy("mcsx.expedition.completed").visible(completed).classes("hud-completed"),
                            this.objective(
                                    ui,
                                    "mcsx.expedition.travel",
                                    configuration.map(ExpeditionSession.Configuration::travelEnabled),
                                    this.session.travelProgress()
                            ),
                            this.objective(
                                    ui,
                                    "mcsx.expedition.elevation",
                                    configuration.map(ExpeditionSession.Configuration::elevationEnabled),
                                    this.session.elevationProgress()
                            ),
                            this.objective(
                                    ui,
                                    "mcsx.expedition.supplies",
                                    configuration.map(ExpeditionSession.Configuration::suppliesEnabled),
                                    this.session.suppliesProgress()
                            )
                    ).classes("hud-card")
            ).tag(TAG_HUD_ROOT).classes("hud-root");
            root.theme(Themes.mcsx());
            root.stylesheet(Stylesheets.resource(TestmodClient.id("hud-overlay")));
            return root;
        }

        private Column objective(
                Ui ui,
                String label,
                Value<Boolean> visible,
                Value<? extends CharSequence> progress
        ) {
            return ui.column(
                    ui.copy(label),
                    ui.text(progress).classes("hud-objective-value")
            ).visible(visible).classes("hud-objective");
        }
    }
}
