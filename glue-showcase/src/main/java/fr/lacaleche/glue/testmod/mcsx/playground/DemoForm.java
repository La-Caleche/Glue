package fr.lacaleche.glue.testmod.mcsx.playground;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.component.Column;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;

public final class DemoForm {

    public static final String TAG_FIELD = "mcsx.form.field";
    public static final String TAG_ERROR = "mcsx.form.error";
    public static final String TAG_FOCUS = "mcsx.form.focus";
    public static final String TAG_CONSENT = "mcsx.form.consent";
    public static final String TAG_SUBMIT = "mcsx.form.submit";
    public static final String TAG_SUCCESS = "mcsx.form.success";

    private final Signal<String> name = Signal.of("");
    private final Signal<Boolean> consent = Signal.of(false);
    private final Value<Boolean> nameValid = this.name.map(DemoForm::isNameValid);
    private final Value<Boolean> valid = Value.combine(
            this.nameValid,
            this.consent,
            (nameValid, consent) -> nameValid && consent
    );
    private final Signal<Boolean> errorVisible = Signal.of(false);
    private final Signal<Boolean> focusVisible = Signal.of(false);
    private final Signal<Boolean> successVisible = Signal.of(false);
    private int submissionCount;

    Column create(Ui ui) {
        return ui.translatableSection(
                "mcsx.showcase.form_title",
                ui.translatableCopy("mcsx.showcase.form_description"),
                ui.translatableField("mcsx.showcase.form_name")
                        .text(this.name)
                        .onSubmit(this::submit)
                        .onFocusChanged(this::focusChanged)
                        .state("invalid", this.nameValid.map(value -> !value))
                        .tag(TAG_FIELD)
                        .classes("form-field"),
                ui.translatableCopy("mcsx.showcase.form_error")
                        .visible(this.errorVisible)
                        .tag(TAG_ERROR)
                        .classes("form-error"),
                ui.translatableCopy("mcsx.showcase.form_focus")
                        .visible(this.focusVisible)
                        .tag(TAG_FOCUS)
                        .classes("form-focus"),
                ui.translatableCheckbox("mcsx.showcase.form_consent")
                        .checked(this.consent)
                        .enabled(this.nameValid)
                        .tag(TAG_CONSENT),
                ui.translatableButton("mcsx.showcase.form_submit", this::submit)
                        .enabled(this.valid)
                        .tag(TAG_SUBMIT),
                ui.translatableCopy("mcsx.showcase.form_success")
                        .visible(this.successVisible)
                        .tag(TAG_SUCCESS)
                        .classes("form-success")
        ).classes("demo-form");
    }

    public boolean consent() {
        return this.consent.get();
    }

    public int submissionCount() {
        return this.submissionCount;
    }

    private void submit() {
        boolean valid = this.valid.get();
        this.errorVisible.set(!valid);
        if (!valid) {
            this.successVisible.set(false);
            return;
        }

        this.submissionCount++;
        this.successVisible.set(true);
    }

    private void focusChanged(boolean focused) {
        this.focusVisible.set(focused);
        if (!focused) {
            boolean valid = this.valid.get();
            this.errorVisible.set(!valid);
            if (!valid) {
                this.successVisible.set(false);
            }
        }
    }

    private static boolean isNameValid(String name) {
        return name.trim().length() >= 3;
    }
}
