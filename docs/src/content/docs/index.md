---
layout: home
title: Glue Documentation
titleTemplate: false
description: Modular Fabric APIs for game content, rendering, colored lighting, web interfaces and live-client tests.
hero:
  name: Glue
  text: Building blocks for Fabric mods
  tagline: Minecraft 1.21.8 · Java 21 · Official Mojang mappings
  image:
    src: /icon.png
    alt: Glue library icon
  actions:
    - theme: brand
      text: Install Glue
      link: ./getting-started.md
    - theme: alt
      text: Run the showcase
      link: ./showcase.md
features:
  - title: Core
    details: Typed registries, data components, packets, shapes, math and undo history.
    link: ./core/index.md
  - title: Rendering
    details: Render events, shader pipelines, post effects, outlines, scene viewports and native dialogs.
    link: ./rendering/index.md
  - title: Lumos
    details: Local or server-owned colored lights, persistence, shadows and material-aware rendering.
    link: ./lumos/index.md
  - title: Web
    details: Chromium screens, HUDs, overlays and widgets connected to Java actions and game state.
    link: ./web/index.md
  - title: GameTest
    details: Script a live client, exercise input and lifecycle, and collect reports and screenshots.
    link: ./gametest/index.md
  - title: Light Workshop
    details: Create a probe item, add HUD feedback, toggle a local light and test its lifecycle.
    link: ./workshop/index.md
---

## Pick Your Starting Point

| Goal | Guide |
|---|---|
| Add Glue to an existing mod | [Installation](./getting-started.md) and [module dependencies](./modules.md) |
| Learn through a small project | [Light Workshop](./workshop/index.md) |
| See the APIs working in Minecraft | [Showcase and test scenarios](./showcase.md) |
| Build a web interface | [Web interfaces](./web/index.md) |
| Add lighting to an existing feature | [Lumos](./lumos/index.md) |

Use the modules your feature needs. Shared game models live in Core and Lumos; rendering and web
hosts belong to client code. Packages named `internal` are implementation details.
