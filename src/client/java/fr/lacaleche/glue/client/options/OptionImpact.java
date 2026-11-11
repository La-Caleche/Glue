package fr.lacaleche.glue.client.options;

/** How much an option weighs on the frame. Sodium's page shows it beside the option; the vanilla screen does not. */
public enum OptionImpact {
    LOW,
    MEDIUM,
    HIGH,
    /** Depends on the values the option sets, as a preset's does. */
    VARIES
}
