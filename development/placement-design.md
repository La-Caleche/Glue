# Shapes and composite placement: design

Status, 3 October 2026: the core (`shaper/ShapeGeometry`, `GeometryBox`, `ShapeVoxelizer`) and
phase 1 (`BlockShapeProvider`, `BlockShapes`, `mixin/BlockBehaviourMixin`, the showcase's
`test_shape` and `shapes` client test) are implemented. Phase 2's first step is too: `composite/`
(`CompositePart`, `CompositeBlock`, `CompositeBlockEntity`, `CompositeCells`) and the client's
`render/composite/`, a cell built by code or the showcase's `/composite`, meshed with Sodium too and
checked by the `composite` client test. Per-part picking, editing, survival rules and part light are
not.

Two features that share one core:

1. **Generated shapes.** A block's outline and collision come from its model, generated at build
   time, instead of `Block.box` copies written by hand.
2. **Composite cells.** Several parts share one block position, each with its own position, rotation,
   scale and texture. A part is any block (and later any item). This generalises Occamod's shelf and
   goes past Only Furniture's sub-grid placement.

The core they share is **geometry under a transform**: a set of boxes taken from a model or a shape,
moved, rotated and scaled, then turned back into a `VoxelShape` that collision, selection and
outlines can use.

## What exists and what it teaches

| Where | What | Lesson |
|---|---|---|
| Glue `shaper/VoxelShaper` | Quarter-turn rotations of a shape, computed eagerly | Exact only at 90°; no state binding |
| Glue `data/components/TransformationComponent` | Translation, left rotation, scale, right rotation | Already the part transform format; Occamod's shelf uses it |
| Glue `client/render/gizmo` | Translate, rotate, scale gizmos | The editing UX has its widgets |
| Glue `IHaveBigOutline` + `CollisionGetterMixin` | Shapes that leave their block | Overhang has a precedent |
| Occamod blocks | ~380 `Block.box` calls copying Blockbench elements; rotations as hand-named fields and `switch` | The cost phase 1 removes |
| Occamod `ShelfBlock` | 4 item slots, transform per item, block-entity renderer | Proven idea; capped, items only, no per-item collision, per-frame rendering |
| Only Furniture (MPL-2.0, 1.21.1) | 16-bit record per piece in a chunk attachment: 1/16 offset, 22.5° yaw, 4 layers per cell, pointer blocks for overhang; rotation applied at chunk-mesh time | Static meshing is right. Collision rounded to 90° and pointer blocks are what to avoid |

Fabric API 1.21.8 provides what composite rendering needs: `FabricBlockStateModel.emitQuads`
(chunk-mesh quads per block), `RenderDataBlockEntity` (block-entity data available to the mesher)
and `SpriteFinder` (which sprite a quad uses, for retexturing).

## Core: geometry, transform, voxelizer

Both sides of the game need shapes, and only the client has models. The core therefore works on
**geometry data**, never on loaded models.

- **`ShapeGeometry`**: boxes in block pixel space (0..16, overhang allowed), each optionally rotated
  about one axis by an angle and origin, as in model elements. Sources:
  - a model, exported by datagen into the mod jar (phase 1);
  - any `VoxelShape`, such as `state.getShape(...)`, so every block, vanilla included, has geometry
    on both sides;
  - Java.
- **Transform**: `TransformationComponent` (translation, rotations as quaternions, scale) about the
  cell centre. Editing snaps to 1/16 and 22.5° by default; the data allows free values.
- **Voxelizer**: geometry × transform → `VoxelShape`.
  - Fast path: when every box stays axis-aligned (identity, quarter turns, axis scales), transform
    the box corners exactly. Most blocks take this path.
  - Otherwise, sample a grid at a chosen resolution (default 1/16 of a block), mark cells inside any
    transformed box, then merge cells greedily into boxes. Resolution is a per-use setting; the box
    count it produces is reported in datagen output so a heavy shape is visible.
  - The core does not cache. Its callers keep what they reuse: the shape table one shape per block
    state, a composite cell its collision until the next edit. Collision never recomputes per tick.
- **Exact picking**: ray-casting against the transformed boxes themselves (oriented boxes), not the
  voxelized shape, so selection and outlines follow the model exactly at any angle.

Everything above lives in `src/main` (both sides). Nothing reads `BlockModel` at runtime.

## Phase 1: generated shapes for ordinary blocks

### Datagen

A Glue data provider used from a mod's Fabric datagen entrypoint.

- Reads blockstates and models as JSON, resolving `parent` chains, from the mod's resources and from
  extra declared directories (Occamod's parent models live in a development resource pack).
- For each block state: resolves the variant (or multipart parts), applies its `x`/`y` rotation, and
  builds its shape through the voxelizer.
- Rules declared in the provider for what the blockstate does not say:
  - `rotation16(property)` for blocks whose rotation is applied by a renderer (Occamod's chair,
    stool, potion kit, quill): 16 shapes;
  - a collision model per block when collision should be simpler than the outline (Occamod already
    has `template_stove_shape.json`);
  - resolution per block.
- Writes `glue/shapes/<namespace>/<block>.json` into generated resources: a table of distinct shapes
  and the index each state uses. Also writes each model's geometry for phase 2 parts.
- Fails the build on a missing model, an unknown property value or an unresolvable parent.

### Runtime

- `ShapeTable` loads a block's file from the mod jar on first use and caches one shape per
  `BlockState` (an array indexed by state id). Lookup is a field read after that.
- The files are **not** datapack data. A server-side override would desync collision with clients
  that never see it. Shapes belong to the block, like its code.
- A mixin seam on `BlockBehaviour.getShape` and `getCollisionShape` delegates to `ShapeTable` when a
  table exists for the block.
- **Priority.** A block class that overrides `getShape` without calling `super` never reaches the
  seam, so its Java shape wins with no configuration. Otherwise the generated shape is used,
  otherwise vanilla's full block. Collision defaults to the generated collision, then the outline.

### Done when

The showcase's `TestShapeBlock` drops its four `Block.box` shapes and keeps its behaviour; a
showcase block at 22.5° steps collides as drawn; Occamod's sink, stove and chair can drop their
fields (done in Occamod's own migration, after it moves off Glue 1.2.5).

## Phase 2: composite cells

### Data

- One block, `glue:composite`, with a `CompositeBlockEntity` holding an ordered list of parts, capped
  (32 to start).
- A part: a source `BlockState`, a transform, optional texture overrides (sprite to sprite), and
  later an optional tint.
- Persistence through a codec in the block entity. A part whose block no longer exists is dropped
  with a log line, never a crash.
- A cell holding one part with the identity transform reverts to that plain block, so the system
  leaves no trace where it is not used.

### Rendering

- Static, in the chunk mesh: the composite block's model implements `emitQuads`, reads the part list
  through `RenderDataBlockEntity`, and emits each part's block model through a quad transform that
  moves **positions and normals**, and swaps sprites found by `SpriteFinder` for the overrides.
- No face culling on transformed parts; identity parts keep normal culling.
- No block-entity renderer, so a cell costs nothing per frame. Item parts, if added, would need a
  renderer, which is why parts start as blocks only.
- An edit sends the block-entity update and marks the section for remeshing.

### Shapes and interaction

- Collision: the union of each part's voxelized shape, cached in the block entity and rebuilt on
  edit; the block declares a dynamic shape so vanilla does not cache it per state. Client and server
  compute it from the same data.
- Selection: the exact pick finds the part under the crosshair; the outline draws that part's
  transformed boxes.
- Parts are decorative: no block entity, ticking, redstone or interaction of their own. Light: the
  composite carries the brightest part's emission as a block-state property. Sound: the hit part's.
- Survival: adding a part consumes its item; removing a part drops it; breaking the cell drops all.

### Editing

- A dedicated mode or tool: pick a part, edit it with the Glue gizmos, snapping to 1/16, 22.5° and
  1/16 scale steps, free with a modifier key.
- A live preview of a part being added.
- The server is the authority: add, update and remove packets carry the part index and the new
  values; the server checks bounds, the cap, the source block and the player's reach and permission,
  then sends the result back.

### Bounds

To start, a part's transformed geometry must stay inside its cell. Overhang needs another cell to
answer for collision and breaking, which is the pointer machinery that makes Only Furniture fragile.
It can come later on top of `IHaveBigOutline`.

## Open decisions

1. Texture overrides: any block-atlas sprite, or only sprites of blocks the player has?
2. Item parts (the shelf's use case) in phase 2, with a renderer, or later?
3. Overhang beyond the cell: never, or a later phase?
4. Survival rules: who may edit a cell, and does editing need a tool item?
5. Sodium: its FRAPI support must render `emitQuads` with render data; to verify before phase 2
   starts.

## Order

1. Core: geometry, transform, voxelizer, exact pick, with unit tests (box counts, rotation
   round-trips, overhang).
2. Phase 1 datagen and runtime table; showcase migration; docs page.
3. Phase 2 data and rendering of a static composite built by command; then shapes and picking; then
   editing; then survival rules.
