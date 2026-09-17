---
layout: home
title: Glue Documentation
titleTemplate: false
description: Build focused Fabric features with Glue, from a first registered item to rendering, lighting, and live-client tests.
hero:
  name: Glue
  text: Start with one working feature
  tagline: Add only the Fabric infrastructure your mod needs, then grow from a source-verified first item.
  actions:
    - theme: brand
      text: Build the Lumen Probe
      link: ./workshop/probe.md
    - theme: alt
      text: Install Glue
      link: ./getting-started.md
features:
  - title: Embed web content
    details: Own Chromium surfaces with native inputs, cursors and explicit messages, using a Glue API.
    link: ./web/index.md
  - title: Register game content
    details: Build items, blocks, data components, keybindings, shapes, and undoable tools with the shared Core module.
    link: ./core/index.md
  - title: Draw custom visuals
    details: Add render events, pipelines, post effects, outlines, scene tools, and native file dialogs on the client.
    link: ./rendering/events.md
  - title: Add colored light
    details: Keep light state on the server or spawn local visual lights, then render them through Lumos on supported clients.
    link: ./lumos/index.md
  - title: Test in a real client
    details: Script interactions, screenshots, tools, and frame-sensitive checks with the development-only test harness.
    link: ./gametest/index.md
  - title: Choose exact modules
    details: Match each user goal to its Maven artifact, Fabric mod ID, environment, and direct Glue dependencies.
    link: ./modules.md
---

## Your First Success

The [Lumen Probe workshop](./workshop/probe.md) starts with one Core dependency and ends with a
real item available through `/give`. It uses the sample mod **Light Workshop**, mod ID
`lightworkshop`, and package `dev.example.lightworkshop` throughout this section.

<DocImage title="The first Light Workshop milestone" description="A Minecraft 1.21.8 inventory with the Lumen Probe item selected, its name visible, and the command output confirming lightworkshop:lumen_probe was given to the player." />

## Choose by Outcome

| I want to... | Start here |
| --- | --- |
| Install one shared dependency | [Getting Started](./getting-started.md) |
| Finish a small item tutorial | [Build the Lumen Probe](./workshop/probe.md) |
| Learn the Core path in order | [Core Overview](./core/index.md) |
| Add an optional pedestal block | [Blocks and Block Entities](./core/blocks.md) |
| Pick a rendering, lighting, or testing module | [Module Guide](./modules.md) |

Glue documents supported public behavior. Packages named `internal` are implementation details,
not extension points. Keep shared content in client-and-server modules; load rendering, UI, and
other client-only APIs only from client code.
