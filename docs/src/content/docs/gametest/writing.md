---
title: Write Glue GameTests
description: Build fresh scripted tests with client steps, server work, tools, and rendered-frame waits.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Write Glue GameTests

## Outcome

You will grow the title-screen check from [Setup](./setup.md) into a world-backed script that waits
for valid state, captures `01-probe-smoke.png`, and records each executed step in the report.

## Start with a Fresh Factory

Keep registration in the testmod client entrypoint and build the graph only when selected:

```java
private static final String PROBE_SMOKE = "lightworkshop:probe-smoke";

@Override
public void onInitializeClient() {
    GameTests.register(PROBE_SMOKE, LightWorkshopGameTests::probeSmoke);
}
```

The factory form matters because waits, futures, counters, and UI objects are commonly captured by
step closures. `GameTests.register(GameTest)` is valid for a stateless graph, but it reuses the same
instance created at registration time.

## Build the Smallest World Test

Create a singleplayer world named `GameTest World` once, then replace the setup factory with:

```java
private static GameTest probeSmoke() {
    return GameTest.create(PROBE_SMOKE)
            .openWorld("GameTest World")
            .expect("the player is alive", context -> context.player().isAlive())
            .selectEmptyHand()
            .screenshot("probe-smoke");
}
```

`openWorld` requests that existing world, waits for a level, player, and cleared loading screen, then
adds a 40-tick settling delay. The guarded `player()` accessor fails with a descriptive message if a
test reaches it before the player exists.

Run the same namespaced id:

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
```
:::

## Check the Expected Artifacts

The script has six steps because `openWorld` expands to the open request, the world-ready wait, and
the settling delay. A passing run ends with these statuses; the screenshot tick count depends on how
quickly Minecraft's I/O callback completes:

```text
[ 5/ 6] PASS  select an empty hotbar slot  (1 ticks)
[ 6/ 6] PASS  screenshot 'probe-smoke'  (<capture ticks> ticks)
RESULT: PASS
```

The game directory now contains:

```text
testmod/run/screenshots/gametest/lightworkshop_probe-smoke/
|-- 01-probe-smoke.png
`-- report.txt
```

See [Reports and Screenshots](./reports.md) before using those files as an automated verdict.

## Work with TestContext

Every action, predicate, raw step, and tool receives a `TestContext`:

| Method | Contract |
| --- | --- |
| `client()` | Returns the live `Minecraft` instance. Runner steps execute from the end-client-tick callback. |
| `player()` | Returns the local player or throws a descriptive `IllegalStateException`. |
| `level()` | Returns the client level or throws when no level is loaded. |
| `server()` | Returns the integrated server or throws outside singleplayer. |
| `renderedWorldFrames()` | Counts world frames drawn since the runner was armed; it does not advance on the title screen. |
| `saveScreenshot(label, onDone)` | Starts a low-level asynchronous capture; the callback runs on Minecraft's screenshot I/O thread. |
| `log(message)` | Adds a diagnostic line to the game log. |

Prefer `GameTest.screenshot` over `saveScreenshot`. The DSL method safely publishes the I/O-thread
outcome back to the client tick, waits for Minecraft to report a successful PNG write, and fails the
step when the write fails.

## Run Server Work Safely

`run`, raw `step` ticks, waits, and expectations run on the client thread. Use `runOnServer` for a
short integrated-server mutation:

```java
test.runOnServer("set the workshop to midnight",
        server -> server.overworld().setDayTime(18000));
```

The step submits the action to the server executor and polls its future from client ticks. An
exception or `AssertionError` raised by the action is unwrapped and recorded as the step failure.

Keep the action non-blocking. A step timeout can stop waiting, but it cannot unwind work already
blocking Minecraft's server thread; locks, latches, network calls, and long I/O can also prevent
client shutdown. Poll later conditions with `waitUntil` instead.

The convenience methods `give`, `setDayTime`, and `teleport` use this same server-thread path and
therefore require an integrated server.

## Wait for Rendered Frames

Client ticks are not rendered frames. A synchronous renderer reload can block the client and then
allow several ticks to catch up without drawing. Use the frame counter when the next assertion or
screenshot must observe newly rendered output:

```java
test.step("wait for ten rendered world frames", GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
    private int firstFrame = -1;

    @Override
    public boolean tick(TestContext context) {
        if (this.firstFrame < 0) this.firstFrame = context.renderedWorldFrames();
        return context.renderedWorldFrames() - this.firstFrame >= 10;
    }
});
```

Declare this mutable state inside the test factory. The counter only advances while a world is being
rendered, so put the step after `openWorld` or `waitForWorld`.

## Use Tools Sparingly

A `GameTool` is a namespaced, mod-provided verb for an application action that does not belong in the
generic DSL. Register it during client initialization with `GameTools.register(id, tool)`, then call
it with `test.tool(id, args...)`.

`GameTool.start(context, arguments)` runs once on the invocation's first tick and returns a
`GameTest.StepTick` that is polled until completion. Tool lookup is deferred until execution, so
registration order does not matter. Re-registering an id replaces the previous tool; an unknown id
fails with the registered id list.

Do not wrap ordinary calls in tools. A direct `run` is clearer when one test can invoke an existing
client API. Add a tool when several tests need the same high-level app action or when the action must
own a multi-tick readiness check.

### Optional Iris Tool

The built-in `glue-gametest:iris-shaders` tool accepts exactly one argument, `"true"` or `"false"`:

```java
test.tool(IrisShadersTool.ID, "true")
        .screenshot("with-shaders")
        .tool(IrisShadersTool.ID, "false");
```

Iris is a `compileOnly` dependency of `glue-gametest`. Loading or using the rest of the harness
without Iris is safe because the tool checks that mod id `iris` is loaded before resolving its API.
Invoking this tool without Iris fails the step with a clear message.

When a toggle changes state, the tool verifies the requested state and waits for 10 newly rendered
world frames. If the state already matches, it completes immediately. Invoke it only after a world
is rendering; no frame-based settle can complete on the title screen.

## Keep Automated Runs Unblocked

Application UI can use `GameTests.armed()` to detect a non-empty `glue.gametest` system property.
When it returns `true`, skip optional first-run, recovery, and confirmation prompts that would block
unattended input. The method does not verify that the requested ID is registered, answer prompts, or
change application state for you.

## Method Catalog

`GameTest.create(name)` starts a mutable builder. Every method below appends a step and returns the
same builder unless noted otherwise. `DEFAULT_TIMEOUT` is 600 ticks and `LONG_TIMEOUT` is 2,400
ticks.

| Method | What it adds |
| --- | --- |
| `name()` | Returns the test name; it must match the registered id. |
| `steps()` | Returns a copy of the current ordered step list. |
| `step(description, timeoutTicks, tick)` | A raw `StepTick`, polled once per client tick until true, failure, or timeout. |
| `run(description, action)` | A one-shot action on the client thread. |
| `runOnServer(description, action)` | A short integrated-server action plus a client-side future wait. |
| `waitTicks(ticks)` | A client-tick delay with a `ticks + 100` timeout. |
| `waitUntil(description, condition)` | A polled condition using `DEFAULT_TIMEOUT`. |
| `waitUntil(description, condition, timeoutTicks)` | A polled condition with an explicit timeout. |
| `expect(description, check)` | An immediate assertion; false raises an `AssertionError`. |
| `tool(id, args...)` | A lazily resolved `GameTool` invocation using `LONG_TIMEOUT`. |
| `openWorld(levelName)` | Opens an existing singleplayer world, waits for ready client state, then waits 40 ticks. |
| `waitForWorld()` | Waits for level, player, and no screen, then waits 40 ticks. |
| `fullscreen()` | Enters fullscreen when the window is not already fullscreen. |
| `give(items...)` | Adds one of each item to every integrated-server player's inventory. |
| `setDayTime(time)` | Sets the integrated server overworld's day time. |
| `teleport(x, y, z)` | Teleports every integrated-server player. |
| `lookAt(x, y, z)` | Rotates the local player toward a world position. |
| `chat(message)` | Sends chat, or a command when the value starts with `/`. |
| `selectEmptyHand()` | Selects the first empty hotbar slot when one exists. |
| `closeScreen()` | Sets the current client screen to `null`. |
| `screenshot(label)` | Requests a capture on its third poll and waits for the asynchronous save outcome, with a 200-tick timeout. |

Steps run strictly in order. When a step throws or times out, later steps are skipped rather than
reported as passes or failures.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `openWorld` fails | Confirm an existing singleplayer level has exactly the supplied level name. |
| `player()`, `level()`, or `server()` throws | Add `openWorld` or `waitForWorld` before guarded access; `server()` additionally requires singleplayer. |
| A server step times out and shutdown hangs | Remove blocking work from `runOnServer`; poll from client steps instead. |
| A frame wait times out | Ensure a world is actively rendering and the client is not held on a screen or renderer reload. |
| A screenshot step fails | Read the failure detail and game log; Minecraft reported that the PNG was not saved. |
| Mutable state leaks between runs | Move the state into a supplier registered with `GameTests.register(name, factory)`. |

## Next Steps

Use [Reports and Screenshots](./reports.md) to interpret the artifacts, or apply the full pattern in
[Test the Lumen Probe Workshop](../workshop/testing.md).
