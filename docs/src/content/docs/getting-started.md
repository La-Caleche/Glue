---
title: Getting Started
description: Add Glue Core to a Fabric 1.21.8 mod, declare the matching Fabric dependency, and verify both compile and launch.
artifact: glue-core
modId: glue
environment: client and server
---

# Getting Started

By the end of this page, **Light Workshop** compiles with `glue-core`, Fabric loads the matching
`glue` mod, and a common entrypoint writes a success message during launch.

Glue targets Minecraft 1.21.8, Java 21, Fabric Loader, Fabric API, and official Mojang mappings.
You also need read credentials for the private La Calèche Maven repository.

## 1. Add Repository Access

Configure the private repository in the consumer's `build.gradle.kts`. The environment variables
work well in CI; the Gradle properties are convenient on a development machine.

```kotlin [build.gradle.kts]
repositories {
    maven {
        name = "La Calèche Private"
        url = uri("https://reposilite.lacaleche.cc/private")
        credentials {
            username = providers.environmentVariable("REPOSILITE_TOKEN_NAME")
                .orElse(providers.gradleProperty("lc.reposilite.readonly.name")).orNull
            password = providers.environmentVariable("REPOSILITE_TOKEN_SECRET")
                .orElse(providers.gradleProperty("lc.reposilite.readonly.token")).orNull
        }
    }
}
```

For local development, put the credentials in the user-level file, never in the mod repository.

```properties [~/.gradle/gradle.properties]
lc.reposilite.readonly.name=<username>
lc.reposilite.readonly.token=<token>
```

## 2. Add Core

Start with the smallest shared artifact. Replace `<glue-version>` with the version supplied for
your project, and use that same version for every Glue module you add later.

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-core:<glue-version>")
}
```

The Maven artifact is `glue-core`, but its Fabric mod ID is `glue`. Declare that ID alongside the
normal Fabric requirements in Light Workshop's descriptor.

```json [src/main/resources/fabric.mod.json]
{
  "schemaVersion": 1,
  "id": "lightworkshop",
  "version": "1.0.0",
  "name": "Light Workshop",
  "environment": "*",
  "entrypoints": {
    "main": ["dev.example.lightworkshop.LightWorkshop"]
  },
  "depends": {
    "fabricloader": ">=0.14.6",
    "minecraft": "~1.21.8",
    "java": ">=21",
    "fabric-api": "*",
    "glue": "<glue-version>"
  }
}
```

## 3. Add the Entrypoint

Keep the first launch deliberately small. Content registration begins in the next workshop step.

```java [src/main/java/dev/example/lightworkshop/LightWorkshop.java]
package dev.example.lightworkshop;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LightWorkshop implements ModInitializer {
    public static final String MOD_ID = "lightworkshop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Light Workshop is ready");
    }
}
```

## 4. Compile and Run

Run the consumer mod's normal Loom tasks from its project root.

::: code-group
```bash [Unix]
./gradlew classes
./gradlew runClient
```

```powershell [PowerShell]
.\gradlew.bat classes
.\gradlew.bat runClient
```
:::

**Expected result:** `classes` succeeds. The development client reaches the title screen, and its
log contains both Glue's ready message and `Light Workshop is ready`.

::: tip Continue to a visible result
The [Lumen Probe milestone](./workshop/probe.md) turns this verified setup into a registered,
textured item you can receive with one command.
:::

## Optional Client Module

Add a client artifact only when a client-side project or module needs its features. For example, a
client-only Light Workshop extension can add rendering infrastructure like this:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-render:<glue-version>")
}
```

Merge these fields into that extension's Fabric descriptor so it is client-only and names the
corresponding mod ID.

```json [fabric.mod.json]
{
  "environment": "client",
  "depends": {
    "glue-render": "<glue-version>"
  }
}
```

::: warning Keep dedicated servers free of client artifacts
Do not make a shared or server mod require `glue-render` or `glue-lumos-client`.
A client entrypoint isolates client classes, but it does not make a hard Fabric
dependency server-safe. Keep the imports behind that entrypoint and put required client-only mod IDs
in a separate mod or module whose descriptor has `"environment": "client"`. If one descriptor must
load on both sides, make the client integration optional and guard it at runtime instead.
:::

::: details Why both Gradle and fabric.mod.json are required
Gradle resolves the classes and runtime jars used by development and compilation. Fabric Loader
uses `fabric.mod.json` to validate the installed mod graph. Maven transitivity does not replace
Fabric dependency metadata, so always declare the mod ID for every Glue module your mod requires.
:::

## Next Steps

- [Build the Lumen Probe](./workshop/probe.md) for the first visible milestone.
- [Choose Modules](./modules.md) before adding a second artifact.
- [Follow the Core learning path](./core/index.md) for registries, items, blocks, and utilities.
