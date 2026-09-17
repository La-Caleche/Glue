---
title: Read GameTest Reports
description: Interpret screenshots and reports, require a fresh verdict, and diagnose fallback output.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Read GameTest Reports

## Outcome

You will identify the report that belongs to the current run, require an exact `RESULT: PASS` line,
and keep a normal client shutdown from hiding a failed script.

## Run and Locate One Result

Run the test configured in [Setup](./setup.md):

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
```
:::

For `lightworkshop:probe-smoke`, the runner replaces characters outside `[A-Za-z0-9._-]` with `_`.
The primary artifacts are therefore:

```text
testmod/run/screenshots/gametest/lightworkshop_probe-smoke/
|-- 01-probe-smoke.png
`-- report.txt
```

The PNG exists only if the test requested that label. The title-screen test from Setup produces just
`report.txt`; the world-backed test from [Writing Tests](./writing.md) produces both files.

## Read the Expected Report

Each executed step contributes one line with its position, status, description, tick count, and an
optional failure detail. Every written report contains one verdict line:

```text
RESULT: PASS
RESULT: FAIL
```

A representative passing report is shown below. World and screenshot timings vary:

```text
[ 1/ 6] PASS  open world 'GameTest World'  (12 ticks)
[ 2/ 6] PASS  wait until world and player are loaded  (8 ticks)
[ 3/ 6] PASS  wait 40 ticks  (40 ticks)
[ 4/ 6] PASS  expect: the player is alive  (1 ticks)
[ 5/ 6] PASS  select an empty hotbar slot  (1 ticks)
[ 6/ 6] PASS  screenshot 'probe-smoke'  (4 ticks)
RESULT: PASS
```

If a step throws or exceeds its timeout, that line is `FAIL`, remaining steps are omitted, and the
result is `FAIL`. Unknown ids, throwing factories, mismatched factory names, and unusable output
setup add an `ERROR:` line because no step could run.

<DocImage title="Report and screenshot pairing" description="A numbered PNG sits beside report.txt so the textual verdict identifies the run while the image preserves its rendered evidence." />

## Understand Screenshot Semantics

`screenshot(label)` waits until its third poll before requesting a capture of the last rendered
frame. The step does not pass merely because a request was made: it waits for Minecraft's screenshot
I/O callback and requires the success response. A failed or unrecognized response fails the step.

Captures are numbered in request order, such as `01-probe-smoke.png` and `02-after-toggle.png`. Use
filesystem-safe labels because the label becomes part of the filename.

After an ordinary step fails, the runner requests a best-effort numbered `FAILED` screenshot. That
diagnostic capture does not hold the verdict open and is not guaranteed. The runner writes the
report immediately, then normally waits 60 client ticks before closing so asynchronous PNG work has
time to finish. A setup failure has no test context and promises no screenshot.

The runner clears regular files directly inside the primary artifact folder when a run starts. It
does not use screenshots as assertions about pixels: a successful screenshot step proves the PNG was
written, while visual correctness still requires review or a separate image-comparison system.

## Gate a Fresh Verdict

Remove both possible reports before launch, require a successful client process, then require one
fresh report with an exact passing line:

::: code-group
```bash [Unix]
set -euo pipefail

folder='lightworkshop_probe-smoke'
game_dir='testmod/run'
primary="$game_dir/screenshots/gametest/$folder/report.txt"
fallback="$game_dir/gametest-$folder-report.txt"

rm -f -- "$primary" "$fallback"

if ! ./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke; then
    echo 'the client process failed' >&2
    exit 1
fi

if [[ -f "$primary" ]]; then
    report=$primary
elif [[ -f "$fallback" ]]; then
    report=$fallback
else
    echo 'the run produced no fresh GameTest report' >&2
    exit 1
fi

if ! grep -Fxq 'RESULT: PASS' "$report"; then
    echo "the scripted test did not pass; read $report" >&2
    exit 1
fi
```

```powershell [PowerShell]
$folder = 'lightworkshop_probe-smoke'
$gameDir = 'testmod/run'
$primary = "$gameDir/screenshots/gametest/$folder/report.txt"
$fallback = "$gameDir/gametest-$folder-report.txt"

foreach ($path in @($primary, $fallback)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force -ErrorAction Stop
    }
}

& .\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke'
if ($LASTEXITCODE -ne 0) {
    throw "the client process exited with code $LASTEXITCODE"
}

$report = if (Test-Path -LiteralPath $primary) {
    $primary
} elseif (Test-Path -LiteralPath $fallback) {
    $fallback
} else {
    throw 'the run produced no fresh GameTest report'
}

if (-not (Select-String -Quiet -LiteralPath $report -Pattern '^RESULT: PASS$')) {
    throw "the scripted test did not pass; read $report"
}
```
:::

Matching a whole line matters. The report writer escapes carriage returns and line feeds embedded in
step failures, so failure text cannot inject a second verdict line.

::: warning Process success is not test success
A failed or timed-out script normally calls Minecraft's regular stop path. Gradle can therefore exit
with code `0` while the report says `RESULT: FAIL`. Automation must require both a successful process
and a fresh passing report.
:::

## Follow Fallback Output

The primary report belongs beside the screenshots. If it cannot be written, the runner tries this
file in the game directory:

```text
testmod/run/gametest-lightworkshop_probe-smoke-report.txt
```

Before setup, the runner tries to remove a fallback report left by an earlier invocation. Failure to
remove it adds a `WARNING:` to the new verdict. If primary writing fails, the fallback also gains a
`WARNING:` naming the primary file that must not be trusted. If neither destination accepts the
report, the complete verdict is emitted only to the game log.

A missing report is always a failed gate. Check the fallback and log for a filesystem error; if
neither contains a verdict, the client may have crashed or hung before reaching a terminal state.

## Keep the Client Open for Review

For a local inspection run only:

::: code-group
```bash [Unix]
./gradlew :testmod:runClient -Pglue.gametest=lightworkshop:probe-smoke -Pglue.gametest.keepOpen=true
```

```powershell [PowerShell]
.\gradlew.bat :testmod:runClient '-Pglue.gametest=lightworkshop:probe-smoke' '-Pglue.gametest.keepOpen=true'
```
:::

The report is still written as soon as the run finishes. The client remains open, so close it
manually after inspecting the state. Do not use this flag in the gating command.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Gradle succeeds but the script failed | Read the exact `RESULT:` line; process exit and scripted verdict are separate signals. |
| Only a fallback report exists | Read its `WARNING:` and fix permissions, path type, or output-directory preparation for the primary location. |
| Both reports are missing | Treat the run as failed; inspect the game log for report-write errors, an early crash, or a hang. |
| An old pass appears current | Delete primary and fallback before launch as shown in the fresh-verdict gate. |
| No failure PNG exists | The terminal `FAILED` capture is best-effort; the report remains authoritative. |
| The PNG exists but looks wrong | Screenshot success verifies the write, not the visual content; review it or add external image comparison. |

## Next Steps

Return to [Writing Tests](./writing.md) for step and threading contracts, or run the end-to-end
[Lumen Probe workshop test](../workshop/testing.md).
