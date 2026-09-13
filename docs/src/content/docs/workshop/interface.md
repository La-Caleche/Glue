---
title: Light Workshop Interface
description: Combine the Probe Inspector with a persistent Scene, Inspector, and Log Dockspace.
artifact: glue-mcsx-dock
modId: glue-mcsx-dock
environment: client
---

# Light Workshop Interface

This milestone combines the earlier MCSX form and Dockspace tasks into one self-contained client-side
sample. The result owns its state, panes, layout, resources, open/close lifecycle, persistence
controls, and reset behavior.

## Outcome

The Light Workshop opens Scene on the left, with Inspector above Log on the right. Applying a named
probe appends a retained Log row. Users may rearrange the workspace, save it, reset it, close it, and
restore their saved arrangement on the next open.

<DocImage title="Completed Light Workshop interface" description="Capture the final persistent Dockspace with Scene on the left, a filled Probe Inspector form at top-right, Log at bottom-right containing an applied-probe entry, and the Save layout and Reset layout header actions visible." />

## Add the Client Module

The milestone needs Dockspace, which brings base MCSX as a Maven dependency:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-mcsx-dock:<glue-version>")
}
```

```json [fabric.mod.json]
{
  "id": "lightworkshop",
  "environment": "client",
  "depends": {
    "glue-mcsx-dock": "<glue-version>"
  }
}
```

Place the Java class under the client package so a dedicated server never initializes ModernUI or
Dockspace types.

## Build the Complete Owner

```java [LightWorkshopInterface.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.UiOverlay;
import fr.lacaleche.glue.mcsx.client.dock.DockContent;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.client.dock.Dockspace;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import icyllis.modernui.view.View;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class LightWorkshopInterface {

    private static final ResourceLocation INTERFACE =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "interface");

    private final Signal<String> probeName = Signal.of("Key light");
    private final Signal<Boolean> probeEnabled = Signal.of(true);
    private final Value<Boolean> validName =
            this.probeName.map(value -> !value.isBlank());
    private final Signal<List<LogEntry>> logEntries = Signal.of(List.of(
            new LogEntry(1, "Light Workshop ready")
    ));
    private long nextLogId = 2;
    private volatile Dockspace active;

    public void toggle() {
        Dockspace current = this.active;
        if (current != null) {
            current.close();
            return;
        }
        if (UiOverlay.isOccupied()) return;

        Dockspace next = this.createWorkspace();
        this.active = next;
        try {
            next.open();
        } catch (RuntimeException failure) {
            if (this.active == next) this.active = null;
            throw failure;
        }
    }

    public void close() {
        Dockspace current = this.active;
        if (current != null) current.close();
    }

    private Dockspace createWorkspace() {
        Dockspace[] owner = new Dockspace[1];
        Dockspace workspace = Dockspace.builder(INTERFACE)
                .pane(this.scenePane())
                .pane(this.inspectorPane())
                .pane(this.logPane())
                .defaultLayout(defaultLayout())
                .theme(Themes.resource(INTERFACE))
                .stylesheet(Stylesheets.resource(INTERFACE))
                .header(DockContent.ui(this::header))
                .footer(DockContent.ui(this::footer))
                .onClose(() -> {
                    if (this.active == owner[0]) this.active = null;
                })
                .build();
        owner[0] = workspace;
        return workspace;
    }

    private DockPane scenePane() {
        return DockPane.builderLiteral(
                        "scene",
                        "Scene",
                        ui -> ui.screen(
                                ui.column(
                                        ui.literalHeading("Live Scene"),
                                        ui.literalCopy(
                                                "Replace this card with the viewport recipe when the tool needs live gameplay."
                                        )
                                ).classes("workshop-pane", "scene-summary")
                        )
                )
                .closable(false)
                .build();
    }

    private DockPane inspectorPane() {
        return DockPane.literal(
                "inspector",
                "Inspector",
                ui -> ui.screen(
                        ui.column(
                                ui.literalHeading("Probe Inspector"),
                                ui.literalField("Probe name").text(this.probeName),
                                ui.literalCheckbox("Enable probe").checked(this.probeEnabled),
                                ui.literalText("A probe name is required.")
                                        .visible(this.validName.map(valid -> !valid))
                                        .classes("validation"),
                                ui.actions(
                                        ui.literalButton("Apply", this::applyProbe)
                                                .enabled(this.validName)
                                )
                        ).classes("workshop-pane", "inspector-form")
                )
        );
    }

    private DockPane logPane() {
        return DockPane.builderLiteral(
                        "log",
                        "Log",
                        ui -> ui.screen(
                                ui.column(
                                        ui.literalHeading("Session Log"),
                                        ui.boundColumn(
                                                this.logEntries,
                                                LogEntry::id,
                                                entry -> ui.text(entry.map(LogEntry::message))
                                                        .classes("log-line")
                                        )
                                ).classes("workshop-pane")
                        )
                )
                .trailingHeaderUi(ui -> ui.row(ui.literalText("LIVE")))
                .build();
    }

    private View header(Ui ui) {
        return ui.row(
                ui.literalHeading("Light Workshop"),
                ui.literalSecondaryButton("Focus Inspector", this::focusInspector),
                ui.literalSecondaryButton("Save layout", this::saveLayout),
                ui.literalSecondaryButton("Reset layout", this::resetLayout)
        ).classes("workshop-header");
    }

    private View footer(Ui ui) {
        return ui.row(
                ui.literalText("Probe:"),
                ui.text(this.probeName.map(value ->
                        value.isBlank() ? "unnamed" : value.trim())),
                ui.text(this.probeEnabled.map(enabled ->
                        enabled ? "enabled" : "disabled"))
        ).classes("workshop-footer");
    }

    private void applyProbe() {
        String normalized = this.probeName.get().trim();
        if (normalized.isEmpty()) return;

        this.probeName.set(normalized);
        List<LogEntry> updated = new ArrayList<>(this.logEntries.get());
        updated.add(new LogEntry(
                this.nextLogId++,
                "Applied " + normalized + (this.probeEnabled.get() ? " (enabled)" : " (disabled)")
        ));
        this.logEntries.set(List.copyOf(updated));
    }

    private void saveLayout() {
        Dockspace current = this.active;
        if (current != null && current.isOpen()) current.saveLayout();
    }

    private void focusInspector() {
        Dockspace current = this.active;
        if (current != null && current.isOpen()) current.focusPane("inspector");
    }

    private void resetLayout() {
        Dockspace current = this.active;
        if (current != null && current.isOpen()) current.resetLayout();
    }

    private static DockLayout defaultLayout() {
        return DockLayouts.layout(DockLayouts.split(
                DockAxis.HORIZONTAL,
                List.of(
                        DockLayouts.tabs("scene"),
                        DockLayouts.column(
                                DockLayouts.tabs("inspector"),
                                DockLayouts.tabs("log")
                        )
                ),
                List.of(0.68, 0.32)
        ));
    }

    private record LogEntry(long id, String message) {
    }
}
```

Call `toggle()` from a client-thread trigger. The owner checks the shared overlay slot, creates one
single-use Dockspace per open, closes only its own instance, and keeps the reference until `onClose`.

## Add the Interface Theme

```json [assets/lightworkshop/mcsx/themes/interface.json]
{
  "values": {
    "surface": "#191c20",
    "surface-raised": "#262b31",
    "surface-well": "#121518",
    "surface-header": "#1f2328",
    "text-primary": "#eef1f4",
    "text-muted": "#b6bec5",
    "accent": "#3d464f",
    "control-on": "#cdd4da",
    "danger": "#241715",
    "danger-text": "#c19086",
    "control-height": 32,
    "corner-radius": 3,
    "dock-background": "#0b0c0d",
    "dock-pane-background": "@surface",
    "dock-header-background": "@surface-header",
    "dock-active-tab-background": "@surface-hover",
    "dock-drag-ghost-background": {"color": "@surface-raised", "alpha": 232},
    "dock-drop-highlight": {"color": "@control-on", "alpha": 92}
  }
}
```

## Add the Interface Stylesheet

```css [assets/lightworkshop/mcsx/styles/interface.mcss]
.ui-screen {
    padding: 0px;
    gap: 0px;
    align-items: stretch;
    justify-content: start;
    background: @surface;
}

.workshop-pane {
    width: 100%;
    padding: 18px;
    gap: 12px;
    align-items: stretch;
    background: @surface;
}

.inspector-form {
    max-width: 520px;
}

.workshop-header {
    width: 100%;
    padding: 8px;
    gap: 8px;
    align-items: center;
    justify-content: end;
    background: @surface-raised;
}

.workshop-footer {
    width: 100%;
    padding: 6px;
    gap: 8px;
    align-items: center;
    justify-content: start;
    background: @surface-raised;
}

.validation {
    color: @danger;
}

.log-line {
    color: @text-muted;
}

Button.secondary {
    background: transparent;
}
```

### Expected result

The Java default appears on first open. Apply updates UI-owned signals synchronously and the keyed Log
column appends one View without rebuilding retained rows. Dockspace saves completed layout changes and
the final close; Reset deletes the user override and reloads this default.

## Ownership Check

- `Signal` mutations occur in MCSX handlers on ModernUI's UI thread.
- The trigger and Dockspace lifecycle callbacks run on Minecraft's client thread.
- The volatile `active` field makes the Dockspace handoff visible between those threads.
- Pane Views are retained and disposed later by Dockspace on the UI thread.
- The persistent id and both resource ids are `lightworkshop:interface`.
- Pane ids `scene`, `inspector`, and `log` remain stable across saved layouts.

::: details Extend the milestone safely
Use [ClientMirror](../mcsx/reactivity.md#mirror-client-state) when Inspector displays Minecraft-owned
state. Replace the Scene summary with the complete
[embedded viewport recipe](../dockspace/interaction.md#embed-the-game-viewport) only after adding
`glue-render`; keep its synchronous `onUnmount` cleanup. Translate user-facing static labels with the
explicit `translatable*` factories and normal Minecraft language resources when the sample becomes a
shipped mod.
:::

## Next Steps

- [Review MCSX component semantics](../mcsx/components.md) before adding more form controls.
- [Mirror live player or light state](../mcsx/reactivity.md) into Inspector and footer.
- [Validate theme and MCSS resources](../mcsx/themes-and-styles.md#validate-shipped-resources).
- [Replace Scene with the live viewport](../dockspace/interaction.md#embed-the-game-viewport).
- [Author a resource-pack default](../dockspace/persistence.md#use-a-packaged-default-instead) if pack overrides are required.
