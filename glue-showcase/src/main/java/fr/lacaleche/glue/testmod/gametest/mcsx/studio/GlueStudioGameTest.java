package fr.lacaleche.glue.testmod.gametest.mcsx.studio;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Checkbox;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.TextField;
import fr.lacaleche.glue.mcsx.client.dock.DockTags;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.gametest.mcsx.RealInput;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionDemo;
import fr.lacaleche.glue.testmod.mcsx.studio.GlueStudio;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.require;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireEquals;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireSame;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.tagged;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.ui;

/**
 * Drives the Glue Studio workspace against a live world: spawn a light, restyle it through the
 * inspector, then exercise the dock itself &mdash; splitter drag, tab undocking, floating-window move
 * and redock, pane close/reopen, maximize/restore and retained View identity.
 */
public final class GlueStudioGameTest {

    private GlueStudioGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:mcsx-studio", GlueStudioGameTest::create);
    }

    private static GameTest create() {
        GlueStudio studio = new GlueStudio(false);
        Dockspace dockspace = studio.dockspace();
        AtomicReference<View> viewportContent = new AtomicReference<>();
        AtomicReference<View> consoleContent = new AtomicReference<>();
        AtomicReference<ScrollView> emittersContent = new AtomicReference<>();
        int[] windowsBeforeConsole = new int[1];
        int[] lightsBeforeSpawn = new int[1];
        int[] viewportWidthBeforeMax = new int[1];

        GameTest test = GameTest.create("glue-test:mcsx-studio")
                .waitForWorld()
                .run("open glue studio", ctx -> studio.open())
                .waitUntil("glue studio is open", ctx -> dockspace.getView() != null)
                .waitTicks(20);

        ui(test, "assert authored studio layout", () -> {
            require(dockspace.layout().tree() instanceof DockSplit, "Studio layout root is not split");
            DockSplit root = (DockSplit) dockspace.layout().tree();
            requireEquals(3, root.shares().size(), "studio root share count");
            require(Math.abs(root.shares().getFirst() - 0.24) < 1e-6,
                    "Authored light-column share was not loaded");
            require(root.children().getFirst() instanceof DockTabs, "Light column is not tabbed");
            requireEquals(List.of("lights", "palette"), ((DockTabs) root.children().getFirst()).tabs(),
                    "light column tabs");
            require(root.children().get(1) instanceof DockSplit, "Viewport column is not split");
            DockSplit center = (DockSplit) root.children().get(1);
            require(center.children().getFirst() instanceof DockTabs, "Viewport leaf is not tabbed");
            requireEquals(List.of("viewport"), ((DockTabs) center.children().getFirst()).tabs(),
                    "viewport leaf tabs");
            requireEquals(List.of("console", "radar", "effects"),
                    ((DockTabs) center.children().get(1)).tabs(), "viewport sidecar tabs");
            require(root.children().get(2) instanceof DockTabs, "Inspector column is not tabbed");
            requireEquals(List.of("inspector", "emitters"),
                    ((DockTabs) root.children().get(2)).tabs(), "inspector column tabs");
            viewportContent.set(tagged(dockspace, GlueStudio.TAG_VIEWPORT));
            consoleContent.set(tagged(dockspace, GlueStudio.TAG_CONSOLE));
            tagged(dockspace, GlueStudio.TAG_STATUS);
            tagged(dockspace, GlueStudio.TAG_LIGHT_LIST);
        });
        ui(test, "focus the blocks pane", () -> dockspace.focusPane("palette"));
        test.waitTicks(3);
        ui(test, "assert blocks content is top aligned", () -> assertScrollingPane(
                tagged(dockspace, GlueStudio.TAG_PALETTE_CONTENT, ScrollView.class),
                "blocks"
        ));
        ui(test, "focus the create pane for layout", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);
        ui(test, "assert create content is top aligned", () -> {
            ScrollView content = tagged(dockspace, GlueStudio.TAG_EMITTERS_CONTENT, ScrollView.class);
            emittersContent.set(content);
            assertScrollingPane(content, "create");
        });
        ui(test, "restore authored active panes", () -> {
            dockspace.focusPane("lights");
            dockspace.focusPane("inspector");
        });
        test.waitTicks(3);
        test.run("assert the world is confined to the viewport pane", ctx -> {
            GameViewport.Bounds bounds = GameViewport.bounds();
            require(bounds != null, "Mounting the viewport pane did not redirect the world render");
            require(bounds.width() > 32 && bounds.height() > 32,
                    "Viewport bounds are degenerate: " + bounds);
            require(bounds.width() < ctx.client().getWindow().getWidth(),
                    "Viewport bounds cover the whole window, so nothing was confined");
        });
        test.screenshot("mcsx-studio-initial");

        test.run("record demo light count", ctx -> {
            lightsBeforeSpawn[0] = DemoLights.INSTANCE.spawned().size();
        });
        ui(test, "focus the emitters pane", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);
        ui(test, "assert retained create layout", () -> {
            ScrollView content = tagged(dockspace, GlueStudio.TAG_EMITTERS_CONTENT, ScrollView.class);
            requireSame(emittersContent.get(), content, "create content");
            assertScrollingPane(content, "retained create");
        });
        ui(test, "spawn a point emitter", () ->
                tagged(dockspace, GlueStudio.TAG_SPAWN_POINT, Button.class).performClick());
        test.waitTicks(10);
        test.run("assert the emitter reached the world", ctx ->
                require(DemoLights.INSTANCE.spawned().size() == lightsBeforeSpawn[0] + 1,
                        "Spawn button did not add a demo light"));

        ui(test, "focus the inspector pane", () -> dockspace.focusPane("inspector"));
        test.waitTicks(3);
        ui(test, "restyle the selected emitter", () -> {
            tagged(dockspace, GlueStudio.TAG_RED, TextField.class).setText("40");
            tagged(dockspace, GlueStudio.TAG_GREEN, TextField.class).setText("220");
            tagged(dockspace, GlueStudio.TAG_BLUE, TextField.class).setText("255");
            tagged(dockspace, GlueStudio.TAG_INTENSITY, TextField.class).setText("4.0");
            tagged(dockspace, GlueStudio.TAG_RANGE, TextField.class).setText("20");
            Checkbox shadow = tagged(dockspace, GlueStudio.TAG_SHADOW, Checkbox.class);
            shadow.setChecked(false);
        });
        test.waitTicks(3);
        ui(test, "apply the inspector draft", () -> {
            Button apply = tagged(dockspace, GlueStudio.TAG_APPLY, Button.class);
            require(apply.isEnabled(), "A valid inspector draft left Apply disabled");
            apply.performClick();
        });
        test.waitTicks(10);
        test.run("assert the restyled emitter", ctx -> {
            List<Light> lights = DemoLights.INSTANCE.spawned();
            require(!lights.isEmpty(), "The restyled emitter disappeared");
            Light light = lights.getLast();
            require(Math.abs(light.range - 20f) < 0.01f, "Applied range did not reach the light");
            require(Math.abs(light.intensity - 4f) < 0.01f, "Applied intensity did not reach the light");
            require(!light.castsShadow, "Applied shadow flag did not reach the light");
        });
        test.screenshot("mcsx-studio-restyled");

        ui(test, "type an unparseable intensity", () ->
                tagged(dockspace, GlueStudio.TAG_INTENSITY, TextField.class).setText("nope"));
        test.waitTicks(3);
        ui(test, "assert the invalid draft blocks Apply", () ->
                require(!tagged(dockspace, GlueStudio.TAG_APPLY, Button.class).isEnabled(),
                        "An invalid inspector draft left Apply enabled"));
        ui(test, "repair the intensity", () ->
                tagged(dockspace, GlueStudio.TAG_INTENSITY, TextField.class).setText("4.0"));
        test.waitTicks(3);
        ui(test, "assert the repaired draft enables Apply", () ->
                require(tagged(dockspace, GlueStudio.TAG_APPLY, Button.class).isEnabled(),
                        "A repaired inspector draft left Apply disabled"));

        ui(test, "focus the create pane", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);
        ui(test, "spawn the unshadowed stress ring", () ->
                tagged(dockspace, GlueStudio.TAG_STRESS_RING, Button.class).performClick());
        test.waitTicks(10);
        test.run("assert the stress ring reached the world", ctx ->
                require(DemoLights.INSTANCE.spawned().size() > lightsBeforeSpawn[0] + 1,
                        "Stress ring button did not add demo lights"));

        ui(test, "hide and reopen the console", () -> dockspace.togglePane("console"));
        test.waitTicks(3);
        ui(test, "reopen the console", () -> dockspace.togglePane("console"));
        test.waitTicks(3);
        ui(test, "assert retained console identity", () ->
                requireSame(consoleContent.get(), tagged(dockspace, GlueStudio.TAG_CONSOLE), "console"));
        ui(test, "maximize the viewport", () -> {
            GameViewport.Bounds before = GameViewport.bounds();
            require(before != null, "Viewport bounds missing before maximize");
            viewportWidthBeforeMax[0] = before.width();
            dockspace.maximizePane("viewport");
        });
        test.waitTicks(3);
        // A transparent pane keeps its hole while maximized: the world must grow to the stage, not
        // vanish behind an opaque overlay.
        test.run("assert maximizing grows the world bounds", ctx -> {
            GameViewport.Bounds bounds = GameViewport.bounds();
            require(bounds != null, "Maximizing the viewport dropped the world bounds");
            require(bounds.width() > viewportWidthBeforeMax[0],
                    "Maximized viewport did not grow the world bounds");
        });
        ui(test, "assert viewport maximize and restore", () -> {
            requireEquals("viewport", dockspace.maximizedPane().orElse(null), "maximized studio pane");
            requireSame(viewportContent.get(), tagged(dockspace, GlueStudio.TAG_VIEWPORT), "maximized viewport");
            dockspace.restoreMaximizedPane();
        });
        test.waitTicks(3);
        test.run("assert the world returned to the docked viewport", ctx -> {
            GameViewport.Bounds bounds = GameViewport.bounds();
            require(bounds != null, "Restore dropped the world bounds");
            requireEquals(viewportWidthBeforeMax[0], bounds.width(), "restored viewport width");
        });

        ui(test, "undock the game viewport", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View tab = host.findViewWithTag("viewport");
            require(tab != null, "Missing viewport dock tab");
            int windows = dockspace.layout().windows().size();
            float[] start = centerInHost(host, tab);
            drag(host, start[0], start[1], 2, host.getHeight() / 2.0f);
            requireEquals(windows + 1, dockspace.layout().windows().size(),
                    "floating viewport window count");
            requireSame(viewportContent.get(), tagged(dockspace, GlueStudio.TAG_VIEWPORT),
                    "floating viewport content");
        });
        test.waitTicks(5);
        ui(test, "assert the floating viewport owns the world bounds", () ->
                requireViewportMatches(tagged(dockspace, GlueStudio.TAG_VIEWPORT)));
        test.screenshot("mcsx-studio-floating-viewport");

        ui(test, "move the floating game viewport", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View window = floatingWindow(host, "viewport");
            int presentedX = window.getLeft();
            int presentedY = window.getTop();
            float[] origin = pointInHost(host, window, window.getWidth() / 2.0f, 16);
            drag(host, origin[0], origin[1], origin[0] + 70, origin[1] + 35);
            DockWindow moved = floatingLayout(dockspace, "viewport");
            requireEquals(presentedX + 70, moved.x(), "moved viewport window x");
            requireEquals(presentedY + 35, moved.y(), "moved viewport window y");
        });
        test.waitTicks(5);
        ui(test, "assert the world followed the floating viewport", () ->
                requireViewportMatches(tagged(dockspace, GlueStudio.TAG_VIEWPORT)));

        ui(test, "resize the floating game viewport", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View window = floatingWindow(host, "viewport");
            DockWindow before = floatingLayout(dockspace, "viewport");
            float[] corner = pointInHost(host, window, window.getWidth() - 2, window.getHeight() - 2);
            drag(host, corner[0], corner[1], corner[0] + 80, corner[1] + 60);
            DockWindow resized = floatingLayout(dockspace, "viewport");
            require(resized.width() > before.width(), "Floating viewport width did not grow");
            require(resized.height() > before.height(), "Floating viewport height did not grow");
        });
        test.waitTicks(5);
        ui(test, "assert the world resized with the floating viewport", () ->
                requireViewportMatches(tagged(dockspace, GlueStudio.TAG_VIEWPORT)));
        test.screenshot("mcsx-studio-floating-viewport-resized");

        reallyClick(test, "the floating viewport", () -> tagged(dockspace, GlueStudio.TAG_VIEWPORT));
        test.waitTicks(5);

        // The planner must decline because starting an expedition would have to mount its HUD into
        // the overlay slot Studio is holding.
        test.run("ask the expedition planner to open under Studio", ctx ->
                ExpeditionDemo.INSTANCE.openPlanner(ctx.client()));
        test.waitTicks(3);
        test.run("assert the expedition planner stayed shut", ctx -> {
            require(ctx.client().screen == null, "The expedition planner opened over the Studio workspace");
            require(dockspace.isOpen(), "Studio lost the overlay slot to the expedition planner");
        });

        test.run("open inventory in the floating viewport", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_E));
        test.waitUntil("the floating viewport inventory opens",
                        ctx -> ctx.client().screen instanceof AbstractContainerScreen)
                .waitTicks(5);
        test.run("assert inventory uses the floating viewport", ctx -> {
            GameViewport.Bounds bounds = GameViewport.bounds();
            require(bounds != null, "Floating viewport bounds are gone under inventory");
            requireEquals(Math.ceilDiv(bounds.width(), ctx.client().getWindow().getGuiScale()),
                    ctx.client().screen.width, "floating inventory layout width");
        });
        test.screenshot("mcsx-studio-floating-viewport-inventory");
        test.run("close the floating viewport inventory", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitUntil("the floating viewport inventory closes", ctx -> ctx.client().screen == null)
                .waitTicks(3);
        test.run("return from the floating viewport to the workspace", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitTicks(3);

        ui(test, "redock the floating game viewport", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View window = floatingWindow(host, "viewport");
            int windows = dockspace.layout().windows().size();
            int gutter = ((ViewGroup) host).getChildAt(0).getLeft();
            float[] origin = pointInHost(host, window, window.getWidth() / 2.0f, 16);
            drag(host, origin[0], origin[1], host.getWidth() / 2.0f, gutter + 25);
            requireEquals(windows - 1, dockspace.layout().windows().size(),
                    "viewport window count after redock");
        });
        ui(test, "restore the authored layout after viewport redocking", dockspace::resetLayout);
        test.waitTicks(5);
        ui(test, "assert retained viewport identity after floating", () -> {
            requireSame(viewportContent.get(), tagged(dockspace, GlueStudio.TAG_VIEWPORT),
                    "viewport content after floating redock");
            requireViewportMatches(tagged(dockspace, GlueStudio.TAG_VIEWPORT));
        });

        ui(test, "resize the light column through pointer input", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View splitter = host.findViewByPredicate(view -> DockTags.SPLITTER.equals(view.getTag())
                    && view.getWidth() <= 8 && view.getHeight() > 100);
            require(splitter != null, "Missing studio column splitter");
            double before = ((DockSplit) dockspace.layout().tree()).shares().getFirst();
            float[] start = centerInHost(host, splitter);
            drag(host, start[0], start[1], start[0] + 30, start[1]);
            double after = ((DockSplit) dockspace.layout().tree()).shares().getFirst();
            require(after > before, "Studio splitter drag did not change its share");
        });
        ui(test, "undock the palette tab", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View tab = host.findViewWithTag("palette");
            require(tab != null, "Missing palette dock tab");
            windowsBeforeConsole[0] = dockspace.layout().windows().size();
            float[] start = centerInHost(host, tab);
            drag(host, start[0], start[1], 2, host.getHeight() / 2.0f);
            requireEquals(windowsBeforeConsole[0] + 1, dockspace.layout().windows().size(),
                    "floating palette window count");
            requireSame(viewportContent.get(), tagged(dockspace, GlueStudio.TAG_VIEWPORT),
                    "viewport after undock");
        });
        ui(test, "move and redock the palette window", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View window = floatingWindow(host, "palette");
            int presentedX = window.getLeft();
            float[] origin = pointInHost(host, window, window.getWidth() / 2.0f, 16);
            drag(host, origin[0], origin[1], origin[0] + 35, origin[1] + 25);
            DockWindow moved = dockspace.layout().windows().stream()
                    .filter(candidate -> candidate.node() instanceof DockTabs tabs && tabs.tabs().contains("palette"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Missing moved palette layout"));
            requireEquals(presentedX + 35, moved.x(), "moved palette window x");
            window = floatingWindow(host, "palette");
            View lights = host.findViewWithTag("lights");
            require(lights != null, "Missing lights dock tab");
            origin = pointInHost(host, window, window.getWidth() / 2.0f, 16);
            float[] target = centerInHost(host, lights);
            drag(host, origin[0], origin[1], target[0], target[1]);
            requireEquals(windowsBeforeConsole[0], dockspace.layout().windows().size(),
                    "palette window count after redock");
        });

        AtomicReference<View> lightListBeforeMoves = new AtomicReference<>();
        ui(test, "focus the lights pane before the strip moves", () -> dockspace.focusPane("lights"));
        test.waitTicks(3);
        ui(test, "record the light list identity", () ->
                lightListBeforeMoves.set(tagged(dockspace, GlueStudio.TAG_LIGHT_LIST)));
        ui(test, "reorder the palette tab to the front of its strip", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View palette = host.findViewWithTag("palette");
            View lights = host.findViewWithTag("lights");
            require(palette != null && lights != null, "Missing strip tabs for the reorder drag");
            float[] from = centerInHost(host, palette);
            float[] before = pointInHost(host, lights, 2, lights.getHeight() / 2.0f);
            drag(host, from[0], from[1], before[0], before[1]);
            DockTabs group = findGroup(dockspace.layout().tree(), tabs -> tabs.tabs().contains("palette"));
            requireEquals(List.of("palette", "lights"), group.tabs(), "strip order after reorder");
            requireEquals("palette", group.active(), "active tab after reorder");
        });
        ui(test, "drop the palette tab on the root right guide", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View palette = host.findViewWithTag("palette");
            require(palette != null, "Missing palette tab for the guide drag");
            int rootChildren = ((DockSplit) dockspace.layout().tree()).children().size();
            // The stage is inset by the themed gutter, which the docked tree is laid out at; the
            // guide control center sits ROOT_GUIDE_INSET + GUIDE_SIZE / 2 = 25 px inside the edge.
            int gutter = ((ViewGroup) host).getChildAt(0).getLeft();
            float[] from = centerInHost(host, palette);
            drag(host, from[0], from[1], host.getWidth() - gutter - 25, host.getHeight() / 2.0f);
            DockSplit root = (DockSplit) dockspace.layout().tree();
            requireEquals(rootChildren + 1, root.children().size(), "root children after guide drop");
            require(root.children().getLast() instanceof DockTabs last
                    && last.tabs().equals(List.of("palette")),
                    "Root right guide did not split the palette off the stage edge");
        });
        ui(test, "return the palette through the lights strip at pointer position", () -> {
            View host = tagged(dockspace, DockTags.HOST);
            View palette = host.findViewWithTag("palette");
            View lights = host.findViewWithTag("lights");
            require(palette != null && lights != null, "Missing tabs for the strip return drag");
            float[] from = centerInHost(host, palette);
            float[] after = pointInHost(host, lights, lights.getWidth() - 2, lights.getHeight() / 2.0f);
            drag(host, from[0], from[1], after[0], after[1]);
            DockTabs group = findGroup(dockspace.layout().tree(), tabs -> tabs.tabs().contains("palette"));
            requireEquals(List.of("lights", "palette"), group.tabs(), "strip order after the return drop");
        });
        ui(test, "refocus the lights pane after the strip moves", () -> dockspace.focusPane("lights"));
        test.waitTicks(3);
        ui(test, "assert retained pane content across the strip moves", () ->
                requireSame(lightListBeforeMoves.get(), tagged(dockspace, GlueStudio.TAG_LIGHT_LIST),
                        "light list across reorder and guide drops"));

        ui(test, "assert the viewport starts released", () ->
                require(!tagged(dockspace, GlueStudio.TAG_VIEWPORT_RELEASE, Button.class).isEnabled(),
                        "Release is enabled before the viewport was ever captured"));
        ui(test, "capture the viewport", () ->
                tagged(dockspace, GlueStudio.TAG_VIEWPORT).performClick());
        test.waitTicks(5);
        ui(test, "assert the viewport captured the player", () ->
                require(tagged(dockspace, GlueStudio.TAG_VIEWPORT_RELEASE, Button.class).isEnabled(),
                        "Clicking the viewport did not capture"));
        ui(test, "release the viewport", () ->
                tagged(dockspace, GlueStudio.TAG_VIEWPORT_RELEASE, Button.class).performClick());
        test.waitTicks(5);
        ui(test, "assert the viewport released the player", () ->
                require(!tagged(dockspace, GlueStudio.TAG_VIEWPORT_RELEASE, Button.class).isEnabled(),
                        "Release did not hand the cursor back"));

        // Real input: the same MouseHandler and KeyboardHandler entry points GLFW calls, so the mixin
        // arbitration, overlay event synthesis and window-group routing are on the tested path — the
        // dispatch helpers above inject below all of that and cannot see it break.
        AtomicReference<String> expectedActiveTab = new AtomicReference<>();
        AtomicReference<List<Double>> sharesBeforeRealDrag = new AtomicReference<>();
        AtomicReference<float[]> dragStart = new AtomicReference<>();
        int[] lightsBeforeRealClick = new int[1];

        ui(test, "focus the create pane for the real click", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);

        ui(test, "focus the inspector for consumed keyboard input", () -> dockspace.focusPane("inspector"));
        test.waitTicks(3);
        ui(test, "focus the shadow checkbox for consumed keyboard input", () ->
                require(tagged(dockspace, GlueStudio.TAG_SHADOW, Checkbox.class).requestFocus(),
                        "Shadow checkbox rejected keyboard focus"));
        test.run("press Space on the focused dock checkbox", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_SPACE, true));
        test.waitTicks(3);
        test.run("assert consumed Space did not reach jump", ctx ->
                require(!ctx.client().options.keyJump.isDown(),
                        "A key consumed by the dock checkbox reached Minecraft"));
        test.run("release Space on the focused dock checkbox", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_SPACE, false));
        test.waitTicks(3);
        test.run("assert consumed Space release left jump clear", ctx ->
                require(!ctx.client().options.keyJump.isDown(),
                        "A consumed key release changed Minecraft's binding state"));

        ui(test, "focus a Studio text field", () ->
                require(tagged(dockspace, GlueStudio.TAG_RED).requestFocus(),
                        "Studio text field rejected keyboard focus"));
        test.waitTicks(3);
        test.run("press W while the text field owns input", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_W, true));
        test.waitTicks(3);
        test.run("assert text input did not move the player", ctx ->
                require(!ctx.client().options.keyUp.isDown(),
                        "A printable key escaped the focused text field"));
        test.run("release W while the text field owns input", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_W, false));
        test.waitTicks(3);

        ui(test, "clear text focus for unhandled keyboard input", () -> {
            TextField field = tagged(dockspace, GlueStudio.TAG_RED, TextField.class);
            field.clearFocus();
            require(!field.hasFocus(), "Studio text field kept keyboard focus");
        });
        test.run("press unhandled W through the workspace", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_W, true));
        test.waitUntil("unhandled W reaches Minecraft", ctx -> ctx.client().options.keyUp.isDown());
        test.run("release unhandled W through the workspace", ctx ->
                RealInput.key(ctx.client(), GLFW.GLFW_KEY_W, false));
        test.waitUntil("forwarded W release reaches Minecraft", ctx -> !ctx.client().options.keyUp.isDown());

        ui(test, "return to the create pane after keyboard routing", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);
        test.run("record demo lights before the real click", ctx ->
                lightsBeforeRealClick[0] = DemoLights.INSTANCE.spawned().size());
        reallyClick(test, "the spawn button", () -> tagged(dockspace, GlueStudio.TAG_SPAWN_POINT));
        test.waitTicks(10);
        test.run("assert the real click spawned an emitter", ctx ->
                require(DemoLights.INSTANCE.spawned().size() == lightsBeforeRealClick[0] + 1,
                        "A real mouse click did not reach the spawn button"));

        // The floating console reopened earlier would occlude docked tab strips near the stage
        // centre; the real clicks below need an unobstructed target.
        ui(test, "close the floating console before the real tab click", () ->
                dockspace.togglePane("console"));
        test.waitTicks(3);
        reallyClick(test, "an inactive dock tab", () -> {
            DockTabs group = findGroup(dockspace.layout().tree(), tabs -> tabs.tabs().size() >= 2);
            require(group != null, "No multi-tab group to activate");
            String inactive = group.tabs().stream()
                    .filter(tab -> !tab.equals(group.active()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Multi-tab group has no inactive tab"));
            expectedActiveTab.set(inactive);
            View tab = tagged(dockspace, DockTags.HOST).findViewWithTag(inactive);
            require(tab != null, "Missing dock tab " + inactive);
            return tab;
        });
        test.waitTicks(5);
        ui(test, "assert the real tab click activated it", () -> {
            DockTabs group = findGroup(dockspace.layout().tree(),
                    tabs -> tabs.tabs().contains(expectedActiveTab.get()));
            require(group != null, "Clicked pane left the layout");
            requireEquals(expectedActiveTab.get(), group.active(), "clicked tab activation");
        });

        ui(test, "locate a vertical splitter for the real drag", () -> {
            View splitter = verticalSplitter(tagged(dockspace, DockTags.HOST));
            require(splitter != null, "Missing studio column splitter for the real drag");
            sharesBeforeRealDrag.set(collectShares(dockspace.layout().tree()));
            dragStart.set(centerInWindow(splitter));
        });
        test.run("really drag the splitter", ctx -> {
            float[] start = dragStart.get();
            RealInput.moveToFramebuffer(ctx.client(), start[0], start[1]);
            RealInput.leftButton(ctx.client(), true);
            RealInput.moveToFramebuffer(ctx.client(), start[0] + 12, start[1]);
            RealInput.moveToFramebuffer(ctx.client(), start[0] + 40, start[1]);
            RealInput.leftButton(ctx.client(), false);
        });
        test.waitTicks(5);
        ui(test, "assert the real splitter drag resized its split", () ->
                require(!collectShares(dockspace.layout().tree()).equals(sharesBeforeRealDrag.get()),
                        "A real splitter drag did not change any split share"));

        reallyClick(test, "the viewport to fly", () -> tagged(dockspace, GlueStudio.TAG_VIEWPORT));
        test.waitTicks(5);
        test.run("assert the real viewport click grabbed the mouse", ctx ->
                require(ctx.client().mouseHandler.isMouseGrabbed(),
                        "A real viewport click did not grab the mouse"));
        test.run("press Escape to hand the game back", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitTicks(5);
        test.run("assert Escape returned the pointer to the workspace", ctx ->
                require(!ctx.client().mouseHandler.isMouseGrabbed(),
                        "Escape did not release the game focus"));
        ui(test, "assert the workspace survived the round trip", () ->
                require(!tagged(dockspace, GlueStudio.TAG_VIEWPORT_RELEASE, Button.class).isEnabled(),
                        "Release control still armed after Escape"));

        // A vanilla screen opened out of gameplay lays out inside the viewport, keeps the pointer
        // only there, and leaves the workspace around it fully interactive.
        reallyClick(test, "the viewport once more", () -> tagged(dockspace, GlueStudio.TAG_VIEWPORT));
        test.waitTicks(5);
        test.run("assert mouselook resumed", ctx ->
                require(ctx.client().mouseHandler.isMouseGrabbed(), "Second viewport click did not grab"));
        test.run("press the inventory key", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_E));
        test.waitUntil("the inventory opens", ctx -> ctx.client().screen instanceof AbstractContainerScreen)
                .waitTicks(5);
        test.run("assert the inventory laid out inside the viewport", ctx -> {
            GameViewport.Bounds bounds = GameViewport.bounds();
            require(bounds != null, "Viewport bounds are gone under the inventory");
            int expectedWidth = Math.ceilDiv(bounds.width(), ctx.client().getWindow().getGuiScale());
            requireEquals(expectedWidth, ctx.client().screen.width, "inventory layout width");
            require(!ctx.client().mouseHandler.isMouseGrabbed(), "The inventory left the mouse grabbed");
        });
        ui(test, "focus the create pane beside the inventory", () -> dockspace.focusPane("emitters"));
        test.waitTicks(3);
        test.run("record demo lights before the under-screen click", ctx ->
                lightsBeforeRealClick[0] = DemoLights.INSTANCE.spawned().size());
        reallyClick(test, "the spawn button beside the inventory", () ->
                tagged(dockspace, GlueStudio.TAG_SPAWN_POINT));
        test.waitTicks(10);
        test.run("assert the workspace stayed interactive under the screen", ctx ->
                require(DemoLights.INSTANCE.spawned().size() == lightsBeforeRealClick[0] + 1,
                        "A dock click beside an open screen did not land"));
        test.screenshot("mcsx-studio-inventory");
        test.run("close the inventory with Escape", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitUntil("the inventory closes", ctx -> ctx.client().screen == null)
                .waitTicks(3);
        test.run("assert closing the screen returned to mouselook", ctx ->
                require(ctx.client().mouseHandler.isMouseGrabbed(),
                        "Closing the inventory did not restore game focus"));
        test.run("press Escape to return to the workspace", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitTicks(3);
        test.run("assert the workspace owns the pointer again", ctx ->
                require(!ctx.client().mouseHandler.isMouseGrabbed(),
                        "Escape did not return the pointer to the workspace"));

        if (RenderCompat.HAS_IRIS) {
            test.run("open the Iris shader selector", ctx -> {
                Screen screen = (Screen) IrisApi.getInstance().openMainIrisScreenObj(null);
                ctx.client().setScreen(screen);
            });
            test.waitUntil("the Iris shader selector opens", ctx -> ctx.client().screen != null)
                    .waitTicks(5)
                    .run("assert Iris kept the workspace viewport", ctx -> {
                        GameViewport.Bounds bounds = GameViewport.bounds();
                        require(bounds != null, "Iris hid the game viewport");
                        require(dockspace.isOpen(), "Iris closed the dockspace");
                        int expectedWidth = Math.ceilDiv(
                                bounds.width(), ctx.client().getWindow().getGuiScale());
                        requireEquals(expectedWidth, ctx.client().screen.width,
                                "Iris shader selector layout width");
                    })
                    .screenshot("mcsx-studio-iris-shader-selector")
                    .run("close the Iris shader selector", ctx ->
                            RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                    .waitUntil("the Iris shader selector closes", ctx -> ctx.client().screen == null)
                    .waitTicks(3);
        }

        test.run("press idle Escape in the workspace", ctx ->
                RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE));
        test.waitUntil("idle Escape opens the pause screen", ctx -> ctx.client().screen instanceof PauseScreen)
                .run("assert idle Escape did not close Studio", ctx ->
                        require(dockspace.isOpen(), "Idle Escape closed the dockspace"))
                .run("close the pause screen", ctx ->
                        RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("the pause screen closes", ctx -> ctx.client().screen == null)
                .waitTicks(3);

        test.waitTicks(10)
                .screenshot("mcsx-studio-final")
                // The workspace is an overlay, not a screen: it closes through its own API, never by
                // clearing Minecraft#screen.
                .run("close glue studio", ctx -> {
                    dockspace.close();
                    // close() on the client thread tears down inline, and the onUnmount hook drops
                    // the world claim inside that operation — the same frame, not a detach later.
                    require(!GameViewport.isActive(),
                            "Closing the workspace did not synchronously release the game viewport");
                })
                .waitUntil("glue studio closes", ctx -> dockspace.getView() == null)
                .waitTicks(10)
                 .run("assert the world returns to the whole window", ctx ->
                         require(!GameViewport.isActive(),
                                 "Closing the workspace left the world confined to the viewport"))
                // Reference shot of the same view with no workspace over it: the viewport pane should
                // show this, so a black viewport only means a black world.
                 .screenshot("mcsx-studio-world")
                 .run("open the showcase controls with F6", ctx ->
                         RealInput.tap(ctx.client(), GLFW.GLFW_KEY_F6))
                 .waitUntil("F6 opens the showcase controls",
                         ctx -> ctx.client().screen != null)
                 .waitTicks(5)
                 .run("close the showcase controls", ctx ->
                         RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                 .waitUntil("the showcase controls close", ctx -> ctx.client().screen == null);
        return test;
    }

    private static void assertScrollingPane(ScrollView scroll, String label) {
        requireEquals(1, scroll.getChildCount(), label + " scroll child count");
        require(scroll.getChildAt(0) instanceof Column, label + " scroll child is not Taffy content");
        Column content = (Column) scroll.getChildAt(0);
        requireEquals(0, content.getTop(), label + " content top");
        requireEquals(scroll.getWidth(), content.getWidth(), label + " content width");
        require(content.getHeight() >= scroll.getHeight(), label + " content does not fill viewport");
        require(content.getChildCount() > 0, label + " content has no children");
        View first = content.getChildAt(0);
        requireEquals(16, first.getLeft(), label + " first child left inset");
        requireEquals(16, first.getTop(), label + " first child top inset");
    }

    private static void drag(View host, float startX, float startY, float endX, float endY) {
        require(dispatch(host, MotionEvent.ACTION_DOWN, startX, startY), "Dock host ignored the press");
        dispatch(host, MotionEvent.ACTION_MOVE, endX, endY);
        require(dispatch(host, MotionEvent.ACTION_MOVE, endX, endY), "Dock host did not take over the drag");
        require(dispatch(host, MotionEvent.ACTION_UP, endX, endY), "Dock host did not end the drag");
    }

    private static boolean dispatch(View host, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(Core.timeNanos(), action, x, y, 0);
        try {
            return host.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static View floatingWindow(View host, String paneId) {
        View window = host.findViewByPredicate(view -> DockTags.WINDOW.equals(view.getTag())
                && view.findViewWithTag(paneId) != null);
        if (window == null) throw new IllegalStateException("Missing floating studio window");
        return window;
    }

    private static DockWindow floatingLayout(Dockspace dockspace, String paneId) {
        return dockspace.layout().windows().stream()
                .filter(window -> findGroup(window.node(), tabs -> tabs.tabs().contains(paneId)) != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing floating layout for " + paneId));
    }

    private static void requireViewportMatches(View viewport) {
        int[] location = new int[2];
        viewport.getLocationInWindow(location);
        requireEquals(new GameViewport.Bounds(location[0], location[1], viewport.getWidth(), viewport.getHeight()),
                GameViewport.bounds(), "published game viewport bounds");
    }

    private static float[] centerInHost(View host, View view) {
        return pointInHost(host, view, view.getWidth() / 2.0f, view.getHeight() / 2.0f);
    }

    private static float[] pointInHost(View host, View view, float localX, float localY) {
        int[] hostLocation = new int[2];
        int[] viewLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        view.getLocationInWindow(viewLocation);
        return new float[] {
                viewLocation[0] - hostLocation[0] + localX,
                viewLocation[1] - hostLocation[1] + localY
        };
    }

    private static List<Double> collectShares(DockNode node) {
        List<Double> shares = new ArrayList<>();
        collectShares(node, shares);
        return shares;
    }

    private static void collectShares(DockNode node, List<Double> shares) {
        if (!(node instanceof DockSplit split)) return;
        shares.addAll(split.shares());
        for (DockNode child : split.children()) collectShares(child, shares);
    }

    private static DockTabs findGroup(DockNode node, Predicate<DockTabs> match) {
        if (node instanceof DockTabs tabs) return match.test(tabs) ? tabs : null;
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                DockTabs found = findGroup(child, match);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View verticalSplitter(View host) {
        return host.findViewByPredicate(view -> DockTags.SPLITTER.equals(view.getTag())
                && view.getWidth() <= 8 && view.getHeight() > 100);
    }

    /** Adds the locate-on-UI-thread and click-on-client-thread step pair for one real click. */
    private static void reallyClick(GameTest test, String subject, Supplier<View> target) {
        AtomicReference<float[]> point = new AtomicReference<>();
        ui(test, "locate " + subject, () -> point.set(centerInWindow(target.get())));
        test.run("really click " + subject, ctx -> click(ctx.client(), point.get()));
    }

    private static float[] centerInWindow(View view) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return new float[] {
                location[0] + view.getWidth() / 2.0f,
                location[1] + view.getHeight() / 2.0f
        };
    }

    private static void click(Minecraft client, float[] point) {
        RealInput.moveToFramebuffer(client, point[0], point[1]);
        RealInput.leftButton(client, true);
        RealInput.leftButton(client, false);
    }
}
