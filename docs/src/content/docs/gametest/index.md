---
title: Glue GameTest
description: Run named live-client checks and collect reviewable evidence from a development test mod.
artifact: glue-gametest
modId: glue-gametest
environment: client development
---

# Glue GameTest

Glue GameTest runs one named, ordered script inside a real Minecraft client. It is useful when a
feature depends on client screens, an integrated server, rendered frames, or screenshots and cannot
be covered by an ordinary unit test alone.

The outcome is a text verdict and, when requested, numbered PNG captures. A failed step stops the
script, records the failure, and normally closes the client after giving asynchronous captures time
to finish.

::: warning Development harness only
`glue-gametest` is a client-environment Fabric mod for development runs. Put it in a dedicated
`testmod`; do not add it to a production artifact or a descriptor that must load on a dedicated
server.
:::

## Preview a Test

A test is registered by namespaced id and built from a factory. This one can run from the title
screen:

```java
GameTests.register("lightworkshop:probe-smoke", () ->
        GameTest.create("lightworkshop:probe-smoke")
                .expect("the client is initialized", context -> context.client() != null));
```

The selected factory is resolved on the first client tick, after client entrypoints have registered
their tests. Each step is then polled on the client tick until it passes, throws, or exceeds its
timeout.

## Follow the Runner

<DocImage title="Runner flow" description="A registered test is selected by JVM property, built on the first client tick, executed one step at a time, and finished with a report plus any requested screenshots." />

```text
register factory -> select id -> build fresh script -> run steps -> write artifacts -> close client
```

Without the `glue.gametest` JVM system property, the module only registers its built-in tools. It
does not attach a runner to client ticks or world rendering.

## Choose the Next Task

- [Set up a development testmod](./setup.md) and run the title-screen smoke test.
- [Write progressive tests](./writing.md) that use worlds, server work, tools, and rendered frames.
- [Read and gate reports](./reports.md) without mistaking a normal process exit for a passing test.
- [Test the Lumen Probe workshop](../workshop/testing.md) as a complete consumer example.

## Troubleshooting

| Symptom | First check |
| --- | --- |
| Nothing is armed | Confirm the client JVM received `-Dglue.gametest=lightworkshop:probe-smoke`; a Gradle `-P` property must be forwarded. |
| The id is unknown | Register it from a Fabric client entrypoint. The setup error lists the ids that were registered. |
| The client stays open | Remove `-Dglue.gametest.keepOpen=true` from unattended runs. |
| No report appears | Treat the run as failed and follow the primary, fallback, and log checks in [Reports and Screenshots](./reports.md). |

## Next Steps

Start with [Setup](./setup.md), then keep [Writing Tests](./writing.md) and
[Reports and Screenshots](./reports.md) open while adding the first world-backed scenario.
