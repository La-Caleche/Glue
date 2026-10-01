# Glue client GameTest helpers

`glue-gametest` provides sequential helpers for **Fabric Client GameTest** on Minecraft 1.21.8.
Tests implement `FabricClientGameTest`; Fabric owns discovery, the test thread, ticks, client/server
dispatch, worlds and screenshots. Glue adds compile-time test discovery, input-driven container
interactions, retrying assertions, asynchronous waits and scoped Iris toggles. The module is a development dependency,
with no dependency on another Glue module.

## Write a test

The runnable example is
[`InventoryClientTest`](../glue-showcase/src/test/e2e/java/fr/lacaleche/glue/testmod/gametest/InventoryClientTest.java).
After preparing a survival player with three diamonds in hotbar slot zero, its interaction is:

```java
ClientTest game = new ClientTest(context);
ContainerTest inventory = game.openInventory();

inventory.playerSlot(0).expectItem(Items.DIAMOND, 3);
inventory.playerSlot(0).click();
inventory.playerSlot(0).expectEmpty();
inventory.expectCarried(Items.DIAMOND, 3);
context.takeScreenshot("inventory-picked-up");

inventory.playerSlot(1).rightClick();
inventory.playerSlot(1).expectItem(Items.DIAMOND, 1);
inventory.expectCarried(Items.DIAMOND, 2);
inventory.playerSlot(1).click();
inventory.expectCarriedEmpty();
inventory.close();
```

These calls execute in order. `openInventory()` presses the configured inventory key;
`click()` and `rightClick()` move the cursor and use Fabric's mouse input. They do not call a
menu's mutation methods directly. Fabric injects events into Minecraft's input handlers, rather
than into the operating system.

### Assertions and lifecycle

- Call the helpers on Fabric's gametest thread, inside `runTest(ClientGameTestContext)`.
- Create a world with `try (TestSingleplayerContext world = context.worldBuilder().create())`.
  The world closes on success or assertion failure. Fabric requires the test to return to the title
  screen; it does not reset every static state held by mods.
- Use `world.getServer().runOnServer(...)` for fixture setup and server assertions. A client-side
  expectation checks client state; verify authoritative server state separately when it matters.
- `new ClientTest(context).expect(description, assertion)` retries `AssertionError` on the client
  thread while Fabric advances ticks. The timeout overload accepts a positive tick budget;
  the default is Fabric's 200 ticks. The failure retains the last assertion as its cause.
- Assertion callbacks must be short, read-only checks. Runtime exceptions and stale handles fail
  immediately. Never block a game-thread callback waiting for another tick or future.
- `playerSlot(0..8)` addresses hotbar slots, `playerSlot(9..35)` the main inventory.
  `slot(index)` instead addresses the container menu's index, including crafting and armor slots.
- Stack expectations compare item and exact count, not components. Use `expect` for custom checks.
- Container handles belong to one screen instance. They survive layout/GUI-scale changes but reject
  operations after the screen closes or is replaced. Click coordinates use the current container
  origin and GLFW window units, including the recipe-book offset.
- `openInventory()` expects gameplay and a non-creative player. For an already-open container, use
  `game.container(YourContainerScreen.class)`.

Screenshots remain Fabric's `context.takeScreenshot(...)` and `assertScreenshotEquals(...)` APIs.
A saved screenshot is an artifact, not a visual assertion. Fabric can generate absent comparison
templates when `fabric.client.gametest.testModResourcesPath` is configured; CI requiring strict
golden-image validation must not silently generate missing references.

## Asynchronous work and rendering

`game.await(description, future, Duration)` waits for an external operation while Fabric keeps
advancing the game. Create client-owned futures through `context.computeOnClient(...)`; never
`join()` one on the render thread. The deadline uses wall-clock time, and exceptions retain their
original runtime/error cause. The producer retains ownership of the future, including cancellation.
`game.waitUntil(description, predicate, Duration)` is the corresponding wall-clock condition wait.

`game.waitForWorldFrames(count)` observes actual world renders through one lazily installed Fabric
render event. It is useful after synchronous resource reloads, where catch-up ticks alone do not
prove that a new frame has rendered. A loaded world is required.

```java
try (IrisTest iris = new IrisTest(context)) {
    iris.setEnabled(false);
    context.takeScreenshot("without-shaders");
    iris.setEnabled(true);
    context.takeScreenshot("with-shaders");
}
```

Iris remains optional until `IrisTest` is used. A changed state waits for ten world frames; enabling
requires a valid selected shaderpack. Close this scope before closing the world so the initial
shader state can be restored and rendered.

## Run the showcase tests

```powershell
.\gradlew.bat :glue-showcase:listClientTests
.\gradlew.bat :glue-showcase:clientTest
.\gradlew.bat :glue-showcase:clientTest --tests web --tests scenes
```

Each task creates a fresh profile under `glue-showcase/build/run/<task-name>`, runs the
selected Fabric client tests, and exits. Screenshots go into that profile's `screenshots/` directory.
The profile is cleared before each run. The game process has a ten-minute Gradle task timeout,
in addition to the per-assertion tick timeouts. A timeout based on ticks alone cannot stop a frozen
render or server thread.

Without a selector, all automatically runnable tests are selected. Tests marked
`@ClientTestSpec(explicitOnly = true)` are listed but require explicit selection. The inventory
scenario checks pickup, deposit, synchronization, layout changes and stale handles; the default
suite also includes the scene, web, bundle, startup and viewport scenarios.

Optional-mod variants have named tasks:

```powershell
.\gradlew.bat :glue-showcase:clientTestSodium --tests inventory
.\gradlew.bat :glue-showcase:clientTestIris --tests viewport-sky
```

`clientTest` has neither Iris nor Sodium. The other tasks add only their declared rendering stack.
All three copy `src/test/assets/` into the isolated game directory before launch. Shader fixtures
live under `assets/shaderpacks/` and are selected by `assets/config/iris.properties`, just like a
normal game profile. See the [asset guide](../glue-showcase/src/test/assets/README.md) and
[scenario catalogue](../glue-showcase/README.md#fabric-client-tests).

## Automatic discovery

The showcase configures Loom's `fabricApi.configureTests` with a dedicated `gametest` source set,
mapped to `src/test/e2e/java` and `src/test/e2e/resources`. **Adding a public concrete implementation
of `FabricClientGameTest` is enough.** No registry or JSON entry needs to be maintained. Inheritance
from a fixture and public static nested classes are supported. Fabric needs a public no-argument
constructor; invalid implementations are diagnosed at compilation.

Default short names are kebab-case, stripping a final `ClientTest`, `GameTest` or `Test`:
`InventoryClientTest` becomes `inventory`, and nested `ViewportSky` becomes `viewport-sky`.
An optional `@ClientTestSpec("web-bundles")` keeps a custom short name next to its class.
Short names use lowercase letters/digits and `_.:-`; `auto` and `all` are reserved selectors.

For tests with external prerequisites, use metadata on the concrete class:

```java
@ClientTestSpec(value = "web-sites", explicitOnly = true)
```

The annotation is optional and is not inherited from fixtures or enclosing types. Shaderpack,
external-site and human-assisted tests in the showcase use `explicitOnly`; ordinary tests need no
annotation. The [existing web-site class](../glue-showcase/src/test/e2e/java/fr/lacaleche/glue/testmod/gametest/web/WebSitesClientTest.java)
shows the complete example.

### Selection

- `listClientTests` compiles and lists every discovered test, with `[explicit]` and its binary class
  name where applicable, without starting Minecraft.
- No option, or `--tests auto`, selects all tests not marked `explicitOnly`.
- Repeat `--tests` or separate selectors with commas. They accept a short name, a simple class name, a full binary class name,
  or `*` wildcards. For example, `inventory,scenes` or `*IrisHud`.
- `all` or `*` explicitly includes every test, including shader, network and manual tests.
- Exact ambiguous short names fail with the candidate classes; a fully qualified name disambiguates.
  A selector matching nothing, an empty selector or an empty discovered suite fails before launch.
- Selection is deterministic; repeated matches run only once. Metadata indexes are regenerated when
  sources are changed, renamed or removed.

Fabric's generated test-mod descriptor contains only the selected classes. `--tests` is a native
Gradle task option selecting these Fabric entrypoints, not Jupiter methods. The task subclasses
Loom's `RunGameTask`, so Minecraft launch arguments, natives and platform handling still belong to
Loom. Test outputs do not enter showcase or library jars. Ordinary JUnit tests still run with `test`.

### Build integration

The JDK annotation processor is shipped in `glue-gametest` and registered through Java's service
loader. Add the module to both the test compilation and annotation-processor configurations:

```kotlin
dependencies {
    "gametestImplementation"(project(path = ":glue-gametest", configuration = "namedElements"))
    "gametestAnnotationProcessor"(project(path = ":glue-gametest", configuration = "namedElements"))
}
apply(from = rootProject.file("gradle/client-tests.gradle.kts"))
```

Configure the `gametest` source set through `fabricApi.configureTests` with both generated run
types disabled; this convention supplies the launch tasks. The reusable
[Gradle integration](../gradle/client-tests.gradle.kts) reads the generated
`META-INF/glue/client-tests.tsv`, applies the selection and writes a per-task test-mod jar under
`build/client-test-mods/`. The source descriptor keeps its `fabric-client-gametest` array empty.
Compiled resources are never rewritten per selection. Each task replaces the test source-set roots
on its runtime classpath with its own jar, which contains the selected entrypoints and test resources,
but not the game-profile assets. Fabric remains the only test executor.
For another project, use the published Glue coordinate on its annotation-processor configuration
and reuse the integration script with its `gametest` source set and test-mod descriptor. The script
uses the root project's already resolved Loom plugin API. The optional `sodium` and `iris` runtime
source sets are supplied by [render-profiles.gradle.kts](../gradle/render-profiles.gradle.kts) in this repository.

Discovery uses javac's type model, not reflection or a runtime classpath scan. It never instantiates
tests or bootstraps Minecraft. The processor is deliberately non-incremental because it indexes
unannotated source types too: each compilation reconstructs the complete index. Only classes in
that Java compilation are candidates; dependency classes are used to resolve inheritance, not
automatically added to the suite.

## Migration from the step runner

The previous step DSL, runtime registries, client entrypoint and report-text runner have been
removed. Tests now implement Fabric's interface and use ordinary Java control flow and
try-with-resources. The showcase's twelve previous scenarios live under `src/test/e2e` alongside
the inventory example. Main mod initialization no longer registers tests or requires this module.

The showcase's `WorldClientTest` is a shared disposable-world fixture, with a failure capture and
screen cleanup. `WebTestPage` shares DOM queries, input and asynchronous waits between its web
and scene tests. It stays in the showcase test sources so this library does not acquire a dependency
on Glue Web. There is no parallel execution engine or string-based tool registry.

Rendering tests build a reproducible arena instead of modifying an existing world. The Lumos
scenarios left with Lumos for its own repository, where they run on these helpers.

### Verification notes

The inventory, scenes, bundles, startup indicator, full web and viewport scenarios have passed in
a real Windows client without Iris/Sodium. The five shader scenarios, since moved to Lumos, had also passed with
Complementary Unbound 5.7.1 + Euphoria Patches 1.8.6, exercising actual shader toggles and rendered
frame waits. Captures are diagnostic evidence; these scenarios do not claim pixel-golden validation.

The external-site scenario also passed navigation, input and capture on La Calèche, Google and
YouTube; Google returned a challenge page, so search-result content was not validated. The native-dialog
scenario requires a human to cancel OS dialogs and was not exercised during this migration. A previous deliberately failing
inventory test propagated its assertion into a Gradle failure, but ended with Windows native status
`0xC0000409` during JCEF shutdown; that failure-shutdown path still needs dedicated investigation.

The public site is maintained in `glue-docs`; its matching page requires a separate update when that
repository is accessible.
