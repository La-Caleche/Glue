---
title: Test the Light Lifecycle
description: Drive the probe's registered key, verify two light toggle cycles and capture the result.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Test the Light Lifecycle

Complete the [lighting step](./lighting.md), then create the development-only subproject from
[GameTest setup](../gametest/setup.md). Keep the harness out of the shipped application.

## Connect the Testmod to the Application

The workshop in this tutorial is the **root project**, so its development dependency is `":"`:

```kotlin [testmod/build.gradle.kts — add to the existing dependencies]
dependencies {
    implementation(project(path = ":", configuration = "namedElements"))
    modImplementation("fr.lacaleche.glue:glue-lumos:<glue-version>")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<glue-version>")
}
```

Add `lightworkshop`, `glue-lumos` and `glue-lumos-client` to the testmod descriptor's existing
dependencies. Use `"*"` for the local application and the selected Glue version for library IDs.
If your application is a named subproject, replace `":"` with its actual Gradle path.

Create a disposable singleplayer world named **GameTest World**, stand still in a dim area facing
an opaque wall, and save it. Use Fast or Fancy graphics, the default **P** binding and no other
Lumos lights. The test equips the probe in the main hand and owns that fixture inventory.

## Register the Scenario

Replace the setup test entrypoint with the following class. Each run receives its own state object:

```java [testmod/src/main/java/dev/example/lightworkshop/test/LightWorkshopGameTests.java]
package dev.example.lightworkshop.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.example.lightworkshop.registry.WorkshopItems;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.lumos.Lumos;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class LightWorkshopGameTests implements ClientModInitializer {

    private static final String ID = "lightworkshop:probe-smoke";

    @Override
    public void onInitializeClient() {
        GameTests.register(ID, () -> new ProbeSmoke().build());
    }

    private static void pressProbeKey() {
        KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P));
    }

    private static final class ProbeSmoke {

        private List<Light> baseline;
        private Light preview;
        private Vec3 expectedPosition;
        private int firstFrame = -1;

        GameTest build() {
            return GameTest.create(ID).openWorld("GameTest World")
                    .runOnServer("equip the probe", server -> {
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WorkshopItems.LUMEN_PROBE));
                        }
                    })
                    .waitUntil("the probe is equipped", ctx -> ctx.player().getMainHandItem().is(WorkshopItems.LUMEN_PROBE))
                    .run("record baseline and probe position", ctx -> {
                        this.baseline = List.copyOf(Lumos.active(ctx.level()));
                        this.expectedPosition = ctx.player().getEyePosition().add(ctx.player().getViewVector(1.0f).scale(3));
                    })
                    .run("show the light", ctx -> pressProbeKey())
                    .waitUntil("one new light is active", this::capturePreview)
                    .expect("the light matches the probe", ctx -> this.matchesPreview())
                    .step("wait for ten rendered frames", GameTest.DEFAULT_TIMEOUT, ctx -> {
                        if (this.firstFrame < 0) this.firstFrame = ctx.renderedWorldFrames();
                        return ctx.renderedWorldFrames() - this.firstFrame >= 10;
                    })
                    .screenshot("lumen-probe-on")
                    .run("hide the light", ctx -> pressProbeKey())
                    .waitUntil("the baseline is restored", this::baselineRestored)
                    .run("show a second light", ctx -> pressProbeKey())
                    .waitUntil("one new light returns", this::capturePreview)
                    .run("hide the second light", ctx -> pressProbeKey())
                    .waitUntil("the second cycle leaves no light", this::baselineRestored);
        }

        private boolean capturePreview(TestContext context) {
            List<Light> active = Lumos.active(context.level());
            if (active.size() != this.baseline.size() + 1 || !active.containsAll(this.baseline)) return false;
            for (Light light : active) {
                if (!this.baseline.contains(light)) {
                    this.preview = light;
                    return true;
                }
            }
            return false;
        }

        private boolean matchesPreview() {
            return this.preview.type == LightType.POINT
                    && new Vec3(this.preview.x, this.preview.y, this.preview.z).distanceTo(this.expectedPosition) < 0.05
                    && this.preview.r == 1.0f && this.preview.g == 0.72f && this.preview.b == 0.38f
                    && this.preview.intensity == 2.0f && this.preview.range == 8.0f && !this.preview.castsShadow;
        }

        private boolean baselineRestored(TestContext context) {
            List<Light> active = Lumos.active(context.level());
            return !active.contains(this.preview) && active.size() == this.baseline.size()
                    && active.containsAll(this.baseline);
        }
    }
}
```

The test queues the registered key action; it does not simulate a physical OS keyboard. Its whole-list
assertions require the isolated light fixture above. The screenshot records evidence, not a pixel-level verdict.

## Run and Read the Result

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke
```
```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
```
:::

Inspect `testmod/run/screenshots/gametest/lightworkshop_probe-smoke/report.txt` for `RESULT: PASS`,
then open `01-lumen-probe-on.png`. The client closes after the scenario unless keep-open was enabled.

| Failure | Check |
|---|---|
| World cannot open | Create and save `GameTest World` in the testmod's run directory. |
| Probe never equips | The testmod loads the application and uses the correct root/subproject dependency. |
| Light never appears | The client entrypoint registers `ProbeLighting`, P retains its default binding, and the probe is held. |
| Position/count assertion fails | Stand still and remove unrelated or late-synchronizing lights from the fixture. |
| Frames never advance | The world must be visible and rendering. |
| Text report passes but the image is wrong | Review the active graphics mode and [Lumos compatibility](../lumos/materials-and-compatibility.md). |

Use [Writing Tests](../gametest/writing.md) for additional scenarios and
[Reports and automation](../gametest/reports.md) for reliable CI verdicts.
