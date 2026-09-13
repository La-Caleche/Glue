package fr.lacaleche.glue.testmod.gametest.mcsx.playground;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Checkbox;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.mcsx.client.component.TextField;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.mcsx.playground.DemoForm;
import fr.lacaleche.glue.testmod.mcsx.playground.DemoItemRow;
import fr.lacaleche.glue.testmod.mcsx.playground.ModernUiDemo;
import fr.lacaleche.glue.testmod.mcsx.playground.ModernUiOverlayDemo;
import fr.lacaleche.mui.MuiApi;
import fr.lacaleche.mui.OverlayHandle;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import net.minecraft.client.gui.screens.Screen;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.descendant;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.require;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireEquals;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireSame;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.tagged;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.ui;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

public final class ModernUiGameTest {

    private ModernUiGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:mcsx-demo", ModernUiGameTest::create);
    }

    private static GameTest create() {
        ModernUiDemo demo = new ModernUiDemo();
        AtomicReference<Screen> openedScreen = new AtomicReference<>();
        AtomicReference<View> titleView = new AtomicReference<>();
        AtomicReference<View> detailsView = new AtomicReference<>();
        AtomicReference<View> formFieldView = new AtomicReference<>();
        AtomicReference<View> overlayRoot = new AtomicReference<>();
        AtomicReference<View> firstItemRow = new AtomicReference<>();
        AtomicReference<String> originalLanguage = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> frenchReload = new AtomicReference<>();
        AtomicReference<CompletableFuture<Void>> restoreReload = new AtomicReference<>();
        AtomicReference<OverlayHandle> rawOverlay = new AtomicReference<>();

        UiOverlay.Hud<ModernUiOverlayDemo.HudFragment> overlay = ModernUiOverlayDemo.HUD;
        GameTest test = GameTest.create("glue-test:mcsx-demo")
                .waitForWorld()
                // An overlay mounted straight through MuiApi takes the same single slot the managed
                // hosts hand out, so isOccupied() has to see it too — or a keybind that checks before
                // mounting would be told the slot is free and hit the rejection it was avoiding.
                .run("mount a raw MCSX overlay", ctx -> rawOverlay.set(MuiApi.mountOverlay(new Fragment())))
                .run("raw overlay occupies the MCSX slot", ctx ->
                        require(UiOverlay.isOccupied(), "a raw overlay left the MCSX slot reported free"))
                .run("close the raw MCSX overlay", ctx -> rawOverlay.get().close())
                .run("closed raw overlay frees the MCSX slot", ctx ->
                        require(!UiOverlay.isOccupied(), "the MCSX slot stayed taken after the raw overlay closed"))
                .run("mount MCSX HUD overlay", ctx -> overlay.mount())
                .run("reject duplicate MCSX HUD overlay", ctx -> {
                    try {
                        MuiApi.mountOverlay(new Fragment());
                    } catch (IllegalStateException exception) {
                        requireEquals(
                                "A ModernUI overlay is already active",
                                exception.getMessage(),
                                "duplicate overlay failure"
                        );
                        return;
                    }
                    throw new AssertionError("A duplicate ModernUI overlay was accepted");
                })
                .waitTicks(20);
        ui(test, "assert mounted HUD overlay", () -> {
            View root = overlay.fragment().requireView();
            overlayRoot.set(root);
            require(root.isShown(), "HUD overlay is not shown during gameplay");
            require(root.getWidth() > 0, "HUD overlay has no width");
            require(root.getHeight() > 0, "HUD overlay has no height");
            View title = root.findViewWithTag(ModernUiOverlayDemo.TAG_TITLE);
            require(title instanceof Text, "HUD overlay title is missing");
            requireEquals("Playground HUD", ((Text) title).getText().toString(), "HUD title");
        });
        test.run("open ModernUiDemo", ctx -> {
                    demo.show(ctx.client().screen);
                    openedScreen.set(ctx.client().screen);
                })
                .waitUntil("ModernUiDemo screen is open", ctx ->
                        ctx.client().screen == openedScreen.get())
                .waitTicks(40);

        ui(test, "capture initial MCSX controls", () -> {
            require(!overlayRoot.get().isShown(), "HUD overlay remains shown over MCSX screen");
            Text title = tagged(demo, ModernUiDemo.TAG_TITLE, Text.class);
            titleView.set(title);
            View details = tagged(demo, ModernUiDemo.TAG_DETAILS, View.class);
            detailsView.set(details);
            requireEquals(View.VISIBLE, details.getVisibility(), "initial details visibility");
            TextField formField = tagged(demo, DemoForm.TAG_FIELD, TextField.class);
            formFieldView.set(formField);
            require(
                    !tagged(demo, DemoForm.TAG_SUBMIT, Button.class).isEnabled(),
                    "empty form submit is enabled"
            );
            require(
                    !tagged(demo, DemoForm.TAG_CONSENT, Checkbox.class).isEnabled(),
                    "empty form checkbox is enabled"
            );
            requireEquals(
                    View.GONE,
                    tagged(demo, DemoForm.TAG_ERROR, View.class).getVisibility(),
                    "initial form error visibility"
            );
            requireEquals("MCSX Playground", title.getText().toString(), "English title");
            requireEquals(
                    "Counter: 0",
                    tagged(demo, ModernUiDemo.TAG_COUNTER, Text.class).getText().toString(),
                    "initial counter"
            );
            Button increment = tagged(demo, ModernUiDemo.TAG_INCREMENT, Button.class);
            Button burst = tagged(demo, ModernUiDemo.TAG_BURST, Button.class);
            Button toggleDetails = tagged(demo, ModernUiDemo.TAG_TOGGLE_DETAILS, Button.class);
            requireEquals(
                    Themes.mcsx().get(ThemeTokens.ACCENT),
                    buttonBackground(increment),
                    "primary button fill"
            );
            requireEquals(
                    Themes.mcsx().get(ThemeTokens.SURFACE_RAISED),
                    buttonBackground(burst),
                    "secondary button fill"
            );
            requireEquals(Color.TRANSPARENT, buttonBackground(toggleDetails), "quiet button fill");
            require(increment.getElevation() > 0, "primary button is not raised");
            require(burst.getElevation() > 0, "secondary button is not raised");
            requireEquals(0, toggleDetails.getElevation(), "quiet button is raised");
        });
        ui(test, "click Increment", () ->
                tagged(demo, ModernUiDemo.TAG_INCREMENT, Button.class).performClick());
        ui(test, "assert Increment result", () -> requireEquals(
                "Counter: 1",
                tagged(demo, ModernUiDemo.TAG_COUNTER, Text.class).getText().toString(),
                "incremented counter"
        ));
        ui(test, "click Burst", () ->
                tagged(demo, ModernUiDemo.TAG_BURST, Button.class).performClick());
        ui(test, "assert Burst result", () -> {
            Text counter = tagged(demo, ModernUiDemo.TAG_COUNTER, Text.class);
            requireEquals("Counter: 4", counter.getText().toString(), "burst counter");
            requireEquals(
                    Themes.mcsx().get(ThemeTokens.TEXT_PRIMARY),
                    Color.toArgb(counter.getCurrentTextColor()),
                    "burst color"
            );
        });
        ui(test, "hide reactive details", () ->
                tagged(demo, ModernUiDemo.TAG_TOGGLE_DETAILS, Button.class).performClick());
        ui(test, "assert details left layout flow", () -> {
            View details = tagged(demo, ModernUiDemo.TAG_DETAILS, View.class);
            requireSame(detailsView.get(), details, "hidden details identity");
            requireEquals(View.GONE, details.getVisibility(), "hidden details visibility");
            requireEquals(0, details.getWidth(), "hidden details width");
            requireEquals(0, details.getHeight(), "hidden details height");
        });
        ui(test, "show reactive details", () ->
                tagged(demo, ModernUiDemo.TAG_TOGGLE_DETAILS, Button.class).performClick());
        ui(test, "assert details returned to layout flow", () -> {
            View details = tagged(demo, ModernUiDemo.TAG_DETAILS, View.class);
            requireSame(detailsView.get(), details, "restored details identity");
            requireEquals(View.VISIBLE, details.getVisibility(), "restored details visibility");
            require(details.getWidth() > 0, "restored details has no width");
            require(details.getHeight() > 0, "restored details has no height");
        });

        ui(test, "focus empty form field", () -> {
            TextField field = tagged(demo, DemoForm.TAG_FIELD, TextField.class);
            field.setText("");
            field.requestFocus();
        });
        ui(test, "assert form focus feedback", () -> requireEquals(
                View.VISIBLE,
                tagged(demo, DemoForm.TAG_FOCUS, View.class).getVisibility(),
                "focused form feedback"
        ));
        test.run("submit invalid form with Enter", ctx -> {
            openedScreen.get().keyPressed(GLFW_KEY_ENTER, 0, 0);
            openedScreen.get().keyReleased(GLFW_KEY_ENTER, 0, 0);
        });
        ui(test, "assert invalid form feedback", () -> {
            requireEquals(0, demo.form().submissionCount(), "invalid form submission count");
            requireEquals(
                    View.VISIBLE,
                    tagged(demo, DemoForm.TAG_ERROR, View.class).getVisibility(),
                    "invalid form error visibility"
            );
            require(
                    !tagged(demo, DemoForm.TAG_SUBMIT, Button.class).isEnabled(),
                    "invalid form submit is enabled"
            );
        });
        test.run("type valid form value", ctx -> {
            for (char character : "Ada".toCharArray()) {
                openedScreen.get().charTyped(character, 0);
            }
        }).waitTicks(10);
        ui(test, "focus form consent", () -> {
            Checkbox checkbox = tagged(demo, DemoForm.TAG_CONSENT, Checkbox.class);
            require(checkbox.isEnabled(), "valid form checkbox did not enable");
            require(checkbox.isFocusable(), "form checkbox is not focusable");
            require(checkbox.getWidth() > 0, "form checkbox has no width");
            require(checkbox.getHeight() > 0, "form checkbox has no height");
            require(checkbox.requestFocus(), "form checkbox rejected focus");
            require(
                    !tagged(demo, DemoForm.TAG_SUBMIT, Button.class).isEnabled(),
                    "form submit enabled without consent"
            );
        });
        test.run("press Space on form consent", ctx ->
                openedScreen.get().keyPressed(GLFW_KEY_SPACE, 0, 0));
        ui(test, "assert form consent pressed state", () -> {
            Checkbox checkbox = tagged(demo, DemoForm.TAG_CONSENT, Checkbox.class);
            require(checkbox.isPressed(), "Space did not press form checkbox");
            require(!checkbox.isChecked(), "form checkbox toggled before Space release");
            require(!demo.form().consent(), "consent signal changed before Space release");
        });
        test.run("release Space on form consent", ctx ->
                openedScreen.get().keyReleased(GLFW_KEY_SPACE, 0, 0));
        ui(test, "assert keyboard form consent", () -> {
            Checkbox checkbox = tagged(demo, DemoForm.TAG_CONSENT, Checkbox.class);
            require(!checkbox.isPressed(), "form checkbox stayed pressed");
            require(checkbox.isChecked(), "Space did not check form checkbox");
            require(checkbox.hasFocus(), "form checkbox lost focus after Space");
            require(demo.form().consent(), "checkbox did not update consent signal");
            require(
                    tagged(demo, DemoForm.TAG_SUBMIT, Button.class).isEnabled(),
                    "combined form validity did not enable submit"
            );
            TextField field = tagged(demo, DemoForm.TAG_FIELD, TextField.class);
            require(field.requestFocus(), "form field rejected restored focus");
            require(field.hasFocus(), "form field did not regain focus");
        });
        ui(test, "submit valid combined form", () ->
                tagged(demo, DemoForm.TAG_SUBMIT, Button.class).performClick());
        ui(test, "assert successful form submission", () -> {
            requireEquals(1, demo.form().submissionCount(), "valid form submission count");
            requireEquals(
                    View.VISIBLE,
                    tagged(demo, DemoForm.TAG_SUCCESS, View.class).getVisibility(),
                    "form success visibility"
            );
            tagged(demo, ModernUiDemo.TAG_EDITOR, TextField.class).requestFocus();
        });
        ui(test, "assert form focus loss feedback", () -> requireEquals(
                View.GONE,
                tagged(demo, DemoForm.TAG_FOCUS, View.class).getVisibility(),
                "blurred form feedback"
        ));
        ui(test, "repeat valid form submission", () ->
                tagged(demo, DemoForm.TAG_SUBMIT, Button.class).performClick());
        ui(test, "assert button form submission", () -> requireEquals(
                2,
                demo.form().submissionCount(),
                "button form submission count"
        ));

        ui(test, "focus native editor", () -> {
            TextField editor = tagged(demo, ModernUiDemo.TAG_EDITOR, TextField.class);
            editor.setText("");
            editor.requestFocus();
        });
        test.run("type through Minecraft screen", ctx -> {
            for (char character : "Java input".toCharArray()) {
                openedScreen.get().charTyped(character, 0);
            }
        }).waitTicks(10);
        ui(test, "assert native editor input", () -> requireEquals(
                "Java input",
                tagged(demo, ModernUiDemo.TAG_EDITOR, TextField.class).getText().toString(),
                "native editor text"
        ));

        ui(test, "edit retained keyed row", () -> {
            View row = tagged(demo, DemoItemRow.rowTag(1), View.class);
            firstItemRow.set(row);
            tagged(demo, DemoItemRow.fieldTag(1), TextField.class).setText("Edited Apple");
            requireEquals("Edited Apple", demo.itemLabel(1), "edited item signal");
        });
        ui(test, "rotate keyed rows", () ->
                tagged(demo, ModernUiDemo.TAG_ROTATE, Button.class).performClick());
        ui(test, "assert keyed row identity and order", () -> {
            Column rows = tagged(demo, ModernUiDemo.TAG_ITEMS, Column.class);
            requireEquals("Banana", rowText(rows, 0), "first rotated row");
            requireEquals("Cherry", rowText(rows, 1), "second rotated row");
            requireEquals("Edited Apple", rowText(rows, 2), "retained edited row");
            requireSame(firstItemRow.get(), rows.getChildAt(2), "retained row identity");
        });
        ui(test, "update retained row through its signal", () ->
                demo.renameItem(1, "Signal Apple"));
        ui(test, "assert signal update reached retained field", () -> {
            requireEquals(
                    "Signal Apple",
                    tagged(demo, DemoItemRow.fieldTag(1), TextField.class).getText().toString(),
                    "signal-updated row"
            );
            requireSame(
                    firstItemRow.get(),
                    tagged(demo, DemoItemRow.rowTag(1), View.class),
                    "signal-updated row identity"
            );
        });
        ui(test, "add keyed row", () ->
                tagged(demo, ModernUiDemo.TAG_ADD, Button.class).performClick());
        ui(test, "assert added keyed row", () -> {
            Column rows = tagged(demo, ModernUiDemo.TAG_ITEMS, Column.class);
            requireEquals(4, rows.getChildCount(), "row count after add");
            requireEquals(
                    "Item 4",
                    tagged(demo, DemoItemRow.fieldTag(4), TextField.class).getText().toString(),
                    "added row text"
            );
        });
        ui(test, "remove keyed row", () ->
                tagged(demo, DemoItemRow.removeTag(1), Button.class).performClick());
        ui(test, "assert removed keyed row", () -> {
            Column rows = tagged(demo, ModernUiDemo.TAG_ITEMS, Column.class);
            requireEquals(3, rows.getChildCount(), "row count after remove");
            require(firstItemRow.get().getParent() == null, "removed row is still attached");
            require(
                    demo.requireView().findViewWithTag(DemoItemRow.rowTag(1)) == null,
                    "removed row is still discoverable"
            );
        });
        ui(test, "remove second keyed row", () ->
                tagged(demo, DemoItemRow.removeTag(2), Button.class).performClick());
        ui(test, "remove third keyed row", () ->
                tagged(demo, DemoItemRow.removeTag(3), Button.class).performClick());
        ui(test, "assert Rotate disabled for one row", () -> {
            Column rows = tagged(demo, ModernUiDemo.TAG_ITEMS, Column.class);
            Button rotate = tagged(demo, ModernUiDemo.TAG_ROTATE, Button.class);
            requireEquals(1, rows.getChildCount(), "row count at disabled threshold");
            require(!rotate.isEnabled(), "Rotate remains enabled for one row");
        });

        ui(test, "fill collection for scrolling", () -> {
            Button add = tagged(demo, ModernUiDemo.TAG_ADD, Button.class);
            for (int item = 0; item < 8; item++) {
                add.performClick();
            }
        });
        test.waitTicks(10);
        ui(test, "assert Rotate re-enabled for multiple rows", () -> require(
                tagged(demo, ModernUiDemo.TAG_ROTATE, Button.class).isEnabled(),
                "Rotate did not re-enable after adding rows"
        ));
        ui(test, "scroll MCSX viewport", () -> {
            ScrollView scroll = descendant(demo, ScrollView.class);
            View content = scroll.getChildAt(0);
            require(content.getHeight() > scroll.getHeight(), "showcase does not overflow viewport");
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        ui(test, "assert viewport scrolled", () -> require(
                descendant(demo, ScrollView.class).getScrollY() > 0,
                "MCSX viewport did not scroll"
        ));

        test.run("select French language", ctx -> {
            originalLanguage.set(ctx.client().options.languageCode);
            ctx.client().options.languageCode = "fr_fr";
            ctx.client().getLanguageManager().setSelected("fr_fr");
            frenchReload.set(ctx.client().reloadResourcePacks());
        });
        waitForReload(test, "French", frenchReload);
        ui(test, "assert mounted French translation", () -> {
            Text title = tagged(demo, ModernUiDemo.TAG_TITLE, Text.class);
            Checkbox checkbox = tagged(demo, DemoForm.TAG_CONSENT, Checkbox.class);
            requireSame(titleView.get(), title, "translated title View identity");
            requireSame(
                    formFieldView.get(),
                    tagged(demo, DemoForm.TAG_FIELD, View.class),
                    "form field identity"
            );
            requireEquals("Atelier MCSX", title.getText().toString(), "French title");
            requireEquals(
                    "J'accepte les conditions de démonstration",
                    checkbox.getText().toString(),
                    "French checkbox"
            );
            require(checkbox.isChecked(), "checkbox state lost on language reload");
            requireEquals(2, demo.form().submissionCount(), "submission lost on language reload");
        });

        test.run("restore original language", ctx -> {
            String language = Objects.requireNonNull(originalLanguage.get(), "original language");
            ctx.client().options.languageCode = language;
            ctx.client().getLanguageManager().setSelected(language);
            restoreReload.set(ctx.client().reloadResourcePacks());
        });
        waitForReload(test, "restored language", restoreReload);
        ui(test, "assert mounted English translation restored", () -> {
            Text title = tagged(demo, ModernUiDemo.TAG_TITLE, Text.class);
            requireSame(titleView.get(), title, "restored title View identity");
            requireEquals("MCSX Playground", title.getText().toString(), "restored title");
        });

        test.waitUntil("ModernUiDemo remains open after interactions", ctx ->
                        ctx.client().screen == openedScreen.get())
                .screenshot("modern-ui-demo")
                .run("click MCSX Close button", ctx ->
                        Core.postOnUiThread(() -> tagged(
                                demo,
                                ModernUiDemo.TAG_CLOSE,
                                Button.class
                        ).performClick()))
                .waitUntil("MCSX Close button closes screen", ctx -> ctx.client().screen == null)
                .waitTicks(20);
        ui(test, "assert HUD overlay resumed", () -> {
            View root = overlay.fragment().requireView();
            requireSame(overlayRoot.get(), root, "HUD overlay root");
            require(root.isShown(), "HUD overlay did not resume after screen close");
        });
        test.screenshot("modern-ui-overlay")
                .run("unmount MCSX HUD overlay twice", ctx -> {
                    overlay.unmount();
                    overlay.unmount();
                })
                .waitTicks(10);
        ui(test, "assert HUD overlay detached", () -> {
            require(!overlay.isMounted(), "HUD overlay handle remains mounted");
            require(overlayRoot.get().getParent() == null, "HUD overlay root remains attached");
        });
        return test;
    }

    private static void waitForReload(
            GameTest test,
            String label,
            AtomicReference<CompletableFuture<Void>> reload
    ) {
        test.waitUntil(label + " resource reload completes", ctx -> {
            CompletableFuture<Void> future = reload.get();
            if (future == null || !future.isDone()) return false;

            future.join();
            return true;
        }).waitUntil(label + " reload overlay closes", ctx -> ctx.client().getOverlay() == null);
        ui(test, label + " MCSX publication completes", () -> {
        });
        test.waitTicks(20);
    }

    private static String rowText(Column rows, int index) {
        View child = rows.getChildAt(index);
        if (!(child instanceof ViewGroup row)) {
            throw new IllegalStateException("Keyed child " + index + " is not a row");
        }
        for (int childIndex = 0; childIndex < row.getChildCount(); childIndex++) {
            if (row.getChildAt(childIndex) instanceof TextField field) {
                return field.getText().toString();
            }
        }
        throw new IllegalStateException("Keyed child " + index + " has no text field");
    }

    private static int buttonBackground(Button button) {
        if (!(button.getBackground() instanceof ShapeDrawable background)) {
            throw new IllegalStateException("Button does not use an MCSX shape background");
        }
        return Color.toArgb(background.getColor().getDefaultColor());
    }
}
