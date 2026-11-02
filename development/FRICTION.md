# Glue — friction log

Each place where Glue gets in a dependent mod's way, and each idea put aside for later. One item per
problem: what it is, and what it would take. An item leaves the file when it is done or the maintainer
drops it.

## Rendering

- **Under Iris, a batch's captures are composited in queue order, with no depth test between them.**
  `GlDirectRenderer.setupBlitState` (`GlDirectRenderer.java:252-263`) blends each capture onto the main
  target with the depth test and depth writes off. So wherever two captures overlap, the one queued later
  lands on top, whatever their depths. Ignis's `debug:vfx/sort_wall` and `debug:vfx/sort_wall_swapped` test
  effects show it: translucent particles behind a wall come out on top of it in the first, and particles in
  front of it come out under it in the second. What it would take: compare each capture's depth with the
  captures already composited (a running depth of the composite), or let the caller order its captures
  from farthest to nearest.
