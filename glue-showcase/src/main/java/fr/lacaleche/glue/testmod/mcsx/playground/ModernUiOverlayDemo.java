package fr.lacaleche.glue.testmod.mcsx.playground;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/**
 * The playground HUD overlay: a {@link UiOverlay.Hud} host whose fragment shows a static card during
 * gameplay. MCSX composites the mounted overlay on its own, so mounting through {@link #HUD} is the
 * entire integration.
 */
public final class ModernUiOverlayDemo {

    public static final UiOverlay.Hud<HudFragment> HUD = UiOverlay.hud(HudFragment::new);
    public static final String TAG_ROOT = "mcsx.overlay.root";
    public static final String TAG_TITLE = "mcsx.overlay.title";

    private ModernUiOverlayDemo() {
    }

    public static final class HudFragment extends Fragment {

        @Override
        public View onCreateView(
                LayoutInflater inflater,
                ViewGroup container,
                DataSet savedInstanceState
        ) {
            Ui ui = Ui.with(this.requireContext());
            Column root = ui.column(
                    ui.column(
                            ui.heading("mcsx.showcase.playground_overlay_title").tag(TAG_TITLE),
                            ui.copy("mcsx.showcase.playground_overlay_status")
                    ).classes("hud-card")
            ).tag(TAG_ROOT).classes("hud-root");
            root.theme(Themes.mcsx());
            root.stylesheet(Stylesheets.resource(TestmodClient.id("hud-overlay")));
            return root;
        }
    }
}
