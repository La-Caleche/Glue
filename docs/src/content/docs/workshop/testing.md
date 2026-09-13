---
title: Test the Lumen Probe Workshop
description: Exercise the documented Lumen Probe toggle in a live client and capture its rendered result.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Test the Lumen Probe Workshop

## Outcome

You will test the fresh [Lumen Probe lighting milestone](./lighting.md) without depending on any
bundled example mod. The script opens an existing singleplayer world, queues the documented `P` key,
finds exactly one new warm point light three blocks ahead of the player, captures it after 10 rendered
frames, toggles it off, and repeats the cycle to catch leaked duplicates.

The test uses the generic runner plus the public `Light` and `Lumos` APIs. No custom application tool
is necessary for this scenario.

## Add the Scenario Dependencies

Start with the development `testmod` from [GameTest Setup](../gametest/setup.md). Add the Light
Workshop development output and direct dependencies for the Lumos types imported by the test:

```kotlin [testmod/build.gradle.kts]
dependencies {
    implementation(project(path = ":lightworkshop", configuration = "namedElements"))
    modImplementation("fr.lacaleche.glue:glue-lumos:<glue-version>")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<glue-version>")
}
```

Keep these beside the existing `glue-gametest` development dependency. The testmod descriptor should
also require the application mod so Fabric loads the feature under test:

```json [testmod/src/main/resources/fabric.mod.json]
{
  "depends": {
    "lightworkshop": "*",
    "glue-gametest": "*",
    "glue-lumos": "<glue-version>",
    "glue-lumos-client": "<glue-version>"
  }
}
```

Merge those entries into the complete descriptor from Setup. Create a singleplayer world named
`GameTest World`, stand still in a dim Fast or Fancy scene, and face an opaque wall from a few blocks
away. Keep this fixture free of other local, attached, or persistent Lumos lights and let initial
world synchronization settle before using it for the test. The script opens that world itself.

## Write the Probe Smoke Test

Replace the setup test class with this factory-backed script:

```java [testmod/src/main/java/dev/example/lightworkshop/test/LightWorkshopGameTests.java]
package dev.example.lightworkshop.test;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.lumos.Lumos;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class LightWorkshopGameTests implements ClientModInitializer {
    private static final String PROBE_SMOKE = "lightworkshop:probe-smoke";

    @Override
    public void onInitializeClient() {
        GameTests.register(PROBE_SMOKE, LightWorkshopGameTests::probeSmoke);
    }

    private static GameTest probeSmoke() {
        List<Light> baseline = new ArrayList<>();
        Light[] preview = new Light[1];
        Vec3[] expectedPosition = new Vec3[1];
        int[] firstRenderedFrame = {-1};

        return GameTest.create(PROBE_SMOKE)
                .openWorld("GameTest World")
                .run("record the initial lights and probe point", context -> {
                    baseline.addAll(Lumos.active(context.level()));
                    expectedPosition[0] = context.player().getEyePosition()
                            .add(context.player().getViewVector(1.0f).scale(3.0));
                })
                .run("press P to show the Lumen Probe", context -> pressProbeKey())
                .waitUntil("one new Lumen Probe light is active",
                        context -> captureSinglePreview(context, baseline, preview))
                .expect("the Lumen Probe matches the documented definition",
                        context -> matchesDocumentedProbe(preview[0], expectedPosition[0]))
                .step("wait for ten Lumen Probe frames", GameTest.DEFAULT_TIMEOUT, context -> {
                    if (firstRenderedFrame[0] < 0) {
                        firstRenderedFrame[0] = context.renderedWorldFrames();
                    }
                    return context.renderedWorldFrames() - firstRenderedFrame[0] >= 10;
                })
                .screenshot("lumen-probe-on")
                .run("press P to hide the Lumen Probe", context -> pressProbeKey())
                .waitUntil("the first toggle restores the initial lights",
                        context -> baselineRestored(context, baseline, preview[0]))
                .run("press P to start a second cycle", context -> pressProbeKey())
                .waitUntil("one Lumen Probe light returns",
                        context -> captureSinglePreview(context, baseline, preview))
                .run("press P to finish the second cycle", context -> pressProbeKey())
                .waitUntil("the second cycle leaves no preview light",
                        context -> baselineRestored(context, baseline, preview[0]));
    }

    private static void pressProbeKey() {
        KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P));
    }

    private static boolean captureSinglePreview(
            TestContext context, List<Light> baseline, Light[] preview) {
        List<Light> active = Lumos.active(context.level());
        if (active.size() != baseline.size() + 1 || !active.containsAll(baseline)) {
            return false;
        }
        for (Light light : active) {
            if (!baseline.contains(light)) {
                preview[0] = light;
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDocumentedProbe(Light light, Vec3 expected) {
        return light != null
                && light.type == LightType.POINT
                && close(light.x, expected.x)
                && close(light.y, expected.y)
                && close(light.z, expected.z)
                && light.r == 1.0f
                && light.g == 0.72f
                && light.b == 0.38f
                && light.intensity == 2.0f
                && light.range == 8.0f
                && !light.castsShadow;
    }

    private static boolean baselineRestored(
            TestContext context, List<Light> baseline, Light preview) {
        List<Light> active = Lumos.active(context.level());
        return !active.contains(preview)
                && active.size() == baseline.size()
                && active.containsAll(baseline);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.05;
    }
}
```

The factory creates fresh baseline, preview, position, and frame state for each selected run. `Light`
does not override equality, so the list checks preserve the identity behavior used by the documented
`Lumos.spawn` and `Lumos.despawn` calls. The whole-snapshot assertions deliberately require the
isolated fixture described above; changing or late-synchronized lights would make that baseline
unstable.

`KeyMapping.click` increments the click count for the mapping currently bound to `P`. The app's
`consumeClick` loop receives it from an end-client-tick listener. The following `waitUntil` works
whether that listener consumes the click later in the current event or on the next client tick.

## Run the Scenario

Use the default `P` binding from the lighting milestone, then run:

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
```
:::

The script does not invoke `IrisShadersTool`, so Iris is not required. It exercises whichever
supported renderer configuration is active in this development client.

## Check the Expected Result

`openWorld` contributes three steps, making this a 15-step script. A pass ends with these statuses;
the screenshot tick count varies with the asynchronous PNG write:

```text
[ 9/15] PASS  screenshot 'lumen-probe-on'  (<capture ticks> ticks)
[14/15] PASS  press P to finish the second cycle  (1 ticks)
[15/15] PASS  wait until the second cycle leaves no preview light  (<wait ticks> ticks)
RESULT: PASS
```

The paired artifacts are:

```text
testmod/run/screenshots/gametest/lightworkshop_probe-smoke/
|-- 01-lumen-probe-on.png
`-- report.txt
```

<DocImage title="Captured result" description="A dim opaque wall with exactly one warm orange Lumen Probe pool centered three blocks ahead of the player's eyes." />

The report verifies the light count, documented definition, identity cleanup, and second cycle. The
screenshot step verifies that Minecraft wrote the PNG, not that its pixels match a baseline. Review
the image for the warm pool or add an external image-comparison stage if visual output must gate the
run.

## Why No Custom Tool Is Needed

The generic DSL can queue this milestone's registered key with a one-shot `run`, wait for the public
Lumos state, and inspect the public immutable `Light` definition. Adding an application-specific
`GameTool` would duplicate a path that is already observable.

Use a custom tool only when the real app action has no stable public or registered input seam. A tool
starts once, returns a per-run tick, and is then polled like any other step; the exact lifecycle is in
[Use Tools Sparingly](../gametest/writing.md#use-tools-sparingly).

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `openWorld` reports that the world could not be opened | Create an existing singleplayer world named `GameTest World`, then rerun. |
| The first light wait times out | Restore the workshop action to its documented default `P` binding and confirm the `lightworkshop` client entrypoint loaded. |
| The definition expectation fails | Compare the app against the lighting milestone's position, linear RGB, intensity, range, and `withShadow(false)` values. |
| The rendered-frame step times out | Confirm the world is visible and rendering; the counter does not advance on the title screen. |
| The second cycle fails | Check that the app stores the exact `Lumos.spawn` result and clears both `previewLight` and `ownerLevel`. |
| The report passes but the image is visually wrong | Treat the PNG as review evidence, not a pixel assertion; check the active graphics configuration and Lumos compatibility guidance. |
| The report is missing from the screenshot folder | Follow the fallback and fresh-verdict procedure in [Reports and Screenshots](../gametest/reports.md). |

## Next Steps

Use [Writing Tests](../gametest/writing.md) to add app-level checks and
[Reports and Screenshots](../gametest/reports.md) to gate a fresh workshop verdict.
