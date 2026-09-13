package fr.lacaleche.glue.mcsx.client.component;

import dev.vfyjxf.taffy.style.FlexDirection;
import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.internal.property.Property;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheet;
import fr.lacaleche.glue.mcsx.client.style.StylesheetParser;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import icyllis.modernui.ModernUI;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.resources.Resources;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaffyLayoutTest {

    private final Context context = new HeadlessContext();

    @BeforeAll
    static void initializeModernUi() {
        if (ModernUI.getInstance() == null) {
            new ModernUI();
        }
    }

    @Test
    void allowsRaisedChildrenToDrawOutsideTheirLayoutBounds() {
        assertFalse(new TaffyLayout(this.context).getClipChildren());
        assertFalse(new TaffyLayout(this.context, FlexDirection.ROW).getClipChildren());
    }

    @Test
    void unstyledColumnUsesNeutralStartLayout() {
        TaffyLayout layout = new TaffyLayout(this.context);
        FixedView first = new FixedView(this.context, 40, 20);
        FixedView second = new FixedView(this.context, 60, 30);
        layout.addView(first, wrapContent());
        layout.addView(second, wrapContent());

        measureAndLayout(layout, exact(300), exact(200));

        assertBounds(first, 0, 0, 40, 20);
        assertBounds(second, 0, 20, 60, 50);
    }

    @Test
    void followsIndexedInsertionAndRemoval() {
        TaffyLayout layout = new TaffyLayout(this.context, FlexDirection.ROW);
        FixedView first = new FixedView(this.context, 20, 10);
        FixedView second = new FixedView(this.context, 30, 10);
        FixedView inserted = new FixedView(this.context, 10, 10);
        layout.addView(first, wrapContent());
        layout.addView(second, wrapContent());
        layout.addView(inserted, 1, wrapContent());

        measureAndLayout(layout, exact(200), exact(100));

        assertTrue(first.getLeft() < inserted.getLeft());
        assertTrue(inserted.getLeft() < second.getLeft());

        layout.removeView(inserted);
        measureAndLayout(layout, exact(200), exact(100));

        assertEquals(0, second.getLeft() - first.getRight());

        layout.addView(inserted, 0, wrapContent());
        measureAndLayout(layout, exact(200), exact(100));

        assertTrue(inserted.getLeft() < first.getLeft());
    }

    @Test
    void goneChildLeavesLayoutFlowAndCanReturn() {
        TaffyLayout layout = new TaffyLayout(this.context);
        FixedView first = new FixedView(this.context, 20, 10);
        FixedView second = new FixedView(this.context, 20, 10);
        layout.addView(first, wrapContent());
        layout.addView(second, wrapContent());

        first.setVisibility(View.INVISIBLE);
        measureAndLayout(layout, exact(100), exact(100));

        assertEquals(0, second.getTop() - first.getBottom());

        first.setVisibility(View.GONE);
        measureAndLayout(layout, exact(100), exact(100));

        assertBounds(first, 0, 0, 0, 0);
        assertBounds(second, 0, 0, 20, 10);

        first.setVisibility(View.VISIBLE);
        measureAndLayout(layout, exact(100), exact(100));

        assertEquals(0, second.getTop() - first.getBottom());
    }

    @Test
    void atMostRootWrapsContentWithinConstraint() {
        TaffyLayout layout = new TaffyLayout(this.context);
        FixedView child = new FixedView(this.context, 40, 20);
        layout.addView(child, wrapContent());

        measureAndLayout(layout, atMost(300), atMost(200));

        assertEquals(40, layout.getMeasuredWidth());
        assertEquals(20, layout.getMeasuredHeight());
        assertBounds(child, 0, 0, 40, 20);
    }

    @Test
    void unspecifiedRootWrapsContent() {
        TaffyLayout layout = new TaffyLayout(this.context);
        FixedView child = new FixedView(this.context, 40, 20);
        layout.addView(child, wrapContent());

        measureAndLayout(layout, unspecified(), unspecified());

        assertEquals(40, layout.getMeasuredWidth());
        assertEquals(20, layout.getMeasuredHeight());
        assertBounds(child, 0, 0, 40, 20);
    }

    @Test
    void repeatedMeasurementAndResizeKeepCorrectGeometry() {
        TaffyLayout layout = new TaffyLayout(this.context);
        FixedView child = new FixedView(this.context, 40, 20);
        layout.addView(child, wrapContent());

        measureAndLayout(layout, exact(200), exact(100));
        int left = child.getLeft();
        int top = child.getTop();

        measureAndLayout(layout, exact(200), exact(100));

        assertEquals(left, child.getLeft());
        assertEquals(top, child.getTop());
        assertEquals(40, child.getMeasuredWidth());
        assertEquals(20, child.getMeasuredHeight());

        measureAndLayout(layout, exact(300), exact(200));

        assertBounds(child, 0, 0, 40, 20);
    }

    @Test
    void exactWidthProducesWrappedIntrinsicHeight() {
        TaffyLayout layout = new TaffyLayout(this.context);
        WrappingView child = new WrappingView(this.context, 120, 10);
        layout.addView(child, new ViewGroup.LayoutParams(
                50,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        measureAndLayout(layout, exact(200), exact(120));

        assertEquals(50, child.getMeasuredWidth());
        assertEquals(30, child.getMeasuredHeight());
        assertEquals(MeasureSpec.EXACTLY, child.lastIntrinsicWidthMode);
    }

    @Test
    void matchParentWidthRewrapsAfterResize() {
        TaffyLayout layout = new TaffyLayout(this.context);
        WrappingView child = new WrappingView(this.context, 120, 10);
        layout.addView(child, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        measureAndLayout(layout, exact(100), exact(120));

        assertEquals(100, child.getMeasuredWidth());
        assertEquals(20, child.getMeasuredHeight());

        measureAndLayout(layout, exact(70), exact(120));

        assertEquals(70, child.getMeasuredWidth());
        assertEquals(20, child.getMeasuredHeight());
    }

    @Test
    void childLayoutRequestInvalidatesTaffyMeasurementCache() {
        TaffyLayout layout = new TaffyLayout(this.context);
        WrappingView child = new WrappingView(this.context, 60, 10);
        layout.addView(child, new ViewGroup.LayoutParams(
                30,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        measureAndLayout(layout, exact(100), exact(120));
        assertEquals(20, child.getMeasuredHeight());

        child.setContentWidth(120);
        measureAndLayout(layout, exact(100), exact(120));

        assertEquals(40, child.getMeasuredHeight());
    }

    @Test
    void modernUiTextRewrapsWhenTaffyWidthChanges() {
        TaffyLayout layout = new TaffyLayout(this.context);
        TextView text = new TextView(this.context);
        text.setText(
                "ModernUI text should become taller when Taffy gives it less horizontal space."
        );
        text.setTextSize(16);
        text.setHorizontallyScrolling(false);
        layout.addView(text, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        measureAndLayout(layout, exact(220), exact(200));
        int wideHeight = text.getMeasuredHeight();

        measureAndLayout(layout, exact(100), exact(200));

        assertEquals(100, text.getMeasuredWidth());
        assertTrue(text.getMeasuredHeight() > wideHeight);
    }

    @Test
    void editableLeafRetainsTextAndSelectionAcrossResize() {
        TaffyLayout layout = new TaffyLayout(this.context);
        EditText editor = new EditText(this.context);
        editor.setSingleLine(true);
        editor.setText("editable value");
        editor.setSelection(2, 8);
        layout.addView(editor, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        measureAndLayout(layout, exact(240), exact(120));
        measureAndLayout(layout, exact(140), exact(120));

        assertEquals("editable value", editor.getText().toString());
        assertEquals(2, editor.getSelectionStart());
        assertEquals(8, editor.getSelectionEnd());
        assertEquals(140, editor.getMeasuredWidth());
    }

    @Test
    void initialComponentsPreserveNativeViewBehavior() {
        Column column = new Column(this.context);
        Text text = new Text(this.context, "Counter: 0");
        ClickRecorder clicks = new ClickRecorder();
        Button button = new Button(this.context, "Increment", clicks::record);
        TextField field = new TextField(this.context);

        column.add(text, button, field);
        button.performClick();

        assertEquals("Counter: 0", text.getText().toString());
        assertEquals("Increment", button.getText().toString());
        assertEquals(1, clicks.count);
        assertEquals(1, field.getMaxLines());
        assertEquals(3, column.getChildCount());
    }

    @Test
    void fluentConfigurationPreservesConcreteComponentTypes() {
        Column column = new Column(this.context);
        Row row = new Row(this.context);
        Text text = new Text(this.context, "Tagged");
        Button button = new Button(this.context, "Tagged", () -> {
        });
        Checkbox checkbox = new Checkbox(this.context, "Tagged");
        TextField field = new TextField(this.context);

        assertSame(column, column.tag("column"));
        assertSame(row, row.tag("row"));
        assertSame(text, text.tag("text"));
        assertSame(button, button.tag("button"));
        assertSame(checkbox, checkbox.tag("checkbox"));
        assertSame(field, field.tag("field"));
        assertSame(field, field.text("Editable"));
        assertSame(column, column.visible(false));
        assertSame(row, row.visible(false));
        assertSame(text, text.visible(false));
        assertSame(button, button.visible(false));
        assertSame(checkbox, checkbox.visible(false));
        assertSame(field, field.visible(false));
        assertEquals("column", column.getTag());
        assertEquals("row", row.getTag());
        assertEquals("text", text.getTag());
        assertEquals("button", button.getTag());
        assertEquals("checkbox", checkbox.getTag());
        assertEquals("field", field.getTag());
        assertEquals("Editable", field.getText().toString());
        assertEquals(View.GONE, column.getVisibility());
        assertEquals(View.GONE, row.getVisibility());
        assertEquals(View.GONE, text.getVisibility());
        assertEquals(View.GONE, button.getVisibility());
        assertEquals(View.GONE, checkbox.getVisibility());
        assertEquals(View.GONE, field.getVisibility());
    }

    @Test
    void componentsBindVisibilityForTheirAttachedLifetime() {
        Signal<Boolean> visible = Signal.of(false);
        Column column = new Column(this.context).visible(visible);
        Row row = new Row(this.context).visible(visible);
        Text text = new Text(this.context, "Text").visible(visible);
        Button button = new Button(this.context, "Action", () -> {
        }).visible(visible);
        Checkbox checkbox = new Checkbox(this.context, "Choice").visible(visible);
        TextField field = new TextField(this.context).visible(visible);
        List<View> components = List.of(column, row, text, button, checkbox, field);

        components.forEach(component -> assertEquals(View.GONE, component.getVisibility()));
        components.forEach(Lifecycles::attach);

        visible.set(true);
        components.forEach(component -> assertEquals(View.VISIBLE, component.getVisibility()));
        components.forEach(Lifecycles::detach);

        visible.set(false);
        components.forEach(component -> assertEquals(View.VISIBLE, component.getVisibility()));
        components.forEach(Lifecycles::attach);
        components.forEach(component -> assertEquals(View.GONE, component.getVisibility()));
        components.forEach(Lifecycles::detach);
    }

    @Test
    void interactiveComponentsBindEnabledStateForTheirAttachedLifetime() {
        Signal<Boolean> enabled = Signal.of(false);
        Button button = new Button(this.context, "Action", () -> {
        });
        Checkbox checkbox = new Checkbox(this.context, "Choice");
        TextField field = new TextField(this.context);

        assertSame(button, button.enabled(enabled));
        assertSame(checkbox, checkbox.enabled(enabled));
        assertSame(field, field.enabled(enabled));
        assertFalse(button.isEnabled());
        assertFalse(checkbox.isEnabled());
        assertFalse(field.isEnabled());

        Lifecycles.attach(button);
        Lifecycles.attach(checkbox);
        Lifecycles.attach(field);
        enabled.set(true);
        assertTrue(button.isEnabled());
        assertTrue(checkbox.isEnabled());
        assertTrue(field.isEnabled());

        Lifecycles.detach(button);
        Lifecycles.detach(checkbox);
        Lifecycles.detach(field);
        enabled.set(false);
        assertTrue(button.isEnabled());
        assertTrue(checkbox.isEnabled());
        assertTrue(field.isEnabled());

        Lifecycles.attach(button);
        Lifecycles.attach(checkbox);
        Lifecycles.attach(field);
        assertFalse(button.isEnabled());
        assertFalse(checkbox.isEnabled());
        assertFalse(field.isEnabled());
        Lifecycles.detach(button);
        Lifecycles.detach(checkbox);
        Lifecycles.detach(field);
    }

    @Test
    void textFieldSubmitsOnUnmodifiedEnterRelease() {
        ClickRecorder submissions = new ClickRecorder();
        TextField field = new TextField(this.context).onSubmit(submissions::record);

        assertFalse(field.onKeyUp(KeyEvent.KEY_A, keyUp(KeyEvent.KEY_A, 0)));
        assertFalse(field.onKeyUp(
                KeyEvent.KEY_ENTER,
                keyUp(KeyEvent.KEY_ENTER, KeyEvent.META_SHIFT_ON)
        ));
        assertFalse(field.onKeyUp(
                KeyEvent.KEY_ENTER,
                KeyEvent.obtain(
                        0,
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEY_ENTER,
                        0,
                        0,
                        0,
                        KeyEvent.FLAG_CANCELED
                )
        ));
        assertTrue(field.onKeyUp(KeyEvent.KEY_ENTER, keyUp(KeyEvent.KEY_ENTER, 0)));
        assertTrue(field.onKeyUp(KeyEvent.KEY_KP_ENTER, keyUp(KeyEvent.KEY_KP_ENTER, 0)));
        assertEquals(2, submissions.count);

        field.setEnabled(false);
        field.onKeyUp(KeyEvent.KEY_ENTER, keyUp(KeyEvent.KEY_ENTER, 0));
        assertEquals(2, submissions.count);
    }

    @Test
    void textFieldFocusCallbackDoesNotReplaceNativeListener() {
        List<Boolean> mcsxChanges = new ArrayList<>();
        List<Boolean> nativeChanges = new ArrayList<>();
        TaffyLayout root = new TaffyLayout(this.context);
        TextField field = new TextField(this.context).onFocusChanged(mcsxChanges::add);
        field.setOnFocusChangeListener((view, focused) -> nativeChanges.add(focused));
        root.addView(field);
        Lifecycles.attach(root);

        assertTrue(field.requestFocus());
        field.clearFocus();

        assertEquals(nativeChanges, mcsxChanges);
        assertTrue(mcsxChanges.get(0));
        assertFalse(mcsxChanges.get(1));
    }

    @Test
    void checkboxBindingSynchronizesNativeAndReactiveChanges() {
        Signal<Boolean> value = Signal.of(false);
        Checkbox checkbox = new Checkbox(this.context, "Choice").checked(value);
        List<Integer> nativeStates = new ArrayList<>();
        checkbox.setOnCheckedStateChangeListener((view, state) -> nativeStates.add(state));

        assertFalse(checkbox.isChecked());

        Lifecycles.attach(checkbox);
        checkbox.performClick();
        assertTrue(value.get());
        assertEquals(List.of(Checkbox.STATE_CHECKED), nativeStates);

        value.set(false);
        assertFalse(checkbox.isChecked());

        Lifecycles.detach(checkbox);
        value.set(true);
        assertFalse(checkbox.isChecked());

        Lifecycles.attach(checkbox);
        assertTrue(checkbox.isChecked());
        Lifecycles.detach(checkbox);
    }

    @Test
    void booleanCheckboxBindingNormalizesIndeterminateState() {
        Signal<Boolean> value = Signal.of(false);
        Checkbox checkbox = new Checkbox(this.context, "Choice").checked(value);
        Lifecycles.attach(checkbox);

        checkbox.setCheckedState(Checkbox.STATE_INDETERMINATE);

        assertEquals(Checkbox.STATE_UNCHECKED, checkbox.getCheckedState());
        assertFalse(value.get());
        Lifecycles.detach(checkbox);
    }

    @Test
    void checkboxIsFocusableAndSpaceUsesNativeClickPath() {
        Signal<Boolean> value = Signal.of(false);
        TaffyLayout root = new TaffyLayout(this.context);
        Checkbox checkbox = new Checkbox(this.context, "Choice").checked(value);
        root.addView(checkbox);
        Lifecycles.attach(root);
        measureAndLayout(root, exact(200), exact(100));

        assertTrue(checkbox.isFocusable());
        assertTrue(checkbox.isFocusableInTouchMode());
        assertTrue(checkbox.requestFocus());
        assertTrue(checkbox.onKeyDown(KeyEvent.KEY_SPACE, keyDown(KeyEvent.KEY_SPACE)));
        assertTrue(checkbox.isPressed());
        assertFalse(value.get());

        assertTrue(checkbox.onKeyUp(KeyEvent.KEY_SPACE, keyUp(KeyEvent.KEY_SPACE, 0)));
        assertFalse(checkbox.isPressed());
        assertTrue(checkbox.isChecked());
        assertTrue(value.get());
    }

    @Test
    void disabledCheckboxConsumesSpaceWithoutToggling() {
        Signal<Boolean> value = Signal.of(false);
        Checkbox checkbox = new Checkbox(this.context, "Choice")
                .checked(value)
                .enabled(false);
        Lifecycles.attach(checkbox);

        assertTrue(checkbox.onKeyDown(KeyEvent.KEY_SPACE, keyDown(KeyEvent.KEY_SPACE)));
        assertTrue(checkbox.onKeyUp(KeyEvent.KEY_SPACE, keyUp(KeyEvent.KEY_SPACE, 0)));
        assertFalse(checkbox.isPressed());
        assertFalse(checkbox.isChecked());
        assertFalse(value.get());
        Lifecycles.detach(checkbox);
    }

    @Test
    void canceledSpaceReleaseClearsPressWithoutTogglingCheckbox() {
        Signal<Boolean> value = Signal.of(false);
        Checkbox checkbox = new Checkbox(this.context, "Choice").checked(value);
        Lifecycles.attach(checkbox);

        assertTrue(checkbox.onKeyDown(KeyEvent.KEY_SPACE, keyDown(KeyEvent.KEY_SPACE)));
        assertTrue(checkbox.isPressed());
        assertTrue(checkbox.onKeyUp(
                KeyEvent.KEY_SPACE,
                KeyEvent.obtain(
                        0,
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEY_SPACE,
                        0,
                        0,
                        0,
                        KeyEvent.FLAG_CANCELED
                )
        ));
        assertFalse(checkbox.isPressed());
        assertFalse(checkbox.isChecked());
        assertFalse(value.get());
        Lifecycles.detach(checkbox);
    }

    @Test
    void disablingCheckboxDuringSpacePressClearsPressWithoutToggling() {
        Signal<Boolean> value = Signal.of(false);
        Checkbox checkbox = new Checkbox(this.context, "Choice").checked(value);
        Lifecycles.attach(checkbox);

        assertTrue(checkbox.onKeyDown(KeyEvent.KEY_SPACE, keyDown(KeyEvent.KEY_SPACE)));
        assertTrue(checkbox.isPressed());
        checkbox.setEnabled(false);
        assertTrue(checkbox.onKeyUp(KeyEvent.KEY_SPACE, keyUp(KeyEvent.KEY_SPACE, 0)));
        assertFalse(checkbox.isPressed());
        assertFalse(checkbox.isChecked());
        assertFalse(value.get());
        Lifecycles.detach(checkbox);
    }

    @Test
    void checkboxIndicatorTracksThemeAndPreservesStateColors() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout root = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Theme> theme = Signal.of(Themes.dark());
        Checkbox checkbox = new Checkbox(this.context, "Choice");
        root.provideTheme(theme);
        root.provideStylesheet(Stylesheet.empty());
        root.addView(checkbox);
        Lifecycles.attach(root);
        uiPoster.runAll();

        ColorStateList dark = checkbox.getButtonTintList();
        assertSame(Themes.dark().get(ThemeTokens.CHECKBOX_INDICATOR), dark);
        assertTrue(dark.isStateful());
        int checked = Color.toArgb(dark.getColorForState(
                new int[]{
                        icyllis.modernui.R.attr.state_enabled,
                        icyllis.modernui.R.attr.state_checked
                },
                Color.RED_SRGB
        ));
        int unchecked = Color.toArgb(dark.getColorForState(
                new int[]{icyllis.modernui.R.attr.state_enabled},
                Color.RED_SRGB
        ));
        int disabled = Color.toArgb(dark.getColorForState(new int[]{}, Color.RED_SRGB));
        assertNotEquals(checked, unchecked);
        assertNotEquals(unchecked, disabled);

        theme.set(Themes.light());
        uiPoster.runAll();

        assertSame(Themes.light().get(ThemeTokens.CHECKBOX_INDICATOR), checkbox.getButtonTintList());
        assertNotSame(dark, checkbox.getButtonTintList());
    }

    @Test
    void consumerStylesheetCanRetainNativeCheckboxIndicator() {
        TaffyLayout root = new TaffyLayout(this.context);
        Checkbox checkbox = new Checkbox(this.context, "Choice");
        ColorStateList nativeIndicator = checkbox.getButtonTintList();
        root.provideStylesheet(this.stylesheet("Checkbox { indicator-tint: native; }"));
        root.addView(checkbox);

        Lifecycles.attach(root);

        assertSame(nativeIndicator, checkbox.getButtonTintList());
    }

    @Test
    void checkboxRejectsConflictingCheckedSources() {
        Checkbox bound = new Checkbox(this.context, "Bound").checked(Signal.of(false));
        Checkbox direct = new Checkbox(this.context, "Direct").checked(false);

        assertThrows(IllegalStateException.class, () -> bound.checked(Signal.of(true)));
        assertThrows(IllegalStateException.class, () -> bound.checked(true));
        assertThrows(IllegalStateException.class, () -> direct.checked(Signal.of(true)));
    }

    @Test
    void textFieldBindingSynchronizesNativeAndReactiveChanges() {
        Signal<String> value = Signal.of("Initial");
        TextField field = new TextField(this.context).text(value);

        assertEquals("Initial", field.getText().toString());

        field.mountTextBinding();
        field.setText("Native edit");
        assertEquals("Native edit", value.get());

        field.setSelection(3);
        value.set("External update");
        assertEquals("External update", field.getText().toString());
        assertEquals(3, field.getSelectionStart());

        field.unmountTextBinding();
        value.set("Detached value");
        assertEquals("External update", field.getText().toString());
        field.setText("Detached edit");
        assertEquals("Detached value", value.get());

        field.mountTextBinding();
        assertEquals("Detached value", field.getText().toString());
        field.setText("Reattached edit");
        assertEquals("Reattached edit", value.get());
        field.unmountTextBinding();
    }

    @Test
    void textFieldRejectsMultipleTextSignals() {
        TextField field = new TextField(this.context).text(Signal.of("First"));
        TextField direct = new TextField(this.context).text("Direct");

        assertThrows(IllegalStateException.class, () -> field.text(Signal.of("Second")));
        assertThrows(IllegalStateException.class, () -> field.text("Direct"));
        assertThrows(IllegalStateException.class, () -> direct.text(Signal.of("Second")));
    }

    @Test
    void minecraftComponentsPopulateLocalizedTextProperties() {
        Ui ui = Ui.with(this.context);
        Text text = ui.heading(Component.literal("Localized heading"));
        Button button = ui.button(Component.literal("Localized button"), () -> {
        });
        Checkbox checkbox = ui.checkbox(Component.literal("Localized checkbox"));
        TextField field = ui.field(Component.literal("Localized hint"));

        assertEquals("Localized heading", text.getText().toString());
        assertEquals("Localized button", button.getText().toString());
        assertEquals("Localized checkbox", checkbox.getText().toString());
        assertEquals("Localized hint", field.getHint().toString());
    }

    @Test
    void nestedRowMeasuresAsOpaqueColumnChild() {
        Column column = new Column(this.context);
        Row row = new Row(this.context);
        FixedView first = new FixedView(this.context, 20, 10);
        FixedView second = new FixedView(this.context, 30, 10);
        row.add(first, second);
        column.add(row);

        measureAndLayout(column, exact(300), exact(200));

        assertEquals(50, row.getMeasuredWidth());
        assertEquals(10, row.getMeasuredHeight());
        assertTrue(first.getLeft() < second.getLeft());
        assertEquals(0, second.getLeft() - first.getRight());
    }

    @Test
    void nestedChildLayoutRequestRecomputesBothLayoutRoots() {
        Column column = new Column(this.context);
        Row row = new Row(this.context);
        WrappingView child = new WrappingView(this.context, 60, 10);
        row.addView(child, new ViewGroup.LayoutParams(
                30,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        column.add(row);

        measureAndLayout(column, exact(200), exact(160));
        int initialHeight = row.getMeasuredHeight();

        child.setContentWidth(120);
        measureAndLayout(column, exact(200), exact(160));

        assertTrue(row.getMeasuredHeight() > initialHeight);
        assertEquals(40, child.getMeasuredHeight());
    }

    @Test
    void colorUpdatesCoalesceWithoutRunningTaffy() {
        TestUiPoster uiPoster = new TestUiPoster();
        List<RuntimeException> failures = new ArrayList<>();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                failures::add
        );
        Signal<Integer> color = Signal.of(0xff000000);
        Text text = new Text(this.context, "Paint only").color(color);
        layout.addChildren(text);
        Lifecycles.attach(layout);
        measureAndLayout(layout, exact(200), exact(100));
        int layoutCount = layout.layoutComputationCount();
        int propertyCount = layout.propertyApplicationCount();

        color.set(0xffff0000);
        color.set(0xff00ff00);
        color.set(0xff0000ff);

        assertEquals(1, uiPoster.size());
        uiPoster.runAll();

        assertEquals(0xff0000ff, Color.toArgb(text.getCurrentTextColor()));
        assertEquals(layoutCount, layout.layoutComputationCount());
        assertEquals(propertyCount + 1, layout.propertyApplicationCount());
        assertFalse(text.isLayoutRequested());
        assertTrue(failures.isEmpty());

        color.set(0xffff0000);
        color.set(0xff00ff00);
        color.set(0xff0000ff);
        uiPoster.runAll();

        assertEquals(0xff0000ff, Color.toArgb(text.getCurrentTextColor()));
        assertEquals(propertyCount + 1, layout.propertyApplicationCount());
    }

    @Test
    void themeTracksOnlyUsedPaintTokenWithoutRunningTaffy() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Theme> theme = Signal.of(Themes.dark());
        Text text = new Text(this.context, "Themed text");
        layout.provideTheme(theme);
        layout.provideStylesheet(Stylesheet.empty());
        layout.addView(text);
        Lifecycles.attach(layout);
        measureAndLayout(layout, exact(200), exact(100));
        int layoutCount = layout.layoutComputationCount();

        Theme accentOnly = Theme.builder()
                .set(ThemeTokens.TEXT_PRIMARY, Themes.dark().get(ThemeTokens.TEXT_PRIMARY))
                .set(ThemeTokens.ACCENT, Color.rgb(240, 80, 120))
                .build();
        theme.set(accentOnly);

        assertEquals(1, layout.themeDependencyCount());
        assertEquals(0, uiPoster.size());

        theme.set(Themes.light());
        assertEquals(1, uiPoster.size());
        uiPoster.runAll();

        assertEquals(
                Themes.light().get(ThemeTokens.TEXT_PRIMARY).intValue(),
                Color.toArgb(text.getCurrentTextColor())
        );
        assertEquals(layoutCount, layout.layoutComputationCount());
        assertFalse(text.isLayoutRequested());

        Lifecycles.detach(text);
        assertEquals(0, layout.themeDependencyCount());
    }

    @Test
    void dimensionThemeTokenRequestsLayout() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Theme> theme = Signal.of(Themes.dark());
        Button button = new Button(this.context, "Themed button", () -> {
        });
        layout.provideTheme(theme);
        layout.provideStylesheet(Stylesheet.empty());
        layout.addView(button);
        Lifecycles.attach(layout);
        measureAndLayout(layout, exact(200), exact(100));
        int layoutCount = layout.layoutComputationCount();

        Theme taller = Theme.builder()
                .set(ThemeTokens.CONTROL_HEIGHT, Themes.mcsx().get(ThemeTokens.CONTROL_HEIGHT) + 8)
                .build();
        theme.set(taller);
        assertEquals(1, uiPoster.size());
        uiPoster.runAll();

        assertEquals(taller.get(ThemeTokens.CONTROL_HEIGHT).intValue(), button.getMinimumHeight());
        assertTrue(button.isLayoutRequested());
        assertEquals(layoutCount, layout.layoutComputationCount());

        measureAndLayout(layout, exact(200), exact(100));
        assertEquals(layoutCount + 1, layout.layoutComputationCount());
    }

    @Test
    void themeUpdatesEveryDependencyBeforeRethrowingListenerFailures() {
        Token<Integer> first = Token.of("first", 0);
        Token<Integer> second = Token.of("second", 0);
        Signal<Theme> theme = Signal.of(Theme.builder().set(first, 1).set(second, 2).build());
        ThemeScope scope = new ThemeScope(theme);
        Value<Integer> firstValue = scope.value(first);
        Value<Integer> secondValue = scope.value(second);
        firstValue.subscribe(value -> {
            throw new IllegalStateException("first listener failed");
        });
        secondValue.subscribe(value -> {
            throw new IllegalStateException("second listener failed");
        });
        scope.mount();

        Theme replacement = Theme.builder().set(first, 10).set(second, 20).build();

        assertThrows(IllegalStateException.class, () -> theme.set(replacement));
        assertEquals(10, firstValue.get());
        assertEquals(20, secondValue.get());
    }

    @Test
    void propertyMountRollsBackSubscriptionsAfterFailure() {
        Text text = new Text(this.context, "Rollback");
        PropertyScope<Text> properties = new PropertyScope<>(text);
        TrackableValue<Integer> color = new TrackableValue<>(0xff000000, false);
        TrackableValue<Integer> size = new TrackableValue<>(16, true);
        properties.bind(TextProperties.COLOR, color);
        properties.bind(TextProperties.TEXT_SIZE, size);

        assertThrows(IllegalStateException.class, properties::mount);
        assertEquals(0, color.activeSubscriptions());
        assertEquals(0, size.activeSubscriptions());

        size.setFailSubscribe(false);
        properties.mount();
        assertEquals(1, color.activeSubscriptions());
        assertEquals(1, size.activeSubscriptions());

        properties.unmount();
        assertEquals(0, color.activeSubscriptions());
        assertEquals(0, size.activeSubscriptions());
    }

    @Test
    void styleMountRollsBackStateSubscriptionsAfterFailure() {
        Text text = new Text(this.context, "Rollback");
        TrackableValue<Boolean> selected = new TrackableValue<>(false, false);
        TrackableValue<Boolean> active = new TrackableValue<>(false, true);
        text.state("selected", selected);
        text.state("active", active);

        assertThrows(IllegalStateException.class, text.styleMetadata()::mount);
        assertEquals(0, selected.activeSubscriptions());
        assertEquals(0, active.activeSubscriptions());

        active.setFailSubscribe(false);
        text.styleMetadata().mount();
        assertEquals(1, selected.activeSubscriptions());
        assertEquals(1, active.activeSubscriptions());

        text.styleMetadata().unmount();
        assertEquals(0, selected.activeSubscriptions());
        assertEquals(0, active.activeSubscriptions());
    }

    @Test
    void separateRootsResolveSeparateThemes() {
        TestUiPoster darkPoster = new TestUiPoster();
        TestUiPoster lightPoster = new TestUiPoster();
        TaffyLayout darkRoot = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                darkPoster::post,
                exception -> {
                    throw exception;
                }
        );
        TaffyLayout lightRoot = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                lightPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Text darkText = new Text(this.context, "Dark");
        Text lightText = new Text(this.context, "Light");
        darkRoot.provideTheme(Themes.dark());
        lightRoot.provideTheme(Themes.light());
        darkRoot.provideStylesheet(Stylesheet.empty());
        lightRoot.provideStylesheet(Stylesheet.empty());
        darkRoot.addView(darkText);
        lightRoot.addView(lightText);
        Lifecycles.attach(darkRoot);
        Lifecycles.attach(lightRoot);
        darkPoster.runAll();
        lightPoster.runAll();

        assertEquals(
                Themes.dark().get(ThemeTokens.TEXT_PRIMARY).intValue(),
                Color.toArgb(darkText.getCurrentTextColor())
        );
        assertEquals(
                Themes.light().get(ThemeTokens.TEXT_PRIMARY).intValue(),
                Color.toArgb(lightText.getCurrentTextColor())
        );
    }

    @Test
    void stylesheetCascadeHonorsStateSpecificityAndLocalOrigin() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Boolean> active = Signal.of(false);
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet("""
                Text { color: #111111; }
                .label { color: #222222; }
                .label:active { color: #333333; }
                """));
        Text text = new Text(this.context, "Styled").classes("label").state("active", active);
        layout.provideStylesheet(stylesheet);
        layout.addView(text);
        Lifecycles.attach(layout);

        assertEquals(0xff222222, Color.toArgb(text.getCurrentTextColor()));

        active.set(true);
        uiPoster.runAll();
        assertEquals(0xff333333, Color.toArgb(text.getCurrentTextColor()));

        text.color(0xff444444);
        uiPoster.runAll();
        stylesheet.set(this.stylesheet(".label:active { color: #555555; }"));
        assertEquals(0, uiPoster.size());
        assertEquals(0xff444444, Color.toArgb(text.getCurrentTextColor()));
    }

    @Test
    void consumerStylesheetOutranksMoreSpecificDefaultRules() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Button button = new Button(this.context, "Secondary", () -> {
        }).classes("secondary");
        layout.provideStylesheet(Stylesheets.withDefaults(this.stylesheet("""
                .secondary { color: #123456; }
                .secondary:hover { color: #654321; }
                """)));
        layout.addView(button);
        Lifecycles.attach(layout);

        assertEquals(0xff123456, Color.toArgb(button.getCurrentTextColor()));

        button.setHovered(true);
        uiPoster.runAll();

        assertEquals(0xff654321, Color.toArgb(button.getCurrentTextColor()));
    }

    @Test
    void quietButtonIsFlatUntilHover() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Button button = new Button(this.context, "Quiet", () -> {
        }).classes("quiet");
        layout.provideStylesheet(Stylesheet.empty());
        layout.addView(button);
        Lifecycles.attach(layout);

        assertEquals(Themes.mcsx().get(ThemeTokens.TEXT_MUTED).intValue(),
                Color.toArgb(button.getCurrentTextColor()));
        assertEquals(Color.TRANSPARENT, Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(0, button.getElevation());

        button.setHovered(true);
        uiPoster.runAll();

        assertEquals(Themes.mcsx().get(ThemeTokens.SURFACE_QUIET_HOVER).intValue(), Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
    }

    @Test
    void latchedButtonUsesPressedSurfaceWithoutElevation() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Boolean> pressed = Signal.of(false);
        Signal<Boolean> enabled = Signal.of(true);
        Button button = new Button(this.context, "Latched", () -> {
        }).classes("secondary").pressed(pressed).enabled(enabled);
        layout.provideStylesheet(Stylesheet.empty());
        layout.addView(button);
        Lifecycles.attach(layout);

        assertTrue(button.getElevation() > 0);
        assertEquals(Themes.mcsx().get(ThemeTokens.SURFACE_RAISED).intValue(), Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));

        pressed.set(true);
        uiPoster.runAll();

        assertEquals(0, button.getElevation());
        assertEquals(Themes.mcsx().get(ThemeTokens.SURFACE_PRESSED).intValue(), Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));

        button.setHovered(true);
        enabled.set(false);
        uiPoster.runAll();

        assertEquals(0, button.getElevation());
        assertEquals(Themes.mcsx().get(ThemeTokens.SURFACE_DISABLED).intValue(), Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));

        pressed.set(false);
        enabled.set(true);
        uiPoster.runAll();

        assertTrue(button.getElevation() > 0);
        button.setPressed(true);
        uiPoster.runAll();
        assertEquals(0, button.getElevation());
        assertEquals(Themes.mcsx().get(ThemeTokens.SURFACE_PRESSED).intValue(), Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
    }

    @Test
    void laterDeclarationInSameRuleWins() {
        TaffyLayout layout = new TaffyLayout(this.context);
        Text text = new Text(this.context, "Styled");
        layout.provideStylesheet(this.stylesheet("""
                Text {
                    color: #111111;
                    color: #222222;
                }
                """));
        layout.addView(text);

        Lifecycles.attach(layout);

        assertEquals(0xff222222, Color.toArgb(text.getCurrentTextColor()));
    }

    @Test
    void removingConsumerRuleRestoresBuiltInTokenDeclaration() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet("Text { color: #336699; }"));
        Text text = new Text(this.context, "Styled");
        layout.provideTheme(Themes.dark());
        layout.provideStylesheet(stylesheet);
        layout.addView(text);
        Lifecycles.attach(layout);

        assertEquals(0xff336699, Color.toArgb(text.getCurrentTextColor()));

        stylesheet.set(Stylesheet.empty());
        uiPoster.runAll();

        assertEquals(
                Themes.dark().get(ThemeTokens.TEXT_PRIMARY).intValue(),
                Color.toArgb(text.getCurrentTextColor())
        );
    }

    @Test
    void stylesheetReloadAvoidsLayoutForPaintOnlyChanges() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet("Text { color: #111111; }"));
        Text text = new Text(this.context, "Styled");
        layout.provideStylesheet(stylesheet);
        layout.addView(text);
        Lifecycles.attach(layout);
        measureAndLayout(layout, exact(200), exact(100));
        int layoutCount = layout.layoutComputationCount();

        stylesheet.set(this.stylesheet("Text { color: #111111; }"));
        assertEquals(0, uiPoster.size());

        stylesheet.set(this.stylesheet("Text { color: #222222; }"));
        uiPoster.runAll();

        assertEquals(layoutCount, layout.layoutComputationCount());
        assertFalse(text.isLayoutRequested());

        stylesheet.set(this.stylesheet("Text { color: #222222; text-size: 30px; }"));
        uiPoster.runAll();

        assertTrue(text.isLayoutRequested());
        assertEquals(layoutCount, layout.layoutComputationCount());
        measureAndLayout(layout, exact(200), exact(100));
        assertEquals(layoutCount + 1, layout.layoutComputationCount());
    }

    @Test
    void stylesheetLayoutChangeAppliesUnderZeroWidthConstraints() {
        TaffyLayout root = new TaffyLayout(this.context);
        Column panel = new Column(this.context).classes("panel");
        FixedView child = new FixedView(this.context, 20, 10);
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet(".panel { padding: 4px; }"));
        panel.add(child);
        root.addView(panel);
        root.provideStylesheet(stylesheet);
        Lifecycles.attach(root);

        // AT_MOST 0 is bit-identical to Integer.MIN_VALUE, which must not read as "never measured".
        measureAndLayout(root, atMost(0), atMost(0));
        assertEquals(4, child.getLeft());

        stylesheet.set(this.stylesheet(".panel { padding: 40px; }"));
        measureAndLayout(root, atMost(0), atMost(0));

        assertEquals(40, child.getLeft());
    }

    @Test
    void stylesheetReloadPreservesTextFieldEditingState() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet("""
                TextField { background: #202020; text-size: 16px; }
                """));
        TextField field = new TextField(this.context);
        layout.provideStylesheet(stylesheet);
        layout.addView(field);
        Lifecycles.attach(layout);
        field.setText("persistent text");
        field.setSelection(5);
        assertTrue(field.requestFocus());

        stylesheet.set(this.stylesheet("""
                TextField { background: #404040; text-size: 20px; }
                """));
        uiPoster.runAll();

        assertSame(field, layout.getChildAt(0));
        assertEquals("persistent text", field.getText().toString());
        assertEquals(5, field.getSelectionStart());
        assertTrue(field.hasFocus());
    }

    @Test
    void leavingStylesheetScopeRestoresComponentBaseline() {
        TaffyLayout layout = new TaffyLayout(this.context);
        Text text = new Text(this.context, "Styled");
        int baselineSize = Math.round(text.getTextSize());
        layout.provideStylesheet(this.stylesheet("Text { text-size: 30px; }"));
        layout.addView(text);
        Lifecycles.attach(layout);
        assertEquals(30, Math.round(text.getTextSize()));

        Lifecycles.detach(text);

        assertEquals(baselineSize, Math.round(text.getTextSize()));
    }

    @Test
    void textInteractionStateRecomputesStylesheet() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Text text = new Text(this.context, "Hover me");
        layout.provideStylesheet(this.stylesheet("Text:hover { color: #cc3344; }"));
        layout.addView(text);
        Lifecycles.attach(layout);

        text.setHovered(true);
        uiPoster.runAll();

        assertEquals(0xffcc3344, Color.toArgb(text.getCurrentTextColor()));
    }

    @Test
    void checkboxCheckedStateRecomputesStylesheet() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Checkbox checkbox = new Checkbox(this.context, "Choice");
        layout.provideStylesheet(this.stylesheet("""
                Checkbox { color: #223344; }
                Checkbox:checked { color: #cc3344; }
                """));
        layout.addView(checkbox);
        Lifecycles.attach(layout);
        assertEquals(0xff223344, Color.toArgb(checkbox.getCurrentTextColor()));

        checkbox.setChecked(true);
        uiPoster.runAll();

        assertEquals(0xffcc3344, Color.toArgb(checkbox.getCurrentTextColor()));
    }

    @Test
    void checkboxDoesNotMatchButtonTypeSelector() {
        TaffyLayout layout = new TaffyLayout(this.context);
        Checkbox checkbox = new Checkbox(this.context, "Choice");
        layout.provideStylesheet(this.stylesheet("Button { color: #cc3344; }"));
        layout.addView(checkbox);
        Lifecycles.attach(layout);

        assertEquals(
                Themes.mcsx().get(ThemeTokens.TEXT_MUTED).intValue(),
                Color.toArgb(checkbox.getCurrentTextColor())
        );
    }

    @Test
    void partAndDirectChildSelectorsMatchWithoutRebuildingViews() {
        TaffyLayout root = new TaffyLayout(this.context);
        Column panel = new Column(this.context);
        Row row = new Row(this.context).classes("item-row");
        Text title = new Text(this.context, "Title").part("title");
        Text child = new Text(this.context, "Child");
        panel.add(title, row);
        row.add(child);
        root.addView(panel);
        root.provideStylesheet(this.stylesheet("""
                Column::title { color: #123456; }
                .item-row > Text { color: #654321; }
                """));

        Lifecycles.attach(root);

        assertEquals(0xff123456, Color.toArgb(title.getCurrentTextColor()));
        assertEquals(0xff654321, Color.toArgb(child.getCurrentTextColor()));
        assertSame(title, panel.getChildAt(0));
        assertSame(child, row.getChildAt(0));
    }

    @Test
    void directChildSelectorTraversesUnstyledContainers() {
        TaffyLayout root = new TaffyLayout(this.context);
        Column screen = new Column(this.context).classes("ui-screen");
        ScrollView scroll = new ScrollView(this.context);
        Text text = new Text(this.context, "Scrolled");
        scroll.addView(text);
        screen.add(scroll);
        root.addView(screen);
        root.provideStylesheet(this.stylesheet(".ui-screen > Text { color: #654321; }"));

        Lifecycles.attach(root);

        assertEquals(0xff654321, Color.toArgb(text.getCurrentTextColor()));
    }

    @Test
    void nestedStylesheetRootShieldsItsSubtree() {
        TaffyLayout outer = new TaffyLayout(this.context);
        Column nested = new Column(this.context);
        Text text = new Text(this.context, "Nested");
        nested.add(text);
        nested.stylesheet(this.stylesheet("Text { color: #223344; }"));
        outer.addView(nested);
        outer.provideStylesheet(this.stylesheet("Text { color: #aabbcc; }"));

        Lifecycles.attach(outer);

        assertEquals(0xff223344, Color.toArgb(text.getCurrentTextColor()));
    }

    @Test
    void rawStylesheetOmitsDefaultsAndShieldsItsSubtree() {
        TaffyLayout outer = new TaffyLayout(this.context);
        Column nested = new Column(this.context);
        Text text = new Text(this.context, "Raw text");
        Button button = new Button(this.context, "Raw", () -> {
        });
        TextField field = new TextField(this.context);
        Checkbox checkbox = new Checkbox(this.context, "Raw check");
        long nativeTextColor = text.getCurrentTextColor();
        long nativeFieldColor = field.getCurrentTextColor();
        long nativeCheckboxColor = checkbox.getCurrentTextColor();
        ColorStateList nativeIndicator = checkbox.getButtonTintList();
        nested.add(text, button, field, checkbox);
        nested.rawStylesheet(Stylesheet.empty());
        outer.addView(nested);
        outer.provideStylesheet(Stylesheet.empty());

        Lifecycles.attach(outer);

        assertEquals(0, button.getElevation());
        assertEquals(Color.TRANSPARENT, Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(Color.TRANSPARENT, Color.toArgb(
                ((ShapeDrawable) nested.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(Color.TRANSPARENT, Color.toArgb(
                ((ShapeDrawable) field.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(nativeTextColor, text.getCurrentTextColor());
        assertEquals(nativeFieldColor, field.getCurrentTextColor());
        assertEquals(nativeCheckboxColor, checkbox.getCurrentTextColor());
        assertSame(nativeIndicator, checkbox.getButtonTintList());
        assertNull(button.styleMetadata().computedValue("background"));
        assertNull(button.styleMetadata().computedValue("elevation"));
    }

    @Test
    void rawStylesheetAppliesOnlySuppliedRules() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout root = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Button button = new Button(this.context, "Raw", () -> {
        });
        long nativeTextColor = button.getCurrentTextColor();
        Signal<Stylesheet> stylesheet = Signal.of(this.stylesheet("""
                Button {
                    background: #123456;
                    elevation: 4px;
                }
                """));
        root.addView(button);
        root.provideRawStylesheet(stylesheet);

        Lifecycles.attach(root);

        assertEquals(0xff123456, Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(4, button.getElevation());
        assertEquals(nativeTextColor, button.getCurrentTextColor());
        assertNull(button.styleMetadata().computedValue("font-weight"));

        stylesheet.set(Stylesheet.empty());
        uiPoster.runAll();

        assertEquals(Color.TRANSPARENT, Color.toArgb(
                ((ShapeDrawable) button.getBackground()).getColor().getDefaultColor()
        ));
        assertEquals(0, button.getElevation());
    }

    @Test
    void rawStylesheetCanReplaceImplicitEmptyDefaultScope() {
        Column root = new Column(this.context);
        Button button = new Button(this.context, "Raw", () -> {
        });
        root.add(button);
        root.stylesheet(Stylesheet.empty());

        root.rawStylesheet(Stylesheet.empty());
        Lifecycles.attach(root);

        assertEquals(0, button.getElevation());
        assertNull(button.styleMetadata().computedValue("background"));
    }

    @Test
    void stylesheetControlsResponsiveContainerLayout() {
        TaffyLayout root = new TaffyLayout(this.context);
        Column panel = new Column(this.context).classes("panel");
        FixedView first = new FixedView(this.context, 80, 20);
        FixedView second = new FixedView(this.context, 100, 30);
        panel.add(first, second);
        root.addView(panel);
        root.provideStylesheet(this.stylesheet("""
                .panel {
                    width: 100%;
                    max-width: 600px;
                    padding: 20px;
                    gap: 5px;
                    align-items: stretch;
                    justify-content: start;
                }
                """));
        Lifecycles.attach(root);

        measureAndLayout(root, exact(1000), exact(700));

        assertEquals(600, panel.getWidth());
        assertEquals(560, first.getWidth());
        assertEquals(560, second.getWidth());
        assertEquals(20, first.getTop());
        assertEquals(5, second.getTop() - first.getBottom());
    }

    @Test
    void stylesheetFlexGrowFillsRemainingRowWidth() {
        TaffyLayout root = new TaffyLayout(this.context);
        Row row = new Row(this.context).classes("form-row");
        TextField field = new TextField(this.context).classes("grow");
        Button remove = new Button(this.context, "Remove", () -> {
        }).classes("remove");
        row.add(field, remove);
        root.addView(row);
        root.provideStylesheet(this.stylesheet("""
                .form-row {
                    width: 600px;
                    padding: 0px;
                    gap: 10px;
                    align-items: stretch;
                    justify-content: start;
                }
                .grow { flex-grow: 1; }
                .remove { width: 96px; }
                """));
        Lifecycles.attach(root);

        measureAndLayout(root, exact(1000), exact(300));

        assertEquals(600, row.getWidth());
        assertEquals(96, remove.getWidth());
        assertEquals(494, field.getWidth());
    }

    @Test
    void uiFacadeBuildsSemanticTreeWithOneContext() {
        Ui ui = Ui.with(this.context);
        Column screen = ui.screen(ui.card(
                ui.heading(Component.literal("Editor")),
                ui.section(
                        "Actions",
                        ui.actions(ui.button(Component.literal("Save"), () -> {
                        }))
                )
        ));

        assertTrue(screen.styleMetadata().hasClass("ui-screen"));
        ScrollView scroll = (ScrollView) screen.getChildAt(0);
        assertTrue(scroll.isFillViewport());
        Column viewport = (Column) scroll.getChildAt(0);
        assertTrue(viewport.styleMetadata().hasClass("ui-viewport"));
        Column card = (Column) viewport.getChildAt(0);
        assertTrue(card.styleMetadata().hasClass("ui-card"));
        assertTrue(((Text) card.getChildAt(0)).styleMetadata().hasClass("ui-heading"));
        assertTrue(((Column) card.getChildAt(1)).styleMetadata().hasClass("ui-section"));
    }

    @Test
    void uiScreenScrollsOverflowWithoutShrinkingContent() {
        Ui ui = Ui.with(this.context);
        View content = new View(this.context);
        content.setMinimumHeight(800);
        Column screen = ui.screen(content);

        measureAndLayout(screen, exact(854), exact(480));

        ScrollView scroll = (ScrollView) screen.getChildAt(0);
        assertEquals(800, content.getHeight());
        assertTrue(scroll.canScrollVertically(1));
    }

    @Test
    void textUpdatesCoalesceIntoOneLayoutPass() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<String> value = Signal.of("initial");
        Text text = new Text(this.context, value);
        layout.addChildren(text);
        Lifecycles.attach(layout);
        measureAndLayout(layout, exact(200), exact(100));
        int layoutCount = layout.layoutComputationCount();
        int propertyCount = layout.propertyApplicationCount();

        value.set("first");
        value.set("second");
        value.set("final value");

        assertEquals(1, uiPoster.size());
        uiPoster.runAll();
        assertEquals("final value", text.getText().toString());
        assertTrue(text.isLayoutRequested());
        assertEquals(propertyCount + 1, layout.propertyApplicationCount());

        measureAndLayout(layout, exact(200), exact(100));

        assertEquals(layoutCount + 1, layout.layoutComputationCount());
    }

    @Test
    void queuedUpdateIsDiscardedAfterUnmountAndCaughtUpOnRemount() {
        TestUiPoster uiPoster = new TestUiPoster();
        TaffyLayout layout = new TaffyLayout(
                this.context,
                FlexDirection.COLUMN,
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        Signal<String> value = Signal.of("initial");
        Text text = new Text(this.context, value);
        layout.addChildren(text);
        Lifecycles.attach(layout);

        value.set("queued");
        Lifecycles.detach(text);
        uiPoster.runAll();

        assertEquals("initial", text.getText().toString());

        value.set("detached");
        Lifecycles.attach(text);
        assertEquals(1, uiPoster.size());
        uiPoster.runAll();

        assertEquals("detached", text.getText().toString());
    }

    @Test
    void failingPropertyDoesNotPreventOtherOrLaterUpdates() {
        TestUiPoster uiPoster = new TestUiPoster();
        List<RuntimeException> failures = new ArrayList<>();
        PropertyUpdateQueue queue = new PropertyUpdateQueue(uiPoster::post, failures::add);
        FixedView view = new FixedView(this.context, 10, 10);
        Property<FixedView, Integer> failing = Property.create(
                "failing",
                (target, value) -> {
                    throw new IllegalStateException("failure");
                }
        );
        Property<FixedView, Integer> working = Property.create(
                "working",
                (target, value) -> {
                }
        );
        ClickRecorder applications = new ClickRecorder();

        queue.enqueue(view, failing, () -> {
            failing.apply(view, 1);
            return true;
        });
        queue.enqueue(view, working, () -> {
            applications.record();
            return true;
        });
        uiPoster.runAll();

        assertEquals(1, applications.count);
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst().getMessage().contains("failing"));

        queue.enqueue(view, working, () -> {
            applications.record();
            return true;
        });
        uiPoster.runAll();

        assertEquals(2, applications.count);
    }

    @Test
    void reentrantPropertyUpdateRunsInNextFlush() {
        TestUiPoster uiPoster = new TestUiPoster();
        PropertyUpdateQueue queue = new PropertyUpdateQueue(
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        FixedView view = new FixedView(this.context, 10, 10);
        Property<FixedView, Integer> property = Property.create(
                "value",
                (target, value) -> {
                }
        );
        List<Integer> values = new ArrayList<>();

        queue.enqueue(view, property, () -> {
            values.add(1);
            queue.enqueue(view, property, () -> {
                values.add(2);
                return true;
            });
            return true;
        });

        uiPoster.runNext();
        assertEquals(List.of(1), values);
        assertEquals(1, uiPoster.size());

        uiPoster.runNext();
        assertEquals(List.of(1, 2), values);
    }

    @Test
    void failedPostDoesNotPoisonFutureFlushes() {
        TestUiPoster uiPoster = new TestUiPoster();
        uiPoster.failNextPost = true;
        PropertyUpdateQueue queue = new PropertyUpdateQueue(
                uiPoster::post,
                exception -> {
                    throw exception;
                }
        );
        FixedView view = new FixedView(this.context, 10, 10);
        Property<FixedView, Integer> property = Property.create(
                "value",
                (target, value) -> {
                }
        );
        Property<FixedView, Integer> retryProperty = Property.create(
                "retry",
                (target, value) -> {
                }
        );
        ClickRecorder applications = new ClickRecorder();

        assertThrows(IllegalStateException.class, () -> queue.enqueue(
                view,
                property,
                () -> true
        ));

        queue.enqueue(view, retryProperty, () -> {
            applications.record();
            return true;
        });
        uiPoster.runAll();

        assertEquals(1, applications.count);
    }

    @Test
    void keyedChildrenRetainViewsAndSynchronizeOrder() {
        Signal<List<TestItem>> items = Signal.of(List.of(
                new TestItem("apple"),
                new TestItem("banana"),
                new TestItem("cherry")
        ));
        Column column = new Column(this.context);
        Map<String, FixedView> created = new HashMap<>();
        column.children(items, TestItem::key, item -> {
            FixedView view = new FixedView(this.context, 60, 20);
            created.put(item.key(), view);
            return view;
        });
        Lifecycles.attach(column);

        FixedView apple = created.get("apple");
        FixedView banana = created.get("banana");
        FixedView cherry = created.get("cherry");
        items.set(List.of(
                new TestItem("cherry"),
                new TestItem("apple"),
                new TestItem("date")
        ));
        measureAndLayout(column, exact(200), exact(180));

        assertEquals(4, created.size());
        assertSame(cherry, column.getChildAt(0));
        assertSame(apple, column.getChildAt(1));
        assertSame(created.get("date"), column.getChildAt(2));
        assertNull(banana.getParent());
        assertTrue(cherry.getTop() < apple.getTop());
        assertTrue(apple.getTop() < created.get("date").getTop());
    }

    @Test
    void keyedChildrenPreserveRetainedTextFieldStateAndFocus() {
        Signal<List<TestItem>> items = Signal.of(List.of(
                new TestItem("apple"),
                new TestItem("banana")
        ));
        Column column = new Column(this.context);
        Map<String, TextField> created = new HashMap<>();
        column.children(items, TestItem::key, item -> {
            TextField field = new TextField(this.context);
            field.setText(item.key());
            created.put(item.key(), field);
            return field;
        });
        Lifecycles.attach(column);
        TextField banana = created.get("banana");
        banana.setText("edited banana");
        banana.setSelection(6);
        assertTrue(banana.requestFocus());

        items.set(List.of(new TestItem("banana"), new TestItem("cherry")));

        assertSame(banana, column.getChildAt(0));
        assertEquals("edited banana", banana.getText().toString());
        assertEquals(6, banana.getSelectionStart());
        assertTrue(banana.hasFocus());
    }

    @Test
    void duplicateKeyDoesNotMutateExistingChildren() {
        Signal<List<TestItem>> items = Signal.of(List.of(
                new TestItem("apple"),
                new TestItem("banana")
        ));
        Column column = new Column(this.context);
        ClickRecorder creations = new ClickRecorder();
        column.children(items, TestItem::key, item -> {
            creations.record();
            return new FixedView(this.context, 60, 20);
        });
        Lifecycles.attach(column);
        View apple = column.getChildAt(0);
        View banana = column.getChildAt(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> items.set(List.of(new TestItem("date"), new TestItem("date")))
        );

        assertTrue(exception.getMessage().contains("Duplicate dynamic child key 'date'"));
        assertEquals(2, creations.count);
        assertEquals(2, column.getChildCount());
        assertSame(apple, column.getChildAt(0));
        assertSame(banana, column.getChildAt(1));
    }

    @Test
    void keyedChildrenCatchUpAfterRemount() {
        Signal<List<TestItem>> items = Signal.of(List.of(new TestItem("apple")));
        Column column = new Column(this.context);
        column.children(items, TestItem::key, item -> new FixedView(this.context, 60, 20));
        Lifecycles.attach(column);
        View apple = column.getChildAt(0);
        Lifecycles.detach(column);

        items.set(List.of(new TestItem("banana")));
        assertEquals(1, column.getChildCount());

        Lifecycles.attach(column);

        assertEquals(1, column.getChildCount());
        assertNotSame(apple, column.getChildAt(0));
        assertNull(apple.getParent());
    }

    @Test
    void failedKeyedMountLeavesContainerRemountable() {
        Signal<List<TestItem>> items = Signal.of(List.of(new TestItem("apple")));
        Column column = new Column(this.context);
        column.children(items, TestItem::key, item -> new FixedView(this.context, 60, 20));
        Lifecycles.attach(column);
        View apple = column.getChildAt(0);
        Lifecycles.detach(column);

        items.set(List.of(new TestItem("date"), new TestItem("date")));
        assertThrows(IllegalArgumentException.class, () -> Lifecycles.attach(column));

        items.set(List.of(new TestItem("fig")));
        Lifecycles.attach(column);

        assertEquals(1, column.getChildCount());
        assertNotSame(apple, column.getChildAt(0));
    }

    @Test
    void keyedContainerRejectsExternalHierarchyMutation() {
        Signal<List<TestItem>> items = Signal.of(List.of(new TestItem("apple")));
        Column column = new Column(this.context);
        column.children(items, TestItem::key, item -> new FixedView(this.context, 60, 20));
        View retained = column.getChildAt(0);

        IllegalStateException addFailure = assertThrows(
                IllegalStateException.class,
                () -> column.addView(new FixedView(this.context, 60, 20))
        );
        assertTrue(addFailure.getMessage().contains("bound collection"));
        assertThrows(IllegalStateException.class, () -> column.removeView(retained));
        assertThrows(IllegalStateException.class, () -> column.bringChildToFront(retained));
        assertEquals(1, column.getChildCount());
        assertSame(retained, column.getChildAt(0));
    }

    @Test
    void boundChildrenUpdateRetainedRowsAndSkipUnchangedItems() {
        Signal<List<TestRow>> items = Signal.of(List.of(
                new TestRow("apple", "one"),
                new TestRow("banana", "two")
        ));
        Column column = new Column(this.context);
        Map<String, List<String>> observed = new HashMap<>();
        Map<String, FixedView> created = new HashMap<>();
        column.boundChildren(items, TestRow::key, value -> {
            TestRow first = value.get();
            List<String> labels = new ArrayList<>(List.of(first.label()));
            observed.put(first.key(), labels);
            value.subscribe(row -> labels.add(row.label()));
            FixedView view = new FixedView(this.context, 60, 20);
            created.put(first.key(), view);
            return view;
        });
        Lifecycles.attach(column);

        items.set(List.of(new TestRow("apple", "one"), new TestRow("banana", "edited")));

        assertEquals(2, created.size());
        assertSame(created.get("apple"), column.getChildAt(0));
        assertSame(created.get("banana"), column.getChildAt(1));
        assertEquals(List.of("one"), observed.get("apple"));
        assertEquals(List.of("two", "edited"), observed.get("banana"));
    }

    @Test
    void boundChildrenDropTheValueOfARemovedKey() {
        Signal<List<TestRow>> items = Signal.of(List.of(new TestRow("apple", "one")));
        Column column = new Column(this.context);
        List<String> observed = new ArrayList<>();
        column.boundChildren(items, TestRow::key, value -> {
            value.subscribe(row -> observed.add(row.label()));
            return new FixedView(this.context, 60, 20);
        });
        Lifecycles.attach(column);
        View apple = column.getChildAt(0);

        items.set(List.of(new TestRow("banana", "two")));
        items.set(List.of(new TestRow("apple", "three")));

        assertNull(apple.getParent());
        assertNotSame(apple, column.getChildAt(0));
        assertEquals(List.of(), observed);
    }

    private static ViewGroup.LayoutParams wrapContent() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static KeyEvent keyUp(int keyCode, int modifiers) {
        return KeyEvent.obtain(0, KeyEvent.ACTION_UP, keyCode, 0, modifiers, 0, 0);
    }

    private static KeyEvent keyDown(int keyCode) {
        return KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0);
    }

    private Stylesheet stylesheet(String source) {
        return StylesheetParser.parse(
                ResourceLocation.fromNamespaceAndPath("test", "inline"),
                source
        );
    }

    private static int exact(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    private static int atMost(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
    }

    private static int unspecified() {
        return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }

    private static void measureAndLayout(TaffyLayout layout, int widthSpec, int heightSpec) {
        layout.measure(widthSpec, heightSpec);
        layout.layout(0, 0, layout.getMeasuredWidth(), layout.getMeasuredHeight());
    }

    private static void assertBounds(View view, int left, int top, int right, int bottom) {
        assertEquals(left, view.getLeft());
        assertEquals(top, view.getTop());
        assertEquals(right, view.getRight());
        assertEquals(bottom, view.getBottom());
    }

    private static final class FixedView extends View {

        private final int desiredWidth;
        private final int desiredHeight;

        private FixedView(Context context, int desiredWidth, int desiredHeight) {
            super(context);
            this.desiredWidth = desiredWidth;
            this.desiredHeight = desiredHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            this.setMeasuredDimension(
                    resolveSize(this.desiredWidth, widthMeasureSpec),
                    resolveSize(this.desiredHeight, heightMeasureSpec)
            );
        }
    }

    private static final class WrappingView extends View {

        private int contentWidth;
        private final int lineHeight;
        private int lastIntrinsicWidthMode;

        private WrappingView(Context context, int contentWidth, int lineHeight) {
            super(context);
            this.contentWidth = contentWidth;
            this.lineHeight = lineHeight;
        }

        private void setContentWidth(int contentWidth) {
            this.contentWidth = contentWidth;
            this.requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int width = switch (widthMode) {
                case MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec);
                case MeasureSpec.AT_MOST -> Math.min(
                        this.contentWidth,
                        MeasureSpec.getSize(widthMeasureSpec)
                );
                default -> this.contentWidth;
            };
            int lines = (int) Math.ceil((double) this.contentWidth / Math.max(1, width));
            int desiredHeight = lines * this.lineHeight;

            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                this.lastIntrinsicWidthMode = widthMode;
            }
            this.setMeasuredDimension(
                    resolveSize(width, widthMeasureSpec),
                    resolveSize(desiredHeight, heightMeasureSpec)
            );
        }
    }

    private static final class TrackableValue<T> implements Value<T> {

        private final T value;
        private boolean failSubscribe;
        private int activeSubscriptions;

        private TrackableValue(T value, boolean failSubscribe) {
            this.value = value;
            this.failSubscribe = failSubscribe;
        }

        @Override
        public T get() {
            return this.value;
        }

        @Override
        public Subscription subscribe(Consumer<? super T> listener) {
            if (this.failSubscribe) throw new IllegalStateException("subscription failed");

            this.activeSubscriptions++;
            return new Subscription() {

                private boolean active = true;

                @Override
                public void close() {
                    if (!this.active) return;

                    this.active = false;
                    TrackableValue.this.activeSubscriptions--;
                }
            };
        }

        private void setFailSubscribe(boolean failSubscribe) {
            this.failSubscribe = failSubscribe;
        }

        private int activeSubscriptions() {
            return this.activeSubscriptions;
        }
    }

    private static final class ClickRecorder {

        private int count;

        private void record() {
            this.count++;
        }
    }

    private record TestItem(String key) {
    }

    /** A keyed item carrying a field that changes while its key stays. */
    private record TestRow(String key, String label) {
    }

    private static final class TestUiPoster {

        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean failNextPost;

        private void post(Runnable task) {
            if (this.failNextPost) {
                this.failNextPost = false;
                throw new IllegalStateException("poster unavailable");
            }
            this.tasks.addLast(task);
        }

        private int size() {
            return this.tasks.size();
        }

        private void runNext() {
            this.tasks.removeFirst().run();
        }

        private void runAll() {
            while (!this.tasks.isEmpty()) {
                this.tasks.removeFirst().run();
            }
        }
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
