---
title: Set Up Glue GameTest
description: Add a development-only Fabric testmod, forward the runner properties, and run one test.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Set Up Glue GameTest

## Outcome

You will add a client-only `testmod`, register `lightworkshop:probe-smoke`, run it from your shell,
and receive a fresh passing report at:

```text
testmod/run/screenshots/gametest/lightworkshop_probe-smoke/report.txt
```

This first test stays on the title screen, so no world or application fixture is required.

## Add the Smallest Testmod

Start from a Loom-enabled Fabric workspace that already has the repository and credentials described
in [Getting Started](../getting-started.md). Include a development subproject:

```kotlin [settings.gradle.kts]
include("testmod")
```

Add GameTest only to that project. The explicit run directory makes the artifact paths in this guide
independent of IDE defaults.

```kotlin [testmod/build.gradle.kts]
plugins {
    id("fabric-loom")
}

dependencies {
    modImplementation("fr.lacaleche.glue:glue-gametest:<glue-version>")
}

loom {
    runs {
        named("client") {
            runDir(project.layout.projectDirectory.dir("run").asFile.absolutePath)

            providers.gradleProperty("glue.gametest").orNull?.let { testId ->
                vmArg("-Dglue.gametest=$testId")
            }
            if (providers.gradleProperty("glue.gametest.keepOpen").orNull == "true") {
                vmArg("-Dglue.gametest.keepOpen=true")
            }
        }
    }
}
```

Give the testmod its own client-only descriptor:

```json [testmod/src/main/resources/fabric.mod.json]
{
  "schemaVersion": 1,
  "id": "lightworkshop-test",
  "version": "1.0.0",
  "name": "Light Workshop Tests",
  "environment": "client",
  "entrypoints": {
    "client": [
      "dev.example.lightworkshop.test.LightWorkshopGameTests"
    ]
  },
  "depends": {
    "fabricloader": ">=0.14.6",
    "minecraft": "~1.21.8",
    "java": ">=21",
    "fabric-api": "*",
    "glue-gametest": "*"
  }
}
```

## Register One Test

Use the factory overload so each selected run gets a new script and fresh closure state:

```java [testmod/src/main/java/dev/example/lightworkshop/test/LightWorkshopGameTests.java]
package dev.example.lightworkshop.test;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import net.fabricmc.api.ClientModInitializer;

public final class LightWorkshopGameTests implements ClientModInitializer {
    private static final String PROBE_SMOKE = "lightworkshop:probe-smoke";

    @Override
    public void onInitializeClient() {
        GameTests.register(PROBE_SMOKE, LightWorkshopGameTests::probeSmoke);
    }

    private static GameTest probeSmoke() {
        return GameTest.create(PROBE_SMOKE)
                .expect("the client is initialized", context -> context.client() != null);
    }
}
```

The id passed to `GameTests.register` must exactly match the name returned by `GameTest.create`. A
`null` factory result or a mismatched name becomes a setup failure rather than a run under the wrong
id.

## Run the Test

From the workspace root, pass the test ID as a Gradle project property:

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
```
:::

`-Pglue.gametest=...` is a Gradle project property. The run configuration above forwards it as the
`-Dglue.gametest=...` JVM system property that the `GlueGameTest` client entrypoint reads.

## Check the Expected Report

The run directory is pinned to `testmod/run`, and `:` is replaced with `_` for the artifact folder.
The report should contain:

```text
[ 1/ 1] PASS  expect: the client is initialized  (1 ticks)
RESULT: PASS
```

No PNG is expected because this smallest test does not call `screenshot`.

## Property Details

For an IDE launch that does not use the Gradle project property, put this directly in the launch VM
options:

```text
-Dglue.gametest=lightworkshop:probe-smoke
```

To inspect the final session manually, forward the optional keep-open property:

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke -Pglue.gametest.keepOpen=true
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke' '-Pglue.gametest.keepOpen=true'
```
:::

The report is written when the script finishes even though the client remains open. Do not use
keep-open in automation because the Gradle task will continue waiting for the client.

If this testmod exercises a production subproject in the same build, add its named development
output without moving tests into production sources:

```kotlin [testmod/build.gradle.kts]
dependencies {
    implementation(project(path = ":lightworkshop", configuration = "namedElements"))
}
```

Keep `glue-gametest` and `lightworkshop-test` out of published dependency metadata and production
Fabric descriptors.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `-Pglue.gametest` has no effect | Keep the `vmArg("-Dglue.gametest=$testId")` forwarding in the client run, or use `-D` directly in IDE VM options. |
| Unknown test id | Check that `LightWorkshopGameTests` is a `client` entrypoint and that both occurrences of `lightworkshop:probe-smoke` match. |
| Factory setup failure | Return a non-null `GameTest` whose `name()` is the registered id. |
| Client classes load on a server | Move the descriptor and source back into the client-only `testmod`; the harness itself declares a client environment. |
| Report path differs | Check the configured `runDir`; GameTest writes relative to Minecraft's active game directory. |

## Next Steps

Continue with [Writing Tests](./writing.md) to open a world and capture evidence, then use
[Reports and Screenshots](./reports.md) to gate the result safely.
