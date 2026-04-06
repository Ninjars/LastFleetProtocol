---
title: "feat: Ship builder — Phase 0 (data refactor) + Phase 1 (canvas, module placement, save/load)"
type: feat
status: active
date: 2026-04-06
origin: docs/brainstorms/2026-04-06-ship-builder-requirements.md
---

# feat: Ship builder — Phase 0 + Phase 1

## Overview

Deliver the first usable ship builder: a Compose Canvas-based editor with a parts panel,
design canvas with grid/snap/pan/zoom, selection and transforms, hull-constrained module
placement, a live stats panel, and JSON save/load via platform file I/O. Preceded by a
data-layer refactor that moves ship data classes to a shared module and adds serialization.

## Problem Frame

Ship designs are hardcoded in `DemoScenarioConfig.kt`. Iterating requires recompilation.
Multi-hull ships are blocked. A visual builder enables rapid design exploration and
establishes the serialization format for game integration (Phase 3).
(see origin: `docs/brainstorms/2026-04-06-ship-builder-requirements.md`)

## Requirements Trace

- R33. Move ship data classes to `components/game-core/api`
- R1. Design canvas with grid background
- R2. Grid snap
- R3. Canvas pan/zoom
- R4. Parts panel with collapsible categories
- R5. Preview rendering and name per part
- R6. Click-to-add from panel to canvas
- R34. Pre-defined hull pieces available in Phase 1
- R7. Click-to-select with outline
- R8. Drag with snap
- R9. Transform toolbar (mirror, rotate 90)
- R10. Free-rotate handle
- R11. Hull-constrained module placement
- R12. Hull overlap allowed
- R13. Turrets freely placeable within hulls
- R22. Stats panel with calculated stats
- R23. Mass from size category + armour + systems
- R25. Editable design name
- R26. Load button in stats panel
- R27. JSON via kotlinx.serialization + platform file I/O
- R28. Auto-save after every change
- R30. Format captures everything for game simulation

## Scope Boundaries

- Phase 0 + Phase 1 only. Hull polygon editor (Phase 2), game integration (Phase 3),
  and undo/redo (Phase 4) are out of scope.
- Developer tool aesthetic — no polish pass.
- Pre-defined hull pieces only (no custom polygon creation).
- No sprite customisation.
- Square grid only.

## Context & Research

### Relevant Code and Patterns

- **Feature module pattern**: `features/landing/api` has `typealias LandingScreenEntry`,
  `features/landing/impl` has `LandingScreen`, `LandingVM`. Wire via `AppComponent` `@Provides`.
- **MVI pattern**: `ViewModelContract<Intent, State, SideEffect>` in `components/design`.
  Each screen has sealed `Intent`, data class `State`, sealed `SideEffect`.
- **Ship data classes**: All in `features/game/impl/.../data/` — `ShipConfig`, `HullDefinition`,
  `ArmourStats`, `MovementConfig`, `CombatStats`, `InternalSystemSpec`, `TurretConfig`, `GunData`,
  `ProjectileStats`. `Gun.kt` mixes `GunData` (data) with `Gun` (runtime state machine).
- **DemoScenarioConfig**: Defines 4 hull shapes (playerHull, enemyHullLight/Medium/Heavy),
  4 system sets, 3 projectile types, 3 turret configs, 4 ship configs.
- **Drawing patterns**: `DebugVisualiser` shows DrawScope vector drawing (lines, circles,
  polygons via vertex iteration). Same `DrawScope` API works in Compose Canvas.
- **Navigation**: `LFNavDestination` string constants, `LFNavHost` `@Inject @Composable` function.
- **Coordinate system**: +X forward, +Y starboard, atan2 convention. Hull vertices have nose at +X.
- **No existing file I/O** — no okio, no expect/actual file abstractions yet.
- **kotlinx.serialization** plugin configured in multiple modules but zero `@Serializable` usage.
- **`game-core/api`** already depends on `kubriko.engine` and `compose.runtime`. Would need
  `compose.components.resources` for `DrawableResource` and `kotlinx.json` for serialization.

### Institutional Learnings

- No `docs/solutions/` directory exists. Relevant guidance from `docs/game_engine_principles.md`:
  all game-affecting values must be data-driven via parameter data classes.

## Key Technical Decisions

- **Compose Canvas for the builder, not Kubriko viewport**: The builder needs selection, drag
  handles, tool modes, and grid overlays — editor interactions that Kubriko's actor model is not
  designed for. Compose Canvas provides the same `DrawScope` API with full control over rendering
  and gesture handling. (see origin: outstanding question affecting R1, R3)

- **Platform file I/O via expect/actual**: `expect fun saveDesign(name: String, json: String)` /
  `expect fun loadDesign(name: String): String?` / `expect fun listDesigns(): List<String>`.
  Desktop uses `java.io.File` in app data dir; Android uses `Context.filesDir`. No external
  library needed for current targets. (see origin: key decision on file-based JSON storage)

- **Separate GunData from Gun runtime**: `Gun.kt` currently mixes the `GunData` data class with
  the `Gun` state machine (which references `Bullet`, `Ship`, `ActorManager`). The refactor must
  split these — `GunData` moves to `game-core/api`, `Gun` stays in `game/impl`. `GunData` default
  values referencing `Res.drawable.turret_simple_1` must be removed (defaults belong in config, not
  in the data class).

- **DrawableResource stored as string ID**: A `DrawableResourceRegistry` object in
  `components/game-core/api` maps string IDs to `DrawableResource` instances. Both the builder and
  game modules reference this registry. Keeps the serialization format simple (just a string)
  without coupling to Compose resource generation.

- **Ship design model is distinct from ShipConfig**: The builder's serializable `ShipDesign` format
  captures positioned hull piece instances and positioned modules. `ShipConfig` is the runtime
  format consumed by the game simulation. Phase 3 will add a converter from `ShipDesign` ->
  `ShipConfig`. Phase 1 only needs `ShipDesign` serialization.

- **Grid cell size**: Fixed at 10 scene units initially. Configurable via a constant for easy tuning
  during development. Size-category-based scaling deferred.

- **Point-in-polygon via ray casting**: A standalone utility function in the builder module.
  Simple, well-understood algorithm. No Kubriko collision dependency for the editor.

## Open Questions

### Resolved During Planning

- **Canvas approach**: Compose Canvas or possibly a Kubriko instance — see Key Technical Decisions.
- **File storage**: Platform expect/actual — see Key Technical Decisions.
- **Grid cell size**: Fixed 10 scene units initially.
- **Point-in-polygon**: Ray casting utility.
- **DrawableResource serialization**: String ID + registry lookup.
- **GunData/Gun split**: GunData moves, Gun stays.

### Deferred to Implementation

- Exact visual style for selection outline, grid lines, and snap indicators — iterate visually.
- Whether the free-rotate handle should show angle readout.
- Optimal initial set of pre-defined hull pieces to include beyond the 4 DemoScenarioConfig shapes.
- Auto-save debounce interval (immediate vs small delay to batch rapid changes).

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not
> implementation specification. The implementing agent should treat it as context, not code
> to reproduce.*

```
Ship Design Data Model (serializable):

  ShipDesign
    ├── name: String
    ├── hullPieces: List<HullPieceDefinition>   (inline hull templates)
    │     each: { id, vertices, armour, sizeCategory, mass }
    ├── placedHulls: List<PlacedHullPiece>       (instances on canvas)
    │     each: { hullPieceId, position, rotation }
    ├── placedModules: List<PlacedModule>         (internal systems)
    │     each: { systemSpec, position, rotation, parentHullId }
    └── placedTurrets: List<PlacedTurret>         (turrets)
          each: { turretConfig, position, rotation, parentHullId }

Builder Screen Layout:

  ┌─────────────┬────────────────────────────────┬──────────────┐
  │ Parts Panel │       Design Canvas            │  Stats Panel │
  │ (scrollable)│  (Compose Canvas + gestures)   │ (minimisable)│
  │             │                                │              │
  │ [Hull Pieces]│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐   │  Mass: 245   │
  │  > Player   ││       grid + items       │   │  Fwd: 1200   │
  │  > Light    ││       pan / zoom         │   │  Lat: 500    │
  │             ││                           │   │  Rev: 500    │
  │ [Systems]   │ └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘   │  Ang: 300    │
  │  > Reactor  │                                │              │
  │  > Engine   │                                │  Name: [___] │
  │  > Bridge   │  ┌────────────────────────┐   │  [Load...]   │
  │             │  │ Transform: ↔ ↕ ↻ ↺    │   │              │
  │ [Turrets]   │  └────────────────────────┘   │              │
  │  > Standard │                                │              │
  │  > Light    │                                │              │
  │  > Heavy    │                                │              │
  └─────────────┴────────────────────────────────┴──────────────┘
```

## Implementation Units

### Phase 0: Data Layer Refactor

- [ ] **Unit 1: Move ship data classes to game-core/api**

  **Goal:** Extract pure ship data classes from `features/game/impl` to `components/game-core/api`
  so both the game and builder feature modules can import them.

  **Requirements:** R33

  **Dependencies:** None

  **Files:**
    - Modify: `components/game-core/api/build.gradle.kts` (add `compose.components.resources`,
      `kotlinx.json`, `serialization` plugin)
    - Move to `components/game-core/api/src/commonMain/kotlin/.../gamecore/data/`:
        - `ShipConfig.kt` (with `TurretConfig`)
        - `HullDefinition.kt`
        - `ArmourStats.kt`
        - `CombatStats.kt`
        - `MovementConfig.kt`
        - `InternalSystemSpec.kt` (with `InternalSystemType`)
        - `ProjectileStats.kt`
    - Split: `features/game/impl/.../data/Gun.kt` — extract `GunData` to
      `components/game-core/api/.../gamecore/data/GunData.kt`, keep `Gun` class in
      `features/game/impl/.../data/Gun.kt` with updated imports
    - Create: `components/game-core/api/.../gamecore/data/DrawableResourceRegistry.kt`
    - Modify: `features/game/impl/build.gradle.kts` (add dependency on `game-core/api` if missing)
    - Update imports in all files referencing moved types (game/impl actors, managers, tests)

  **Approach:**
    - Move data classes preserving their content. Update package from
      `...components.game.data` to `...components.gamecore.data`.
    - `GunData` loses its default parameter values (especially `Res.drawable.turret_simple_1`).
      Defaults are set at the call site in `DemoScenarioConfig` instead.
    - `DrawableResourceRegistry` maps string IDs like `"ship_player_1"` to `DrawableResource`
      instances. Populated in a single `object` with `register()` calls at app startup or lazy init.
    - `DemoScenarioConfig` stays in `features/game/impl` — it's game-specific scenario config,
      not shared data.

  **Patterns to follow:**
    - Existing `game-core/api` structure (`GameCoreContracts.kt`)
    - Package convention: `jez.lastfleetprotocol.prototype.components.gamecore.data`

  **Test scenarios:**
    - Happy path: project compiles with all data classes in new location
    - Happy path: existing game tests (`ShipConfigTest`, `ShipPhysicsTest`, `ShipSystemsTest`,
      `ArcDamageRouterTest`, `KineticImpactResolverTest`) pass without modification beyond imports
    - Integration: `DemoScenarioConfig` constructs `ShipConfig` from new location correctly
    - Happy path: `DrawableResourceRegistry.get("ship_player_1")` returns the correct resource

  **Verification:**
    - `./gradlew build` passes (full build including Android)
    - All existing tests pass
    - No remaining imports of `...components.game.data.ShipConfig` (or other moved types) in
      any module except through the new `gamecore.data` package

- [ ] **Unit 2: Serializable ship design model + file I/O**

  **Goal:** Define the `ShipDesign` serializable data model and platform file I/O abstraction
  for saving/loading ship designs as JSON.

  **Requirements:** R27, R28, R30

  **Dependencies:** Unit 1

  **Files:**
    - Create: `components/game-core/api/.../gamecore/shipdesign/ShipDesign.kt`
    - Create: `components/game-core/api/.../gamecore/shipdesign/SceneOffsetSerializer.kt`
    - Create: `components/game-core/api/.../gamecore/shipdesign/AngleRadiansSerializer.kt`
    - Create: `components/game-core/api/.../gamecore/shipdesign/ShipDesignRepository.kt` (interface)
    - Create: `components/shared/api/src/commonMain/kotlin/.../utils/FileStorage.kt` (expect)
    - Create: `components/shared/api/src/jvmMain/kotlin/.../utils/FileStorage.jvm.kt` (actual)
    - Create: `components/shared/api/src/androidMain/kotlin/.../utils/FileStorage.android.kt` (
      actual)
    - Modify: `components/shared/api/build.gradle.kts` (add `androidMain`/`jvmMain` source sets
      if not configured, add serialization plugin)
    - Test:
      `components/game-core/api/src/commonTest/.../gamecore/shipdesign/ShipDesignSerializationTest.kt`

  **Approach:**
    - `ShipDesign` is the top-level serializable type. It contains inline `HullPieceDefinition`
      entries (vertices + armour + size category + mass) and lists of placed items (each referencing
      a hull piece by ID, with position/rotation).
    - Custom `KSerializer` for `SceneOffset` (serialize as `{x: Float, y: Float}`) and
      `AngleRadians` (serialize as `Float`). Register via `@Serializable(with = ...)` on
      wrapper types or `@Contextual` with a `SerializersModule`.
    - `FileStorage` expect/actual: `saveFile(directory: String, name: String, content: String)`,
      `loadFile(directory: String, name: String): String?`,
      `listFiles(directory: String): List<String>`. Desktop: `java.io.File` in
      `System.getProperty("user.home")/.lastfleetprotocol/designs/`. Android: `Context.filesDir`.
    - `ShipDesignRepository` interface with `save(design: ShipDesign)`, `load(name: String)`,
      `listAll()`, backed by `FileStorage` + `Json`.

  **Patterns to follow:**
    - Existing expect/actual: `composeApp/src/.../Platform.kt`
    - kotlinx.serialization custom serializer patterns from official docs

  **Test scenarios:**
    - Happy path: `ShipDesign` round-trips through JSON serialization (encode then decode produces
      equal object)
    - Happy path: `SceneOffset` serializes as `{"x": 1.0, "y": 2.0}` and deserializes back
    - Happy path: `AngleRadians` serializes as a float and deserializes back
    - Happy path: a design with multiple hull pieces, modules, and turrets serializes correctly
    - Edge case: empty design (no placed items) serializes and deserializes without error
    - Edge case: design with hull piece referenced by placed items — IDs match after round-trip

  **Verification:**
    - Serialization tests pass
    - A `ShipDesign` can be serialized to JSON string and deserialized back to an equal object

### Phase 1: Ship Builder Screen

- [ ] **Unit 3: Feature module scaffolding**

  **Goal:** Create the `features/ship-builder/api` and `features/ship-builder/impl` modules,
  wire navigation, and establish a minimal screen with the three-panel layout stub.

  **Requirements:** R1 (screen structure)

  **Dependencies:** Unit 1

  **Files:**
    - Create: `features/ship-builder/api/build.gradle.kts`
    - Create:
      `features/ship-builder/api/src/commonMain/kotlin/.../shipbuilder/ShipBuilderFeature.kt`
    - Create: `features/ship-builder/impl/build.gradle.kts`
    - Create:
      `features/ship-builder/impl/src/commonMain/kotlin/.../shipbuilder/ui/ShipBuilderScreen.kt`
    - Create: `features/ship-builder/impl/src/commonMain/kotlin/.../shipbuilder/ui/ShipBuilderVM.kt`
    - Modify: `settings.gradle.kts` (register both modules)
    - Modify: `composeApp/build.gradle.kts` (add dependencies)
    - Modify: `composeApp/.../di/AppComponent.kt` (add `@Provides` binding)
    - Modify: `composeApp/.../ui/navigation/LFNavHost.kt` (add route + parameter)
    - Modify: `components/shared/api/.../LFNavDestination.kt` (add `SHIP_BUILDER` constant)
    - Modify: landing screen to add a "Ship Builder" button that navigates to the new screen

  **Approach:**
    - `api` module: `typealias ShipBuilderScreenEntry = @Composable (NavController) -> Unit`
    - `impl` module: `ShipBuilderVM` extends
      `ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>`. Initial state
      has empty design.
    - `impl` dependencies: own `api`, `components/design`, `components/shared/api`,
      `components/game-core/api`
    - Screen composable: `Row { PartsPanel(); Canvas(); StatsPanel() }` as stubs.

  **Patterns to follow:**
    - `features/landing/api/` and `features/landing/impl/` structure exactly
    - `LFNavHost` wiring pattern
    - `AppComponent` `@Provides` pattern

  **Test expectation:** None — pure scaffolding with no behavioral logic.

  **Verification:**
    - App launches and navigates to the ship builder screen
    - Three-panel layout stub is visible
    - Back navigation works

- [ ] **Unit 4: Design canvas with grid and pan/zoom**

  **Goal:** Implement the central design canvas using Compose Canvas with a grid background,
  pan (drag) and zoom (pinch/scroll) gestures.

  **Requirements:** R1, R2, R3

  **Dependencies:** Unit 3

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/canvas/DesignCanvas.kt`
    - Create: `features/ship-builder/impl/.../shipbuilder/canvas/GridRenderer.kt`
    - Create: `features/ship-builder/impl/.../shipbuilder/canvas/CanvasState.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderScreen.kt`

  **Approach:**
    - `CanvasState` holds camera offset and zoom level. Updated by gestures.
    - `DesignCanvas` composable: `Canvas(modifier = Modifier.fillMaxSize().pointerInput(...))`.
      Gestures: drag updates camera offset, pinch/scroll updates zoom. Apply canvas transform
      (translate + scale) before drawing content.
    - `GridRenderer` draws grid lines in the visible viewport. Grid cell = 10 scene units.
      Grid lines render as thin grey lines. Origin crosshair slightly brighter.
    - Grid scales with zoom — at high zoom, grid is coarser; at low zoom, more lines visible.

  **Patterns to follow:**
    - `DebugVisualiser.kt` for DrawScope drawing patterns (line drawing, coordinate math)
    - Compose `pointerInput` + `detectTransformGestures` for pan/zoom

  **Test scenarios:**
    - Happy path: canvas renders grid lines at default zoom
    - Happy path: drag gesture pans the view (grid lines shift)
    - Happy path: pinch/scroll zooms in and out (grid lines scale)
    - Edge case: zoom clamped to reasonable min/max bounds

  **Verification:**
    - Grid is visible on screen with clear origin
    - Pan and zoom gestures work on both Desktop (mouse) and Android (touch)

- [ ] **Unit 5: Parts panel with pre-defined hull pieces and modules**

  **Goal:** Implement the left-side parts panel showing available hull pieces, internal systems,
  and turrets in collapsible categories. Clicking an item dispatches an intent to add it to the
  design.

  **Requirements:** R4, R5, R6, R34

  **Dependencies:** Unit 3, Unit 1

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/ui/PartsPanel.kt`
    - Create: `features/ship-builder/impl/.../shipbuilder/data/PartsCatalog.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (add-to-canvas
      intent)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderScreen.kt` (wire panel)

  **Approach:**
    - `PartsCatalog` provides the available parts. Phase 1 includes: the 4 hull shapes from
      `DemoScenarioConfig`, the 3 internal system types (Reactor, Engine, Bridge), and the 3
      turret types (standard, light, heavy).
    - Each part has a preview composable (small Canvas drawing the polygon outline or a module icon)
      and a display name.
    - Collapsible sections: `AnimatedVisibility` on each category header click.
    - Click dispatches `ShipBuilderIntent.AddPart(partType, partId)` -> VM adds item to design
      state centered at origin.

  **Patterns to follow:**
    - Material 3 `LazyColumn` for scrollable lists
    - Existing `LFTextButton` / `LFIconButton` from `components/design`

  **Test scenarios:**
    - Happy path: panel displays three categories (Hull Pieces, Systems, Turrets)
    - Happy path: each category is collapsible
    - Happy path: clicking a hull piece adds it to the design state at origin
    - Happy path: clicking a system module adds it to the design state
    - Edge case: adding an item when no hull piece exists on canvas — system/turret placement
      should be rejected (R11) or the item appears but is flagged as invalid

  **Verification:**
    - Parts panel shows all pre-defined parts
    - Clicking a part adds a visible item to the canvas

- [ ] **Unit 6: Canvas item rendering, selection, and drag with snap**

  **Goal:** Render placed items on the canvas, support click-to-select with outline feedback,
  and drag-to-move with grid snapping.

  **Requirements:** R7, R8, R2

  **Dependencies:** Unit 4, Unit 5

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/canvas/ItemRenderer.kt`
    - Create: `features/ship-builder/impl/.../shipbuilder/canvas/SnapUtils.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/canvas/DesignCanvas.kt` (item rendering,
      hit testing, selection, drag)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (selection state,
      move intent)

  **Approach:**
    - Items rendered in canvas draw pass: hull polygons as outlined polygons (stroke), modules as
      small rectangles or icons, turrets as small circles with a direction indicator.
    - Hit testing: on pointer down, check if click position (in canvas coordinates) intersects any
      item. Hull pieces use point-in-polygon; modules/turrets use bounding circle/rect.
    - Selection state in VM: `selectedItemId: String?`. Selected item draws with a bright cyan
      outline.
    - Drag: on pointer move while selected and dragging, update item position. On release, snap
      position to nearest grid intersection. `SnapUtils.snapToGrid(position, cellSize)` rounds
      each coordinate to nearest multiple of cell size.

  **Patterns to follow:**
    - `DebugVisualiser.kt` polygon edge drawing (vertex iteration pattern)
    - Compose `pointerInput` + `detectDragGestures`

  **Test scenarios:**
    - Happy path: placed hull piece renders as polygon outline on canvas
    - Happy path: placed module renders at its position
    - Happy path: clicking on an item selects it (cyan outline appears)
    - Happy path: clicking empty space deselects
    - Happy path: dragging a selected item moves it, snaps to grid on release
    - Happy path: `snapToGrid(SceneOffset(13, 27), 10)` returns `SceneOffset(10, 30)`
    - Edge case: `snapToGrid(SceneOffset(5, 5), 10)` returns `SceneOffset(10, 10)` (round to
      nearest, with 0.5 rounding up)
    - Edge case: overlapping items — topmost item is selected on click

  **Verification:**
    - Items visible on canvas at correct positions
    - Selection and drag work fluidly on both Desktop and Android

- [ ] **Unit 7: Transform toolbar and free-rotate handle**

  **Goal:** When an item is selected, show a transform toolbar at the bottom of the screen
  and a free-rotate handle near the item.

  **Requirements:** R9, R10

  **Dependencies:** Unit 6

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/ui/TransformToolbar.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/canvas/DesignCanvas.kt` (rotate handle
      rendering + drag gesture)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (transform intents)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderScreen.kt` (toolbar slot)

  **Approach:**
    - `TransformToolbar` composable: `Row` of icon buttons for mirror X, mirror Y, rotate CW 90,
      rotate CCW 90. Visible only when an item is selected. Each dispatches a transform intent.
    - Mirror: negate the relevant position axis relative to the item's local origin.
    - Rotate 90: add/subtract PI/2 to item's rotation.
    - Free-rotate handle: rendered as a small circle on the canvas, offset from the selected item
      in its forward (+X) direction. Dragging the handle computes the angle from item center to
      pointer position and sets the item's rotation to that angle.

  **Patterns to follow:**
    - Material 3 `IconButton` for toolbar
    - `docs/game_engine_principles.md` coordinate convention (+X forward)

  **Test scenarios:**
    - Happy path: toolbar appears when item is selected, disappears when deselected
    - Happy path: rotate CW 90 adds PI/2 to item rotation
    - Happy path: mirror X negates item's X-axis scale/flip
    - Happy path: dragging the free-rotate handle changes item rotation to match pointer angle
    - Edge case: rotation wraps correctly past 2*PI

  **Verification:**
    - Transform buttons produce visible rotation/mirror changes on the canvas item
    - Free-rotate handle allows smooth continuous rotation

- [ ] **Unit 8: Hull-constrained module placement**

  **Goal:** Enforce that internal modules and turrets can only be placed within the bounds
  of a hull piece instance. Provide visual feedback for invalid placement.

  **Requirements:** R11, R12, R13

  **Dependencies:** Unit 6

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/geometry/PointInPolygon.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/canvas/DesignCanvas.kt` (placement
      validation feedback)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (placement
      validation logic)
    - Test:
      `features/ship-builder/impl/src/commonTest/.../shipbuilder/geometry/PointInPolygonTest.kt`

  **Approach:**
    - `pointInPolygon(point, vertices): Boolean` using ray casting algorithm. The point and
      vertices are in the same coordinate space (canvas/world coordinates). For a placed hull
      piece with rotation, transform the test point into the hull's local space first.
    - When a module/turret is dragged, check if its center falls within any hull piece instance.
      If not, render the item with a red tint or dashed outline to indicate invalid placement.
    - On release, if placement is invalid, snap to nearest valid position within the closest hull
      piece (or revert to previous position).
    - Hull pieces themselves have no placement constraints (R12 — overlap allowed).

  **Patterns to follow:**
    - Standard ray casting algorithm for point-in-convex-polygon

  **Test scenarios:**
    - Happy path: point inside a convex polygon returns true
    - Happy path: point outside returns false
    - Happy path: point on polygon edge returns true (inclusive boundary)
    - Happy path: module placed inside hull piece — accepted, normal rendering
    - Happy path: module dragged outside all hull pieces — red feedback shown
    - Edge case: point-in-polygon with rotated hull piece (transform point to local space first)
    - Edge case: module overlapping two hull pieces — valid (inside at least one)
    - Edge case: triangle polygon (minimum vertex count)

  **Verification:**
    - Modules cannot be placed outside hull bounds
    - Invalid placement shows clear visual feedback
    - Point-in-polygon tests pass

- [ ] **Unit 9: Stats panel**

  **Goal:** Implement the right-side stats panel showing calculated ship stats and an editable
  design name, with a load button.

  **Requirements:** R22, R23, R25, R26

  **Dependencies:** Unit 5, Unit 2

  **Files:**
    - Create: `features/ship-builder/impl/.../shipbuilder/ui/StatsPanel.kt`
    - Create: `features/ship-builder/impl/.../shipbuilder/stats/ShipStatsCalculator.kt`
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (stats computation,
      name editing, load intent)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderScreen.kt` (wire panel)
    - Test:
      `features/ship-builder/impl/src/commonTest/.../shipbuilder/stats/ShipStatsCalculatorTest.kt`

  **Approach:**
    - `ShipStatsCalculator` takes a `ShipDesign` and computes: total mass (hull mass from size
      category + armour density contribution + system masses), forward/lateral/reverse thrust
      (sum of engine module thrust values), angular thrust, derived acceleration rates
      (thrust / mass per axis).
    - Stats update reactively when design state changes in the VM.
    - Design name: `TextField` bound to design state. Changes dispatch a rename intent.
    - Load button: opens a simple dialog listing saved designs (from
      `ShipDesignRepository.listAll()`).
      Selecting one saves current design first, then loads the selected one.
    - Minimisable: collapse/expand toggle on the panel header.

  **Patterns to follow:**
    - `ShipConfig.totalMass` computation pattern for mass calculation
    - Material 3 `TextField`, `AlertDialog` for load dialog

  **Test scenarios:**
    - Happy path: empty design shows zero mass, zero acceleration
    - Happy path: design with one hull piece shows hull mass + armour contribution
    - Happy path: adding a Reactor system increases total mass by reactor's mass value
    - Happy path: adding an Engine module increases forward thrust; acceleration = thrust / mass
    - Happy path: adding two hull pieces sums their masses
    - Edge case: design with modules but no hull — mass includes module mass but thrust may be zero
      if no engine module added

  **Verification:**
    - Stats panel shows calculated values that update live as items are added/moved
    - Design name is editable and persists
    - Load button shows list of saved designs

- [ ] **Unit 10: Auto-save integration**

  **Goal:** Wire auto-save so the design is persisted to disk after every change. Create a
  new temp design on builder launch.

  **Requirements:** R28

  **Dependencies:** Unit 2, Unit 9

  **Files:**
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderVM.kt` (auto-save on
      state change)
    - Modify: `features/ship-builder/impl/.../shipbuilder/ui/ShipBuilderScreen.kt` (init flow)

  **Approach:**
    - On builder launch, VM creates a new `ShipDesign` with a generated name (e.g.,
      "Untitled Ship YYYY-MM-DD HH:MM") and saves it immediately.
    - After every state mutation that changes the design (add/move/transform/rename), the VM
      triggers a save via `ShipDesignRepository.save(currentDesign)`.
    - Save runs on a background coroutine to avoid blocking the UI.
    - Load flow: `ShipDesignRepository.load(name)` -> deserialize -> update VM state.

  **Patterns to follow:**
    - `viewModelScope.launch` for background work (existing VM pattern)

  **Test scenarios:**
    - Happy path: launching builder creates a new design file on disk
    - Happy path: adding an item triggers auto-save (file updated on disk)
    - Happy path: loading a saved design populates the canvas with its items
    - Edge case: save failure (disk full, permission) does not crash — log error and continue

  **Verification:**
    - Design files appear in the storage directory after builder use
    - Reopening a saved design shows all previously placed items

## System-Wide Impact

- **Interaction graph:** Ship data classes move from `features/game/impl` to
  `components/game-core/api`, affecting imports across game actors, managers, tests, and
  `DemoScenarioConfig`. The builder module depends on `game-core/api` for data types and
  `shared/api` for file storage.
- **Error propagation:** File I/O errors (save/load) should be caught and logged, not crash
  the app. Invalid JSON on load should show an error message and fall back to a new design.
- **State lifecycle risks:** Auto-save during rapid drag could cause excessive writes. Consider
  a debounce (deferred to implementation). Concurrent save and load is not expected (single user,
  single builder instance).
- **API surface parity:** `ShipDesign` is a new serialization format — no existing consumers.
  Phase 3 will add a converter to `ShipConfig` for game simulation.
- **Unchanged invariants:** The game simulation, combat system, AI, and existing screens are
  unaffected. Ship data classes retain their structure — only their module location changes.

## Risks & Dependencies

| Risk                                                     | Mitigation                                                                                                                    |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| Moving data classes breaks existing game code            | Mechanical refactor — update imports only. Run full test suite after.                                                         |
| `GunData` / `Gun` split introduces subtle bugs           | `Gun` keeps runtime logic unchanged. `GunData` is pure data — no behavior to break.                                           |
| Compose Canvas gesture handling differs across platforms | Use `detectTransformGestures` which abstracts mouse/touch. Test on both Desktop and Android.                                  |
| kotlinx.serialization of Kubriko value classes           | Custom serializers for `SceneOffset` and `AngleRadians` are straightforward (decompose to floats). Test with round-trip.      |
| File I/O expect/actual platform differences              | Desktop uses `java.io.File` (well-understood). Android uses `Context.filesDir`. Both are simple string-based file operations. |
| Point-in-polygon edge cases with rotated hull pieces     | Transform test point into hull local space before testing. Unit test with rotated polygons.                                   |

## Documentation / Operational Notes

- Update `docs/ai_architecture.md` to note that ship data classes now live in
  `components/game-core/api` rather than `features/game/impl`.
- No deployment, monitoring, or operational concerns — this is a local development tool.

## Sources & References

- **Origin document:
  ** [docs/brainstorms/2026-04-06-ship-builder-requirements.md](docs/brainstorms/2026-04-06-ship-builder-requirements.md)
- Related code: `features/game/impl/.../data/` (ship data classes),
  `features/game/impl/.../data/DemoScenarioConfig.kt`
- Related code: `features/landing/` (feature module pattern reference)
- Related code: `features/game/impl/.../debug/DebugVisualiser.kt` (DrawScope drawing patterns)
- Related code: `composeApp/.../di/AppComponent.kt`, `composeApp/.../ui/navigation/LFNavHost.kt`
- Design docs: `docs/game_engine_principles.md` (coordinate system, data-driven design)
