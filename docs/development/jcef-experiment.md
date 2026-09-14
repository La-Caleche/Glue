# JCEF experiment

The `malo/experiment/jcef` branch starts at `9577b5d`, the same baseline as the Obscura experiment.
[`jcef-experiment`](../../jcef-experiment/README.md) embeds JCEF directly, with an independent
Minecraft renderer and showcase fixtures. It is not a published Glue API.

Its [performance notes](../../jcef-experiment/PERFORMANCE.md) record the exact runtime, callback,
conversion, upload and correlated-pixel probe measurements, including an observed slow large-page
sample. The CPU comparison mode isolates channel conversion; a full Graphene A/B comparison remains
unperformed.

Standard Chromium cursor requests now reach GLFW through a screen-owned cache, with restoration and
cleanup on exit. The local client scenario covers cursor changes and reopening. The module README
also records the maintainer's subsequent manual validation of smooth 1080p YouTube playback.
