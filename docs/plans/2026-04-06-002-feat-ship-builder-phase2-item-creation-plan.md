---
title: "feat: Ship builder Phase 2 — item creation mode with polygon tool"
type: feat
status: completed
date: 2026-04-06
origin: docs/brainstorms/2026-04-06-ship-builder-requirements.md
---

# feat: Ship builder Phase 2 — item creation mode

## Overview

Add an item creation mode to the ship builder that allows defining custom hull sections,
modules, and turrets by drawing convex polygons and configuring type-specific attributes.
The polygon tool is shared across all three item types. The existing ship layout becomes a
greyed-out reference while creating a new item, and the right panel changes to show
item-specific attributes instead of ship stats.

## Problem Frame

Phase 1 ships pre-defined hull pieces and catalog modules. To design truly custom ships,
users need to create their own hull sections with specific shapes, modules with chosen types
and attributes, and turrets with rotation/facing parameters. The polygon drawing tool
(originally scoped as hull-only in R14-R21) is generalised to work for all item types.
(see origin: `docs/brainstorms/2026-04-06-ship-builder-requirements.md`)

## Requirements Trace

From the origin doc (revised scope per user direction):

- R14. Polygon creation tool on the canvas (FAB + right-click to start/end)
- R15. Click-to-place vertices with closing outline
- R16. Vertices snap to grid
- R17. Vertices inserted after the currently selected vertex
- R18. Snap-to-existing-vertex selects it instead of creating duplicate (priority over R17)
- R19. Convexity check after each vertex change; red rendering if concave
- R20. Vertex count limit and dimension constraints
- R21. Completed polygon saved as a new item available from the parts panel

New requirements from revised Phase 2 scope:

- R35. Three creation mode entry points: "Create Hull Section", "Create Module", "Create Turret"
- R36. Entering creation mode renders the existing ship layout greyed-out and semi-transparent
  as a visual reference
- R37. The polygon tool is active in the canvas during creation mode for all item types
- R38. The right panel switches from ship stats to item-specific attributes during creation mode
- R39. Hull section attributes: area readout (calculated from polygon)
- R40. Module attributes: module type selector (Reactor, Main Engine, Bridge), type-specific
  attribute inputs (HP, density, mass, thrust values for engines)
- R41. Turret attributes: size category (derived from polygon area), fixed vs rotating toggle,
  default facing value, limited rotation toggle with min/max angle parameters
- R42. Completed items become available in the parts panel as custom entries
- R43. Data model unified: single `ItemDefinition` type with a type discriminator and
  type-specific attribute block, replacing separate HullPieceDefinition for custom items

## Scope Boundaries

- Phase 2 only. Game integration (Phase 3) and undo/redo (Phase 4) remain out of scope.
- No deletion of custom items from the parts panel (future work).
- No editing of already-placed instances' definition polygons (place a new one instead).
- Module thrust is determined by facing/rotation relative to ship — no explicit connection points.
- Armour stats on hull sections use uniform defaults for this phase (editable in attributes later).

## Context & Research

### Relevant Code and Patterns

- **ShipBuilderState** in `features/ship-builder/impl/.../ui/entities/ShipBuilderState.kt` — will
  gain a creation mode field and active item definition state
- **ShipBuilderIntent** in `.../ui/entities/ShipBuilderIntent.kt` — will gain creation mode intents
  and vertex manipulation intents
- **CanvasInputHandler** in `.../canvas/DesignCanvas.kt` — tap/drag events already abstracted;
  creation mode will reinterpret taps as vertex placement
- **ShipDesign** in `components/game-core/api/.../shipdesign/ShipDesign.kt` — will be refactored
  to use unified `ItemDefinition`
- **PartsCatalog** in `.../data/PartsCatalog.kt` — will need to include custom-created items
- **StatsPanel** in `.../ui/StatsPanel.kt` — will be extended or replaced with item attributes panel
- **PointInPolygon** in `.../geometry/PointInPolygon.kt` — reusable for convexity checks
- **DesignCanvas** rendering — `ItemRenderer.kt` handles drawing; will need greyed-out mode and
  vertex handle rendering

### Institutional Learnings

- No `docs/solutions/` directory. Coordinate convention from `docs/game_engine_principles.md`:
  +X forward, +Y starboard. Polygon vertices must follow this convention.

## Key Technical Decisions

- **Unified `ItemDefinition`**: Replace the separate `HullPieceDefinition`, module-type, and
  turret-config concepts with a single `ItemDefinition` that has a `type` discriminator
  (`HULL`, `MODULE`, `TURRET`) and type-specific sealed attribute classes. This simplifies the
  polygon tool (always works on an `ItemDefinition`) and the parts panel (one list of custom items).
  The pre-defined catalog entries will produce `ItemDefinition` instances too.
  (see origin: user decision during planning)

- **Creation mode as a VM state, not a separate screen**: The builder switches between
  `EditingShip` and `CreatingItem` modes. In `CreatingItem` mode, the canvas renders existing
  items greyed-out and routes pointer events to the polygon tool. The right panel swaps content.
  No navigation change — same composable, different state rendering.

- **Polygon tool in the VM, not the canvas**: Consistent with the Phase 1 refactor where the VM
  owns interaction logic. The canvas reports taps; the VM manages the vertex list, selected vertex,
  convexity state, and snap-to-existing-vertex logic.

- **Module thrust from facing**: Engine modules contribute thrust based on their rotation relative
  to the ship. Forward thrust comes from engines facing backward (thrust opposes facing). Lateral
  from engines rotated 90 degrees. This replaces the Phase 1 hardcoded thrust values in
  `ShipStatsCalculator`.

- **Convexity check via cross-product sign consistency**: For each consecutive triple of vertices,
  compute the cross product. If all have the same sign, the polygon is convex. Simple, O(n), and
  well-suited to interactive feedback after each vertex change.

## Open Questions

### Resolved During Planning

- **Connection points on modules**: Not needed — modules use facing/rotation to determine
  directional behavior.
- **Data model approach**: Unified `ItemDefinition` with type discriminator.

### Deferred to Implementation

- Exact attribute ranges and defaults for each module type (HP, density, mass values) — iterate
  based on gameplay feel.
- Turret size category thresholds (polygon area breakpoints for small/medium/large).
- Visual style for greyed-out ship layout (opacity level, colour treatment).
- Whether the creation mode FAB should be in the parts panel or floating on the canvas.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not
> implementation specification. The implementing agent should treat it as context, not code
> to reproduce.*

```
Builder Modes (state machine):

  EditingShip (default)
    ├── Left panel: Parts catalog (pre-defined + custom items)
    ├── Canvas: normal rendering, selection, drag, transforms
    ├── Right panel: Ship stats + design name
    └── Buttons: "Create Hull", "Create Module", "Create Turret"
           │
           ▼
  CreatingItem(itemType)
    ├── Left panel: hidden or collapsed
    ├── Canvas: existing layout greyed-out, polygon tool active
    │     ├── Tap: place vertex (snapped to grid)
    │     ├── Tap on existing vertex: select it
    │     ├── Drag on selected vertex: move it
    │     └── Visual: polygon outline, closing line, red if concave
    ├── Right panel: Item attributes (type-specific)
    │     ├── Hull: area readout
    │     ├── Module: type selector + type-specific attributes
    │     └── Turret: size category, fixed/rotating, facing, limits
    └── FAB/Button: "Finish" → save as ItemDefinition, return to EditingShip


ItemDefinition (unified data model):

  ItemDefinition
    ├── id: String
    ├── name: String
    ├── vertices: List<SceneOffset>  (the polygon)
    ├── itemType: ItemType  (HULL, MODULE, TURRET)
    └── attributes: ItemAttributes  (sealed class)
          ├── HullAttributes { armour: ArmourStats, sizeCategory, mass }
          ├── ModuleAttributes { systemType, maxHp, density, mass,
          │     forwardThrust?, lateralThrust?, reverseThrust?, angularThrust? }
          └── TurretAttributes { sizeCategory, isFixed, defaultFacing,
                isLimitedRotation, minAngle?, maxAngle?,
                gunData reference }
```

## Implementation Units

### Data Model Migration

- [ ] **Unit 1: Unified ItemDefinition data model**

  **Goal:** Replace `HullPieceDefinition` with a unified `ItemDefinition` type that supports
  hull sections, modules, and turrets. Migrate `ShipDesign` and `ShipBuilderState` to use
  the new type. Existing pre-defined catalog entries produce `ItemDefinition` instances.

  **Requirements:** R43

  **Dependencies:** None (builds on completed Phase 1)

  **Files:**
  - Create: `components/game-core/api/.../gamecore/shipdesign/ItemDefinition.kt`
  - Modify: `components/game-core/api/.../gamecore/shipdesign/ShipDesign.kt`
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderState.kt`
  - Modify: `features/ship-builder/impl/.../data/PartsCatalog.kt`
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderVM.kt` (update references)
  - Modify: `features/ship-builder/impl/.../canvas/ItemRenderer.kt` (update rendering)
  - Modify: `features/ship-builder/impl/.../stats/ShipStatsCalculator.kt`
  - Modify: `components/game-core/api/src/commonTest/.../shipdesign/ShipDesignSerializationTest.kt`
  - Test: `components/game-core/api/src/commonTest/.../shipdesign/ItemDefinitionTest.kt`

  **Approach:**
  - `ItemDefinition` has: `id`, `name`, `vertices: List<SceneOffset>`, `itemType: ItemType` enum
    (`HULL`, `MODULE`, `TURRET`), and `attributes: ItemAttributes` sealed class with three
    subclasses (`HullAttributes`, `ModuleAttributes`, `TurretAttributes`).
  - `ShipDesign.hullPieces` becomes `ShipDesign.itemDefinitions: List<ItemDefinition>`.
  - `PlacedHullPiece.hullPieceId` becomes `PlacedHullPiece.itemDefinitionId` (or rename to
    `PlacedItem` — implementation decision).
  - Pre-defined catalog entries in `PartsCatalog` now produce `ItemDefinition` instances with
    the appropriate `HullAttributes`, `ModuleAttributes`, or `TurretAttributes`.
  - `ShipStatsCalculator` updated to read attributes from `ItemDefinition` instead of hardcoded
    values. Engine modules contribute thrust based on their `ModuleAttributes` thrust fields.
  - All serialization annotations (`@Serializable`) applied to new types. Existing serialization
    tests updated + new tests for `ItemDefinition` round-trip with each attribute subclass.

  **Patterns to follow:**
  - Existing `HullPieceDefinition` + `SerializableArmourStats` pattern
  - kotlinx.serialization polymorphic serialization for sealed class `ItemAttributes`

  **Test scenarios:**
  - Happy path: `ItemDefinition` with `HullAttributes` round-trips through JSON
  - Happy path: `ItemDefinition` with `ModuleAttributes` round-trips through JSON
  - Happy path: `ItemDefinition` with `TurretAttributes` round-trips through JSON
  - Happy path: `ShipDesign` with mixed item types serializes and deserializes correctly
  - Happy path: pre-defined catalog hull piece produces valid `ItemDefinition` with `HullAttributes`
  - Integration: stats calculator reads engine module thrust from `ModuleAttributes` instead of
    hardcoded values

  **Verification:**
  - All existing serialization tests pass with updated model
  - New `ItemDefinition` serialization tests pass
  - Full build passes
  - Ship builder still works with pre-defined catalog items after migration

### Creation Mode Infrastructure

- [ ] **Unit 2: Creation mode state and mode switching**

  **Goal:** Add creation mode to `ShipBuilderState` and `ShipBuilderIntent`. Implement mode
  switching: entering creation mode (with item type), exiting (finish or cancel), and the
  state that tracks the in-progress polygon and attributes.

  **Requirements:** R35, R36

  **Dependencies:** Unit 1

  **Files:**
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderState.kt`
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderIntent.kt`
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderVM.kt`
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderScreen.kt` (mode-dependent layout)

  **Approach:**
  - Add to `ShipBuilderState`:
    - `editorMode: EditorMode` sealed class: `EditingShip` (default) or
      `CreatingItem(itemType: ItemType, vertices: List<Offset>, selectedVertexIndex: Int?,
      attributes: ItemAttributes, isConvex: Boolean, name: String)`
  - Add intents: `EnterCreationMode(itemType)`, `ExitCreationMode`, `FinishCreation`
  - `EnterCreationMode` switches to `CreatingItem` with empty vertex list and default attributes
    for the chosen type.
  - `FinishCreation` validates (>= 3 vertices, convex), creates an `ItemDefinition`, adds it to
    the design's item definitions, places an instance at the polygon's centroid, and switches
    back to `EditingShip`.
  - `ExitCreationMode` discards the in-progress item and returns to `EditingShip`.
  - Screen layout changes based on mode: in `CreatingItem`, hide/collapse the parts panel,
    show item attributes panel on the right instead of stats panel.

  **Patterns to follow:**
  - Existing `ShipBuilderState` structure
  - Existing intent handling pattern in `ShipBuilderVM.accept()`

  **Test scenarios:**
  - Happy path: entering creation mode with HULL type sets `editorMode` to `CreatingItem`
    with `HullAttributes` defaults
  - Happy path: entering creation mode with MODULE type sets `ModuleAttributes` defaults
  - Happy path: `ExitCreationMode` returns to `EditingShip` without adding an item
  - Happy path: `FinishCreation` with 3+ convex vertices creates an `ItemDefinition` and
    places it, then returns to `EditingShip`
  - Error path: `FinishCreation` with < 3 vertices does not create an item (stays in creation mode)
  - Error path: `FinishCreation` with concave polygon does not create an item

  **Verification:**
  - Mode switching works: entering and exiting creation mode preserves existing design state
  - Finishing creation adds the new item to both definitions and placed items

### Polygon Tool

- [ ] **Unit 3: Polygon tool — vertex placement, selection, and convexity**

  **Goal:** Implement the polygon drawing tool: tap to place vertices (snapped to grid),
  tap on existing vertex to select it, drag selected vertex to move it, convexity validation
  after each change, and the visual rendering of the in-progress polygon.

  **Requirements:** R14, R15, R16, R17, R18, R19, R20, R37

  **Dependencies:** Unit 2

  **Files:**
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderVM.kt` (vertex intents + logic)
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderIntent.kt`
  - Create: `features/ship-builder/impl/.../geometry/ConvexityCheck.kt`
  - Modify: `features/ship-builder/impl/.../canvas/DesignCanvas.kt` (creation mode rendering)
  - Modify: `features/ship-builder/impl/.../canvas/ItemRenderer.kt` (greyed-out mode, polygon
    outline, vertex handles)
  - Test: `features/ship-builder/impl/src/commonTest/.../geometry/ConvexityCheckTest.kt`

  **Approach:**
  - In creation mode, `CanvasInputHandler` events are reinterpreted by the VM:
    - **Tap**: Check if near an existing vertex (within snap radius). If yes, select that vertex
      (R18 priority). If no, place a new vertex after the selected vertex (R17), snapped to grid.
      The new vertex becomes selected.
    - **DragStart on selected vertex**: Enter vertex-drag mode (return true to consume).
    - **DragMove**: Move the selected vertex (snapped to grid on release).
    - **DragEnd**: Snap vertex, re-check convexity.
  - `ConvexityCheck.isConvex(vertices: List<Offset>): Boolean` — cross-product sign consistency.
    For each consecutive triple (i, i+1, i+2 mod n), compute cross product. All must have the
    same sign. Empty or <3 vertices returns true (not yet invalid).
  - Rendering: draw the polygon outline from vertex to vertex, closing line from last to first.
    Vertices drawn as small circles. Selected vertex drawn larger/brighter. If not convex,
    polygon outline rendered in red. Existing ship items rendered greyed-out (multiply alpha by
    ~0.3, desaturate).
  - Vertex count limit: configurable constant (e.g., 12). Prevent adding beyond limit.

  **Patterns to follow:**
  - Existing `CanvasInputHandler` event handling in VM
  - `SnapUtils.snapToGrid()` for vertex snapping
  - `PointInPolygon.kt` pattern for geometry utility

  **Test scenarios:**
  - Happy path: `isConvex` returns true for a square (4 vertices, all CW or CCW)
  - Happy path: `isConvex` returns false for a concave L-shape
  - Happy path: `isConvex` returns true for a triangle
  - Edge case: `isConvex` with < 3 vertices returns true
  - Edge case: `isConvex` with collinear vertices (cross product = 0) — treat as convex
  - Happy path: tap places a vertex snapped to grid
  - Happy path: tap near existing vertex selects it instead of placing new one (R18)
  - Happy path: new vertex inserted after selected vertex (R17)
  - Happy path: dragging a selected vertex moves it, snapped to grid on release
  - Happy path: convexity re-checked after vertex placement or move
  - Edge case: vertex count at limit — additional taps ignored

  **Verification:**
  - Polygon tool allows drawing convex polygons interactively
  - Concave polygons show red visual feedback
  - Vertex selection, insertion, and dragging work correctly
  - Convexity tests pass

### Item Attributes Panels

- [ ] **Unit 4: Hull section attributes panel**

  **Goal:** When creating a hull section, the right panel shows the polygon area (calculated)
  and armour stat inputs.

  **Requirements:** R38, R39

  **Dependencies:** Unit 2

  **Files:**
  - Create: `features/ship-builder/impl/.../ui/ItemAttributesPanel.kt`
  - Create: `features/ship-builder/impl/.../geometry/PolygonArea.kt`
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderScreen.kt` (swap right panel by mode)
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderIntent.kt` (attribute update
    intents)
  - Test: `features/ship-builder/impl/src/commonTest/.../geometry/PolygonAreaTest.kt`

  **Approach:**
  - `PolygonArea.calculateArea(vertices: List<Offset>): Float` — shoelace formula. Returns
    absolute area (positive regardless of winding order).
  - `ItemAttributesPanel` composable: shows different content based on `itemType`.
  - For HULL: read-only area display (updates as vertices change), armour hardness/density inputs,
    mass input, name field.
  - Attribute changes dispatch intents that update `CreatingItem.attributes`.

  **Patterns to follow:**
  - `StatsPanel.kt` layout pattern (collapsible column with labelled fields)
  - Material 3 `OutlinedTextField` for inputs

  **Test scenarios:**
  - Happy path: triangle with vertices (0,0), (10,0), (0,10) → area = 50
  - Happy path: square with side 10 → area = 100
  - Edge case: polygon with < 3 vertices → area = 0
  - Edge case: degenerate polygon (all collinear) → area = 0

  **Verification:**
  - Area updates live as vertices are added/moved
  - Attribute inputs modify the in-progress item's attributes

- [ ] **Unit 5: Module attributes panel**

  **Goal:** When creating a module, the right panel shows a module type selector and
  type-specific attribute inputs.

  **Requirements:** R38, R40

  **Dependencies:** Unit 4

  **Files:**
  - Modify: `features/ship-builder/impl/.../ui/ItemAttributesPanel.kt`
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderIntent.kt`

  **Approach:**
  - Module type dropdown/selector: Reactor, Main Engine, Bridge.
  - Changing type updates `ModuleAttributes.systemType` and shows relevant fields:
    - **All types**: maxHp, density, mass
    - **Main Engine**: forwardThrust, lateralThrust, reverseThrust, angularThrust
    - **Reactor/Bridge**: no additional fields beyond the common ones
  - Area readout displayed (same as hull, from polygon).
  - Name field auto-populated from type but editable.

  **Patterns to follow:**
  - Material 3 `DropdownMenu` or `ExposedDropdownMenuBox` for type selector
  - `StatsPanel.kt` `StatRow` pattern for labelled value displays

  **Test scenarios:**
  - Happy path: selecting "Main Engine" type shows thrust input fields
  - Happy path: selecting "Reactor" type hides thrust fields, shows HP/density/mass
  - Happy path: changing attribute values updates `CreatingItem.attributes`

  **Verification:**
  - Type selection changes visible fields
  - Attribute values persist in state during creation

- [ ] **Unit 6: Turret attributes panel**

  **Goal:** When creating a turret, the right panel shows size category, fixed/rotating toggle,
  facing, and rotation limit parameters.

  **Requirements:** R38, R41

  **Dependencies:** Unit 4

  **Files:**
  - Modify: `features/ship-builder/impl/.../ui/ItemAttributesPanel.kt`
  - Modify: `features/ship-builder/impl/.../ui/entities/ShipBuilderIntent.kt`

  **Approach:**
  - Size category: read-only, derived from polygon area (small/medium/large thresholds).
  - Fixed vs rotating: `Switch` toggle. Default: rotating.
  - Default facing: angle input (degrees, converted to radians internally). Only shown when
    rotating.
  - Limited rotation: `Switch` toggle. Only shown when rotating.
  - Min/max angle: inputs shown only when limited rotation is enabled.
  - Name field.

  **Patterns to follow:**
  - Material 3 `Switch` for toggles
  - Conditional `AnimatedVisibility` for fields that appear/disappear based on toggles

  **Test scenarios:**
  - Happy path: toggling to "fixed" hides facing and rotation limit fields
  - Happy path: toggling "limited rotation" shows min/max angle fields
  - Happy path: size category updates when polygon area changes

  **Verification:**
  - Toggle states control field visibility correctly
  - Attribute values round-trip through creation flow

### Integration

- [ ] **Unit 7: Creation mode canvas rendering**

  **Goal:** In creation mode, render existing ship items greyed-out and semi-transparent.
  Render the in-progress polygon with vertex handles. Add creation mode entry buttons
  to the parts panel or as FABs.

  **Requirements:** R36, R37, R42

  **Dependencies:** Unit 3, Unit 4

  **Files:**
  - Modify: `features/ship-builder/impl/.../canvas/DesignCanvas.kt`
  - Modify: `features/ship-builder/impl/.../canvas/ItemRenderer.kt`
  - Modify: `features/ship-builder/impl/.../ui/PartsPanel.kt` (creation buttons + custom items)
  - Modify: `features/ship-builder/impl/.../ui/ShipBuilderScreen.kt` (FAB or button placement)

  **Approach:**
  - `DesignCanvas` checks `state.editorMode`: in `CreatingItem`, draw all existing items with
    reduced alpha (~0.3) and desaturated colours. Then draw the in-progress polygon on top with
    full-colour vertex handles.
  - Vertex handles: small circles at each vertex position. Selected vertex drawn larger and in
    a distinct colour (e.g., white fill vs cyan outline for others).
  - Closing line: dashed or semi-transparent line from last vertex to first.
  - Creation buttons: "Create Hull", "Create Module", "Create Turret" at the bottom of the
    parts panel. Custom-created items appear in their respective category in the panel.
  - "Finish" and "Cancel" buttons visible during creation mode (FAB or toolbar area).

  **Patterns to follow:**
  - Existing `drawHullPiece` / `drawModule` / `drawTurret` in `ItemRenderer.kt`
  - `TransformToolbar.kt` pattern for mode-specific bottom buttons

  **Test expectation:** None — visual rendering, verified by manual inspection.

  **Verification:**
  - Entering creation mode visually greys out existing items
  - Polygon tool renders vertices, edges, and closing line
  - Completing creation adds the new item to the parts panel
  - Cancelling returns to normal view without changes

## System-Wide Impact

- **Interaction graph:** Creation mode reuses the same `CanvasInputHandler` but the VM routes
  events differently based on `editorMode`. No new gesture infrastructure needed.
- **Error propagation:** Invalid polygons (concave, < 3 vertices) block completion but don't
  crash — visual feedback only.
- **State lifecycle risks:** Entering creation mode while items are selected should clear
  selection. Exiting creation mode should not lose existing design state.
- **API surface parity:** The `ShipDesign` serialization format changes (unified `ItemDefinition`).
  Existing saved designs will need migration or will fail to load. A version field or fallback
  deserializer may be needed.
- **Unchanged invariants:** Ship editing mode (selection, drag, transforms, auto-save) is
  unchanged. Stats panel in editing mode continues to work as before.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| `ShipDesign` format change breaks existing saved designs | Add format version field. Either migrate on load or treat old format as incompatible (acceptable for dev tool). |
| Unified `ItemDefinition` with sealed `ItemAttributes` complicates serialization | kotlinx.serialization handles sealed class polymorphism with `@SerialName` discriminators. Test each subclass variant. |
| Polygon tool vertex interaction on touch screens (fat finger) | Use generous snap radius for vertex selection. Zoom helps precision. |
| Convexity check edge cases (collinear, very small polygons) | Treat collinear as convex (degenerate but not invalid). Minimum area threshold deferred to implementation. |

## Documentation / Operational Notes

- Update `docs/brainstorms/2026-04-06-ship-builder-requirements.md` Phase 2 section to reflect
  the expanded scope (all item types, not just hull polygons).
- No deployment or operational concerns — developer tool.

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-06-ship-builder-requirements.md](docs/brainstorms/2026-04-06-ship-builder-requirements.md)
- Phase 1 plan: [docs/plans/2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md](docs/plans/2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md)
- Related code: `features/ship-builder/impl/` (all builder code)
- Related code: `components/game-core/api/.../shipdesign/` (data model)
