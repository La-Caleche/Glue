---
title: Persistence
description: Define Light Workshop defaults, expose reset, then understand saved-layout recovery.
artifact: glue-mcsx-dock
modId: glue-mcsx-dock
environment: client
---

# Persistence

Begin with a trustworthy default and an obvious Reset action. Disk paths and recovery behavior matter
only after users can safely return to that default.

## Outcome

The Scene, Inspector, and Log arrangement is the first-run layout. Users may move panes, close and
reopen the tool, and get their arrangement back. Reset layout deletes the user override and restores
the configured default.

<DocImage title="Dock layout restoration" description="Capture three states in sequence: the default Scene/Inspector/Log layout, a visibly customized arrangement before closing, and the same customized arrangement restored after reopening; include the Reset action returning to default." />

## Set the Default

Return the visual layout from one method and pass it to `defaultLayout(...)`:

```java
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
```

Enable the default persistence behavior by removing `.persistence(false)` from the overview owner:

```java
return Dockspace.builder(WORKSPACE)
        .pane(scene)
        .pane(inspector)
        .pane(log)
        .defaultLayout(defaultLayout())
        .header(DockContent.ui(this::workspaceHeader))
        .onClose(() -> this.active = null)
        .build();
```

## Add Save and Reset Actions

The header is created after the volatile `active` field has been assigned and the workspace has
acquired its mount. Keep the methods defensive because close can begin independently:

```java
private View workspaceHeader(Ui ui) {
    return ui.actions(
            ui.literalSecondaryButton("Save layout", this::saveLayout),
            ui.literalSecondaryButton("Reset layout", this::resetLayout)
    );
}

private void saveLayout() {
    Dockspace current = this.active;
    if (current != null && current.isOpen()) current.saveLayout();
}

private void resetLayout() {
    Dockspace current = this.active;
    if (current != null && current.isOpen()) current.resetLayout();
}
```

`saveLayout()` explicitly persists the current semantic layout. `resetLayout()` removes the primary
user document and its backup, restores a maximized pane first, reloads defaults, and publishes the
result through normal layout and open-pane listeners. Completed docking gestures and `close()` also
save automatically.

For temporary tools and tests, `.persistence(false)` makes saves no-ops and makes reset reload defaults
without touching disk. Java and resource defaults still work.

## Know What Is Saved

For workspace id `lightworkshop:interface`, user state lives at:

```text
config/glue-mcsx/dock/lightworkshop/interface.json
```

When a new save replaces the document, the previous one is retained as `interface.json_old`. The
document stores pane order and activation, split axes and shares, floating subtrees, window frames,
and stacking. It does not store pane Views, application state, or transient maximize state.

## Use a Packaged Default Instead

For a default that resource packs may override, place the JSON at
`assets/lightworkshop/mcsx/dock/interface.json` and either omit an explicit default or select it:

```java
.defaultLayout(ResourceLocation.fromNamespaceAndPath(
        "lightworkshop", "interface"))
```

Calling either `defaultLayout` overload replaces the other configured default. Validate a shipped
document with the public parser:

```java [DockResourcesTest.java]
package dev.example.lightworkshop.client.ui;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class DockResourcesTest {

    @Test
    void interfaceLayoutParses() throws IOException {
        String json = Files.readString(Path.of(
                "src/main/resources/assets/lightworkshop/mcsx/dock/interface.json"));
        assertDoesNotThrow(() -> DockLayouts.parse(json));
    }
}
```

Parsing validates and normalizes the document without knowing a workspace's pane registry. Unknown
pane ids are removed only when a particular workspace loads the candidate. There is no public JSON
writer; let Dockspace own user saves instead of using the file as application storage.

## Switch Workspace Presets

One mounted Dockspace can switch persistence keys while retaining its pane registry, theme,
stylesheet, header, and footer:

```java
workspace.switchWorkspace(ResourceLocation.fromNamespaceAndPath(
        "lightworkshop", "lighting"));

workspace.switchWorkspace(
        ResourceLocation.fromNamespaceAndPath("lightworkshop", "inspection"),
        DockLayouts.layout(DockLayouts.tabs("scene", "inspector"))
);
```

Overloads accept no explicit default, a Java `DockLayout`, or a resource `ResourceLocation`. The
current layout is saved before loading the target, maximize state is restored first, and
`workspace()` reports the new key.

::: details Layout JSON document
Saved and resource layouts share one format:

```json [assets/lightworkshop/mcsx/dock/interface.json]
{
  "version": 1,
  "tree": {
    "type": "split",
    "axis": "horizontal",
    "shares": [0.68, 0.32],
    "children": [
      {"type": "tabs", "tabs": ["scene"], "active": "scene"},
      {
        "type": "split",
        "axis": "vertical",
        "shares": [0.5, 0.5],
        "children": [
          {"type": "tabs", "tabs": ["inspector"], "active": "inspector"},
          {"type": "tabs", "tabs": ["log"], "active": "log"}
        ]
      }
    ]
  },
  "windows": []
}
```

Floating windows add objects containing `node`, `x`, `y`, positive `width` and `height`, and
`stackingOrder`. `DockLayouts.parse(json)` throws `DockLayoutException` when a document cannot be
interpreted safely and applies the same format-level normalization as runtime loading.
:::

::: details Default resolution and sanitation
At mount, Dockspace tries candidates in this order:

1. User document, then `_old` if the primary is unreadable.
2. Java `DockLayout` configured through `.defaultLayout(layout)`.
3. Resource default at `assets/<namespace>/mcsx/dock/<path>.json`.
4. One tab group containing all registered panes in registration order.
5. Empty layout when no panes are registered.

Without an explicit resource id, the workspace id is the resource key. Every candidate is sanitized
against registered ids: unknown and duplicate occurrences are removed, invalid active ids select a
survivor, degenerate splits collapse, same-axis splits flatten, shares normalize, and stacking order
becomes consistent. A non-empty candidate that loses every pane falls through; an explicitly empty
layout is valid and stops fallback.

Before mounting, `layout()` can return a sanitized Java default or the registration-order fallback.
An explicit resource default remains unresolved and therefore reports empty until mount can read
client resources.
:::

::: details Ordered writes and recovery
Completed gestures encode a snapshot and schedule an ordered write away from the UI thread. A
temporary sibling is safely swapped into place while retaining the prior document as `_old`; writes
for one workspace remain ordered.

A missing, malformed, or unreadable primary user document falls back to `_old`, then defaults. Invalid
resource defaults are logged and fall through. Layouts containing some obsolete ids keep survivors;
layouts containing only obsolete ids fall through. Failed writes are logged and preserve the previous
document when possible. Runtime user corruption is recovered rather than propagated through mount.
:::

## Next Steps

- [Test tab and focus behavior](./interaction.md) after restoration is enabled.
- [Review layout invariants](./layout-and-panes.md) before authoring a complex resource tree.
- [Validate theme resources too](../mcsx/themes-and-styles.md#validate-shipped-resources).
- [Use the final interface milestone](../workshop/interface.md) as a compact persistent implementation.
