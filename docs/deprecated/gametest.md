# Scripted In-Game Tests (glue-gametest)

`glue-gametest` (client-only, dev-time) drives a live Minecraft client through a scripted test: a
named, ordered list of steps executed one per client tick. The runner saves screenshots and a
per-step `report.txt` under `screenshots/gametest/<name>/`, then closes the game.

Without the arming property the module is inert — it can sit in a dev runtime without affecting
anything.

## Adding the module

`glue-gametest` is published to the same private Reposilite as the runtime libraries (see
[Getting Started](getting-started.md) for the repository block). It drives a live client, so it
belongs to a development-only Gradle subproject — a testmod — whose output ships with nothing:

```kotlin
// testmod/build.gradle.kts
dependencies {
    modImplementation("fr.lacaleche.glue:glue-gametest:${glueVersion}")
    implementation(project(path = ":my-mod", configuration = "namedElements"))
}
```

`modImplementation` is the right configuration *there* because the whole subproject is
development-only. The same line in your production module puts the runner on that module's main
source set — and into the jar you release. Prose about dev-time usage does not change a Gradle scope.

To keep one Gradle project and isolate a source set instead, attach the dependency to that source
set's own Loom configuration. Its name follows from how you declared the source set and the Loom
`mods` block, so take it from your own build rather than from an example here.

The testmod carries its own `fabric.mod.json` and lists `glue-gametest` in *its* `depends`. Leave it
out of the descriptor you ship altogether: the runner is client-only, so a hard dependency on it in a
released mod also stops that mod loading on a dedicated server.

## Running a test

Arm the runner with a JVM property naming one registered test:

```
-Dglue.gametest=<name>              # run this test, write artifacts, close the game
-Dglue.gametest.keepOpen=true      # …but leave the session up for a human afterwards
```

In this repository the showcase forwards Gradle properties:

```
.\gradlew.bat :glue-showcase:runClient -Pglue.gametest=<name> -Pglue.showcase.quickplay=<world>
```

The test is resolved on the first client tick, after every mod's client entrypoint has run, so
registration order between mods does not matter. A step that throws or exceeds its timeout fails
the test: the runner requests a best-effort `FAILED` screenshot, skips the remaining steps, writes
the failing verdict, and waits 60 ticks before closing so asynchronous PNG writes normally have time
to finish. That terminal capture is a diagnostic, not a guarantee — an ordinary `screenshot(label)`
step is the one that completes only after its write is reported successful.

Every way a run can end — an unusable output directory, a test factory that throws, an unknown
test name, a failing or timed-out step, or success — emits the verdict and then closes the
client. An unattended run therefore never sits there with the session open and no verdict.

## Registering tests

Register at client init through `GameTests`:

```java
public static void register(String name, Supplier<GameTest> factory)  // preferred
public static void register(GameTest test)
```

The factory form is preferred: steps routinely capture mutable state in their closures (counters,
futures, pre-built UI objects), and the runner invokes the factory when it selects the test, so
every run gets a freshly built step graph with fresh closure state — and only the selected test's
graph is ever constructed. The built test's `name()` must equal the registered name; a mismatch
fails the run loudly.

```java
GameTests.register("mymod:editor-smoke", MyTests::editorSmoke);

private static GameTest editorSmoke() {
    return GameTest.create("mymod:editor-smoke")
            .waitForWorld()
            .tool(IrisShadersTool.ID, "true")
            .screenshot("editor-open");
}
```

`register(GameTest)` registers an already-built instance whose closure state is created once per
game launch — fine for a stateless step graph, single-shot otherwise.

`GameTests.armed()` reports whether the launch runs a scripted test; mod UI may consult it to skip
prompts that would block an unattended run.

## The step DSL

`GameTest.create(name)` starts a fluent script; each verb appends a step. A step is polled every
tick until it returns true, with a per-step timeout (`DEFAULT_TIMEOUT` 30 s, `LONG_TIMEOUT` 2 min
for world joins and tools).

| Verb | Effect |
|---|---|
| `step(description, timeoutTicks, tick)` | Raw step: full control over per-tick logic. |
| `tool(id, args…)` | Invokes a registered `GameTool` (see below). |
| `run(description, action)` | Runs an action once on the client thread. |
| `runOnServer(description, action)` | Schedules a short, non-blocking mutation on the integrated server thread and waits for it. |
| `waitTicks(n)` / `waitUntil(description, condition[, timeout])` | Delays and polls. |
| `expect(description, check)` | Fails the test immediately if the check does not hold. |
| `openWorld(levelName)` / `waitForWorld()` | Singleplayer join plus settle. |
| `fullscreen()`, `give(items…)`, `setDayTime(t)`, `teleport(x, y, z)`, `lookAt(x, y, z)`, `chat(msg)`, `selectEmptyHand()`, `closeScreen()` | Game-agnostic scripting verbs. |
| `screenshot(label)` | Completes once the PNG is on disk; fails the step if the capture never wrote. |

`runOnServer` waits on the server's own completion, so an action that throws fails the step with its
own exception rather than timing out. It cannot rescue one that *blocks*: a step timeout stops the
runner from waiting, but a task already wedged on Minecraft's server thread stays wedged, and client
shutdown then waits on the integrated server. Poll for conditions with `waitUntil` on the client
instead, and keep locks, latches, network calls and long I/O out of the action.

Steps receive a `TestContext`: the live `Minecraft` client, guarded `player()` / `level()` /
`server()` accessors that throw with a clear message instead of returning null, `saveScreenshot`,
`log`, and `renderedWorldFrames()`.

`renderedWorldFrames()` is the count of world frames drawn since the run was armed. Ticks and
frames are different clocks — after a long synchronous reload the client catches up several ticks
without drawing anything — so a step that must observe real rendering (a rebuilt pipeline, newly
mounted UI) should wait on this count rather than on `waitTicks`. Only frames that render a level
advance it: it stands still on the title screen.

## Tools

A `GameTool` is a named, mod-contributed test verb — the extension point that keeps mod knowledge
out of the DSL. Register at client init, invoke by id from any test:

```java
GameTools.register("mymod:open-editor", (ctx, args) -> tickThatOpensTheEditor);
…
test.tool("mymod:open-editor", "my-file.vfx");
```

`start` is called once per invocation on its first tick and returns the tick that is then polled
until it completes. Tools are resolved lazily at step execution, so registration order between
mods does not matter; an unregistered id fails the step naming every registered id.

### Built-in: `IrisShadersTool`

The module registers one built-in tool under `IrisShadersTool.ID`
(`"glue-gametest:iris-shaders"`): it toggles the active Iris shaderpack for A/B captures.

```java
test.tool(IrisShadersTool.ID, "true")   // enable the shaderpack
    .screenshot("with-shader")
    .tool(IrisShadersTool.ID, "false")  // disable it
    .screenshot("without-shader");
```

One argument, `"true"` or `"false"`. The step applies the toggle, verifies Iris reports the
requested state, then holds until the rebuilt pipeline has drawn 10 world frames — no manual
`waitTicks` needed after it. If the requested state already holds, the step completes immediately
without settling.

The settle counts drawn frames, not client ticks: a shaderpack reload blocks the client thread and
Minecraft then catches up the ticks it missed without rendering, so a tick-counted settle could let
the next step screenshot a pipeline that had not drawn once. The step consequently needs a
rendering world — with nothing drawing, it fails on its timeout instead of passing blind.

Iris is optional: `glue-gametest` compiles against the Iris API but never resolves it at runtime
unless a test invokes the tool. Invoking it without Iris installed fails the step with a clear
message.

## Output

Everything lands under `screenshots/gametest/<name>/` (the name is made filesystem-safe:
`mymod:editor-smoke` → `mymod_editor-smoke`): numbered screenshots (`01-<label>.png`) from screenshot
steps that completed, a best-effort `FAILED` capture when an *executing* step fails, and
`report.txt`.

The report holds one `PASS` or `FAIL` line per **executed** step, then the `RESULT:` line — the steps
skipped after a failure get no lines of their own. A setup failure is different: an unknown test name
or an unusable output directory is caught before a test context exists, so it records an `ERROR:`
line and `RESULT: FAIL` and promises no screenshot at all. The directory is cleared at the start of
each run, and prepared before the test is built, so even that run leaves a report.

`report.txt` is the verdict an unattended run leaves behind: the client JVM exits 0 either way, so
gate CI on the artifact rather than on the exit code. When the output directory itself is the
failure — it could not be created or cleared — the report goes to `gametest-<name>-report.txt`
in the game directory instead, and to the log if that write fails too. Treat a missing
`report.txt` as a failed run, then read the fallback path and the log for the reason: the run may
have ended without ever reaching a terminal state (a crash, or a hang before the first tick), or it
may have emitted its verdict through one of the fallbacks.

A gate has to establish that the verdict it reads belongs to the run it just started. Both
destinations are therefore cleared before launch — a report left by an earlier invocation reads
exactly like this one's — and whichever one the run created is the one that decides:

```powershell
$name = "mymod_editor-smoke"
$primary  = "run/screenshots/gametest/$name/report.txt"
$fallback = "run/gametest-$name-report.txt"

foreach ($path in @($primary, $fallback)) {
    if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force -ErrorAction Stop }
}

.\gradlew.bat :glue-showcase:runClient -Pglue.gametest=mymod:editor-smoke -Pglue.showcase.quickplay=Demo

$report = if (Test-Path -LiteralPath $primary) { $primary } `
    elseif (Test-Path -LiteralPath $fallback) { $fallback } `
    else { throw "the run produced no verdict" }

if (-not (Select-String -Quiet -LiteralPath $report -Pattern '^RESULT: PASS$')) {
    throw "the run did not pass; verdict in $report"
}
```

Failing the preflight is deliberate: if an old verdict cannot be removed, nothing the run writes
afterwards can be told apart from it. The runner covers its own half — it clears the fallback and
overwrites the primary, and when it cannot overwrite the primary the verdict it does write carries a
`WARNING:` line naming the artifact that must not be trusted.
