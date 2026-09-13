package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.component.Text;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiTest {

    private static final Runnable NO_ACTION = () -> {
    };

    private final Context context = new HeadlessContext();

    @BeforeAll
    static void initializeModernUi() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
    }

    @Test
    void explicitLiteralFactoriesNeverInterpretTranslationKeys() {
        Ui ui = Ui.with(this.context);
        String literal = "gui.done";

        assertEquals(literal, ui.literalText(literal).getText().toString());
        assertEquals(literal, ui.literalHeading(literal).getText().toString());
        assertEquals(literal, ui.literalCopy(literal).getText().toString());
        assertEquals(literal, sectionTitle(ui.literalSection(literal)).getText().toString());
        assertEquals(literal, ui.literalField(literal).getHint().toString());
        assertEquals(literal, ui.literalCheckbox(literal).getText().toString());
        assertEquals(literal, ui.literalButton(literal, NO_ACTION).getText().toString());
        assertEquals(literal, ui.literalSecondaryButton(literal, NO_ACTION).getText().toString());
        assertEquals(literal, ui.literalQuietButton(literal, NO_ACTION).getText().toString());
        assertEquals(literal, ui.literalDangerButton(literal, NO_ACTION).getText().toString());
    }

    @Test
    void explicitTranslatableFactoriesUseMinecraftComponents() {
        Ui ui = Ui.with(this.context);
        String key = "gui.done";
        String translated = Component.translatable(key).getString();

        assertEquals(translated, ui.translatableText(key).getText().toString());
        assertEquals(translated, ui.translatableHeading(key).getText().toString());
        assertEquals(translated, ui.translatableCopy(key).getText().toString());
        assertEquals(translated, sectionTitle(ui.translatableSection(key)).getText().toString());
        assertEquals(translated, ui.translatableField(key).getHint().toString());
        assertEquals(translated, ui.translatableCheckbox(key).getText().toString());
        assertEquals(translated, ui.translatableButton(key, NO_ACTION).getText().toString());
        assertEquals(translated, ui.translatableSecondaryButton(key, NO_ACTION).getText().toString());
        assertEquals(translated, ui.translatableQuietButton(key, NO_ACTION).getText().toString());
        assertEquals(translated, ui.translatableDangerButton(key, NO_ACTION).getText().toString());
    }

    @Test
    void buttonFactoriesLeaveVisualVariantsToStylesheets() {
        Ui ui = Ui.with(this.context);
        Button primary = ui.literalButton("Primary", NO_ACTION);
        Button secondary = ui.literalSecondaryButton(
                "Secondary",
                NO_ACTION
        );
        Button quiet = ui.literalQuietButton("Quiet", NO_ACTION);

        assertEquals(primary.getTextStyle(), secondary.getTextStyle());
        assertEquals(primary.getTextStyle(), quiet.getTextStyle());
        assertEquals(0, primary.getElevation());
        assertEquals(0, secondary.getElevation());
        assertEquals(0, quiet.getElevation());
    }

    @Test
    void translatableFactoriesForwardComponentArguments() {
        String key = "options.percent_value";
        String expected = Component.translatable(key, 42).getString();

        assertEquals(expected, Ui.with(this.context).translatableText(key, 42).getText().toString());
    }

    @Test
    void translatableSectionAcceptsChildViews() {
        Ui ui = Ui.with(this.context);
        Text child = ui.literalText("Child");

        Column section = ui.translatableSection("gui.done", child);

        assertEquals(2, section.getChildCount());
        assertEquals(child, section.getChildAt(1));
    }

    @Test
    void containerFactoriesAcceptListsAsWellAsVarargs() {
        Ui ui = Ui.with(this.context);
        Text first = ui.literalText("First");
        Text second = ui.literalText("Second");

        Column column = ui.column(List.of(first, second));
        Column section = ui.literalSection("Title", List.of(ui.literalText("Child")));

        assertEquals(2, column.getChildCount());
        assertSame(first, column.getChildAt(0));
        assertSame(second, column.getChildAt(1));
        assertEquals(2, section.getChildCount());
        assertEquals(0, ui.row(List.of()).getChildCount());
    }

    @Test
    void taggingReturnsTheViewItTagged() {
        Ui ui = Ui.with(this.context);
        Text view = ui.literalText("Tagged");

        assertSame(view, Ui.tagged(view, "example.tag"));
        assertEquals("example.tag", view.getTag());
        assertSame(this.context, ui.context());
        assertThrows(NullPointerException.class, () -> Ui.tagged(null, "example.tag"));
    }

    private static Text sectionTitle(Column section) {
        return (Text) section.getChildAt(0);
    }

    private static final class HeadlessContext extends Context {

        private final Resources resources = Resources.getSystem();
        private final Resources.Theme theme = this.resources.newTheme();

        @Override
        public Resources getResources() {
            return this.resources;
        }

        @Override
        public void setTheme(ResourceId resourceId) {
        }

        @Override
        public Resources.Theme getTheme() {
            return this.theme;
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }
}
