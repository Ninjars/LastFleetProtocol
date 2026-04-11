---
title: "feat: Ship Builder Phase 3 — game integration (bridge builder designs to runtime)"
type: feat
status: active
date: 2026-04-11
origin: docs/brainstorms/2026-04-06-ship-builder-requirements.md
---

# feat: Ship Builder Phase 3 — Game Integration

## Overview

Replace the hard-coded `DemoScenarioConfig` ships with builder-authored ship designs loaded at runtime. Build a conversion layer that bridges the builder's `ShipDesign` format (`List<PlacedHullPiece>` + `List<PlacedModule>` + `List<PlacedTurret>` + `List<ItemDefinition>`) to the runtime `ShipConfig` format consumed by `GameStateManager.createShip`. Extend `ShipConfig.hull` from a single `HullDefinition` to `hulls: List<HullDefinition>` so multi-hull designs survive the conversion unchanged. Replace sprite-based ship rendering with polygon rendering derived from hull vertices, matching the recent bullet vector-rendering refactor and removing the missing drawable→resource registry entirely. Ship the first real playable builder→combat loop for the project.

This plan is a prerequisite for the atmospheric movement work (`docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`). It ships against the **current frictionless physics** — atmospheric is a separate plan that runs after this one.

## Problem Frame

The ship builder currently produces `ShipDesign` JSON files that nothing in the game simulation knows how to load. `GameStateManager.startDemoScene` instantiates ships from hard-coded `ShipConfig` constants in `DemoScenarioConfig.kt`. Two entirely separate data models coexist:

- The **builder model** (`ShipDesign`, `ItemDefinition`, `PlacedHullPiece/Module/Turret`) is rich, convex-polygon-based, multi-hull-aware, and loose-typed (e.g., `systemType: String`).
- The **runtime model** (`ShipConfig`, `HullDefinition`, `InternalSystemSpec`, `TurretConfig`, `GunData`) is flat, single-hull, enum-based, and fully-specified.

Until these two models are bridged:
- The ship builder is a dead-end tool — its output cannot be played.
- The atmospheric movement brainstorm cannot be validated because it depends on a working builder→combat loop.
- Success criteria for the builder (R31, R32 from the origin doc) are unmet.

Gap summary (full field-by-field mapping in Context & Research below):

| Area | Builder | Runtime | Resolution |
|---|---|---|---|
| Hulls | `List<PlacedHullPiece>` (multi-hull) | `HullDefinition` (single) | **Extend runtime to `List<HullDefinition>`** (Unit 1) |
| Module type | `systemType: String` | `InternalSystemType` enum | Converter validates and maps; rejects unknowns (Unit 4) |
| Module count per type | Unrestricted | `Map<InternalSystemType, _>` (1 each) | Converter aggregates by type (Unit 4) |
| Turret gun specs | `turretConfigId: String` only | Full `GunData` record | **New `TurretGunRegistry`** keyed by `turretConfigId` (Unit 2) |
| Ship sprite | Not stored | `drawable: DrawableResource` | **Polygon rendering — remove the field** (Unit 3) |
| Combat stats | Not stored | `CombatStats.evasionModifier` | Default value; future builder field (Unit 4) |
| Startup ship source | Saved files only | Hard-coded constants | **Bundled JSON designs under composeResources** (Units 5–7) |

## Requirements Trace

From `docs/brainstorms/2026-04-06-ship-builder-requirements.md`:

- **R31.** Ship designs saved by the builder can be loaded into the game simulation, replacing the role of `DemoScenarioConfig` hardcoded ship definitions.
- **R32.** Everything currently used to create ships in `GameStateManager.createShip()` must be suppliable from a ship design file. No simulation data should exist only in code constants.

From `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md` (this plan is a prerequisite):

- **A.R26** (atmospheric): "Phase 3 is an unplanned prerequisite that must be scoped and at least partially planned before atmospheric Slice A planning begins." — this plan fulfils that prerequisite. Phase 3 ships against current frictionless physics; atmospheric replaces physics afterward without needing a second refactor of the conversion layer, because the conversion produces `ShipConfig` values the atmospheric plan will reinterpret rather than restructure.

## Scope Boundaries

- **No physics changes.** MovementConfig is passed through unchanged (thrust values match what `ShipStatsCalculator` already computes). The frictionless model stays intact. Atmospheric is a separate plan.
- **No new builder UI for gun specs.** Turret gun data is supplied by a server-side `TurretGunRegistry` keyed by `turretConfigId`. The builder does not gain fields for damage, reload, projectile stats, etc. A future follow-up can expose them.
- **No new builder UI for combat stats.** `CombatStats.evasionModifier` is defaulted at conversion time. Future work if needed.
- **No Keel support.** Keel is a Slice B atmospheric concept; this plan stays with the current hull/module/turret model.
- **No damage-model changes.** The existing per-type subsystem damage routing (`REACTOR`, `MAIN_ENGINE`, `BRIDGE`) is preserved. No new subsystem enum values.
- **No save-format migration.** `ShipDesign.formatVersion` stays at 2. Existing saved designs continue to load. No breaking change to the serialized format.
- **No player-authored designs loaded at startup.** Startup loads only the bundled default designs. Loading user-saved designs into scenarios is a natural follow-up but out of scope here — the goal is unblocking the playable loop, not building a full campaign loader.
- **`DemoScenarioConfig` is retired as runtime data** but may survive as a test fixture helper if useful. Its numeric values migrate into bundled JSON designs.
- **Polygon rendering is minimal in this plan** — ships render as convex hull outlines with fill colour distinguishing team. Detailed ship aesthetics (shading, lighting, decals) are out of scope and can come later.

## Context & Research

### Relevant Code and Patterns

**Builder side**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` — serialized design format, `formatVersion: Int = 2`, fields: `name`, `itemDefinitions`, `placedHulls`, `placedModules`, `placedTurrets`.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt` — sealed `ItemAttributes` with `HullAttributes`/`ModuleAttributes`/`TurretAttributes`, per-module thrust values (`forwardThrust`, `lateralThrust`, `reverseThrust`, `angularThrust`), `systemType: String` (loose-typed).
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt` — canonical source for summed ship stats (thrust, mass, accel). Reused by the converter to populate `MovementConfig`.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt` — existing save/load via platform `saveFile`/`loadFile` abstractions. The game side can reuse `ShipDesignRepository` (declared in game-core/api).

**Runtime side**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt` — target type for conversion. Current `hull: HullDefinition` becomes `hulls: List<HullDefinition>` in Unit 1.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/InternalSystemSpec.kt` — contains `enum class InternalSystemType { REACTOR, MAIN_ENGINE, BRIDGE }` and the `InternalSystemSpec` record.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` (around lines 51–155) — hosts `startDemoScene()` and `createShip(config, …)`. The integration surgical point is `startDemoScene`'s body.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — the four hard-coded `ShipConfig` instances that Unit 7 retires.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt` — bridge extractor `ShipSpec.fromConfig`. Updates alongside Unit 1 to read from `hulls: List<HullDefinition>`.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt` — reads hull for collision. Updates alongside Unit 1 if collision is hull-aware.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — consumer of `ShipSpec`; Unit 3 replaces its drawable-based renderer with polygon rendering.

**Vector rendering precedent**
- Recent commit `da0a983 feat: decided I liked the vector bullet rendering, removed bullet sprite` and `e67d2ea feat: refactored Bullet to draw as a vector circle, rather than sprite` — the project is already trending toward vector rendering and away from sprites. Unit 3's polygon ship rendering continues this direction.

**Shared infrastructure**
- `components/game-core/api` is accessible to both `features/game/impl` and `features/ship-builder/impl`. The converter lives here, as does the shared `ShipDesignRepository` interface. This avoids cross-feature coupling.
- kotlinx.serialization with polymorphic sealed-class support (already in use for `ItemAttributes`).
- kotlin-inject DI with `@Provides` in `composeApp/src/commonMain/kotlin/.../di/AppComponent.kt`.

### Institutional Learnings

`docs/solutions/` does not exist. No prior incident knowledge on this specific bridge. Forward-looking design docs worth checking:
- `docs/ai_architecture.md` — module dependency rules; the api/impl boundary constraint is preserved by putting the converter in `components/game-core/api`.
- `docs/ai_kubriko_constraints.md` — hot-path and allocation guidance; polygon rendering should avoid per-frame `SceneOffset` churn.
- `docs/ship.md` — canonical ship model description.

### External References

None. This is a pure internal refactor; no new frameworks or external patterns.

## Key Technical Decisions

1. **Extend `ShipConfig.hull: HullDefinition` to `hulls: List<HullDefinition>`**, and update all runtime consumers to iterate. Future-proofs multi-hull for atmospheric; avoids a second migration.
2. **Polygon-based ship rendering replaces sprite rendering entirely**, matching the recent bullet vector refactor. Removes the `DrawableResource` registry problem. Ships render as hull outlines (fill colour by team).
3. **Converter lives in `components/game-core/api`** as a pure function. Both builder and runtime modules depend on api, so neither has to reach across the feature boundary. The converter is `ShipDesign → ShipConfig`, not `ShipDesign → Ship` — keeping the existing `ShipConfig → Ship` path unchanged.
4. **Multiple modules of the same type aggregate**: multiple `REACTOR` modules in a design sum their `maxHp`, `density`, and `mass` into a single `InternalSystemSpec(REACTOR, ...)`. Preserves the runtime's single-instance-per-type model without refactoring `ShipSystems`. Known limitation; documented.
5. **Unknown `systemType` strings cause conversion to fail loudly** rather than silently dropping modules. Validation pass before construction; returns a `Result<ShipConfig>` or similar.
6. **Turret gun specs live in a new `TurretGunRegistry`** (in `components/game-core/api`), keyed by `PlacedTurret.turretConfigId`. Initially hard-coded with the guns from `DemoScenarioConfig`. Builder-side gun specs are a future follow-up, not this plan.
7. **`CombatStats.evasionModifier`** is defaulted at conversion time. No builder field in this plan; value matches the current `DemoScenarioConfig` defaults so playability is unchanged.
8. **Default ship designs ship as JSON files** under `composeResources/files/default_ships/`. At startup the game loads them via a new `DefaultShipDesignLoader` that reads the bundled resources. User-saved designs reuse the existing `FileShipDesignRepository` path — they are not auto-loaded into scenarios in this plan.
9. **`MovementConfig` is computed by `ShipStatsCalculator`** during conversion (the same code the builder uses for its stats panel). This means runtime movement numerically matches what the builder displays — no drift between what the player sees while designing and what they experience in combat.
10. **`DemoScenarioConfig` is deleted as runtime data** after Unit 7 is working. Its numeric values migrate into the bundled JSON files (Unit 6) so playability continuity is preserved. The file may live on briefly as a test fixture during transition.
11. **Frictionless physics is unchanged.** `ShipPhysics` and `ShipNavigator.computeBrakingStrategy` are untouched. Atmospheric is a separate plan that follows this one.

## Open Questions

### Resolved During Planning

- **Hull representation at runtime?** → Extend to `List<HullDefinition>`. Preserves multi-hull for atmospheric.
- **Ship visual rendering?** → Polygon vector rendering, removes drawable-resolution gap.
- **Default ship designs source?** → Bundled JSON files under commonMain resources.
- **Conversion layer location?** → `components/game-core/api`, pure function.
- **Multiple modules of the same type?** → Aggregate at conversion time with a sum rule for `maxHp` and `mass`; the aggregation rule for `density` (average vs max) is deferred to implementation. Single-instance-per-type runtime model is preserved. (Caveat surfaced by review: aggregation collapses `disableThreshold = maxHp * 2/3` across modules, changing effective resilience — see P1 finding on disable-threshold semantics.)
- **Turret gun specs location?** → `TurretGunRegistry` in game-core/api, hard-coded initially, keyed by `turretConfigId`.
- **`CombatStats.evasionModifier` source?** → Default value at conversion; no builder UI field in this plan.
- **Sequencing with atmospheric?** → Phase 3 ships first against current frictionless physics. Atmospheric is a separate plan.

### Deferred to Implementation

- **Exact polygon rendering style.** Line width, team colours, fill vs stroke, transparency. Start with a plain convex outline matching the builder's canvas preview; tune after visual inspection.
- **Polygon rendering under transform.** Placed hulls carry `position`, `rotation`, `mirrorX`, `mirrorY`. The transform composition is straightforward linear algebra but the exact helper placement (Ship actor? HullDefinition method? a shared utility?) should be decided while implementing.
- **Exact `TurretConfig` offset/pivot derivation from `PlacedTurret`.** Builder stores `position` (world-space under ship) and `rotation`. Runtime wants `offsetX/Y` and `pivotX/Y`. Trivial conversion, but the precise pivot rule (centroid of turret polygon? stored in `TurretAttributes`? geometric centre?) needs to be picked during implementation.
- **`DefaultShipDesignLoader` API.** Whether it uses the existing platform `loadFile` abstraction (reading from bundled resource path) or Compose Resources API (`Res.readBytes`). Decide during implementation based on which is cleaner for reading commonMain resource files.
- **Handling of collision for multi-hull ships.** Current `ShipPhysics` treats the hull as a single polygon. For Phase 3, Unit 1 either (a) iterates collision against each hull separately or (b) computes a convex hull of all hull pieces as an approximation. Picking this live with the test scenarios.
- **Whether to retain `DemoScenarioConfig` as a test-fixture helper** after Unit 7 wires in the bundled loader. If it becomes useful in tests, keep it under test sources; otherwise delete.
- **Whether `ShipConfig` should carry a computed `totalMass` or `hulls.totalMass` aggregation helper** or the existing property just sums over the list. Minor refactor decision.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
Builder-side                  Shared (game-core/api)                Runtime-side
────────────                  ──────────────────────                ────────────

ShipDesign (JSON)    ──load──►  DefaultShipDesignLoader    ──►  GameStateManager
  ├─ itemDefinitions              (reads composeResources)       .startDemoScene()
  ├─ placedHulls                                                     │
  ├─ placedModules                                                   │ for each design:
  └─ placedTurrets                                                   │
                                ShipDesignConverter               convert(design)
                                  (pure function)                      │
                                   │                                   │
                                   │ ┌─ resolve ItemDefinitions        ▼
                                   │ ├─ transform hull pieces    ShipConfig (new: hulls=List)
                                   │ ├─ aggregate modules → enum      │
                                   │ ├─ lookup turret guns            │
                                   │ │   via TurretGunRegistry        │
                                   │ └─ compute MovementConfig        │
                                   │     via ShipStatsCalculator      ▼
                                   ▼                              createShip(config)
                                 ShipConfig                           │
                                                                      │
                                                                      ▼
                                                                  Ship actor
                                                                  (polygon-rendered from hulls)
```

Conceptual notes:
- The converter is the single seam. It's pure (no I/O, no actor instantiation), so it's cheap to test exhaustively.
- `ShipStatsCalculator` is shared via the api module. Builder uses it for live stats; converter uses it to produce `MovementConfig`. One formula, two consumers.
- `TurretGunRegistry` and `DefaultShipDesignLoader` are both small singletons in the api module. The registry is hard-coded; the loader reads bundled files. DI-injected so tests can swap them.
- Polygon rendering eliminates the `drawable` field entirely. `Ship` actor and `Ship` rendering both lose a dependency.

## Implementation Units

### Unit 1: Extend `ShipConfig.hull` to `hulls: List<HullDefinition>`

**Goal:** Change the runtime hull representation from a single `HullDefinition` to a list, and update all consumers to iterate. Preserves multi-hull designs through the conversion.

**Requirements:** R31, R32 (data fidelity); A.R26 prerequisite.

**Dependencies:** None. This is the foundation.

**Files:**
- Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt`
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt` — `hull: HullDefinition` → `hulls: List<HullDefinition>`; update `fromConfig` accordingly.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — any use of `spec.hull` → `spec.hulls`.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt` — if hull is referenced for collision, iterate; otherwise note what changes.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — wrap each existing `hull = HullDefinition(...)` in `hulls = listOf(HullDefinition(...))`. This is a temporary edit to keep `DemoScenarioConfig` compiling through Units 1–6; it's retired in Unit 7.
- Modify: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ShipConfigTest.kt` — update assertions to iterate over `hulls`.
- Test: existing `ShipConfigTest` is the primary coverage.

**Approach:**
- `ShipConfig` field rename + type change. The computed `totalMass` property sums over `hulls` instead of reading one.
- Collision and damage code that references "the hull" needs to iterate. Phase 3 uses the simplest working approach: treat each hull polygon as an independent collidable surface. If that proves excessive in practice, a follow-up can compute a simplified collision polygon.
- `ShipSpec.fromConfig` passes the full list through.
- Rendering references are untouched in this unit — polygon rendering comes in Unit 3. `Ship`'s current sprite-rendering path keeps working because it uses `drawable`, not the hull.

**Patterns to follow:**
- Existing `ShipConfig.totalMass` computed-property style for the new list-aware version.
- Kubriko actor conventions — `Ship`'s current update/draw loop.

**Test scenarios:**
- Happy path: `ShipConfig` with a single-element `hulls` list round-trips through `ShipSpec.fromConfig` and produces a `ShipSpec` whose `hulls.size == 1`.
- Happy path: `ShipConfig` with a two-element `hulls` list (multi-hull design) produces a `ShipSpec` whose `hulls.size == 2` and whose `totalMass` is the sum of both hulls' masses + their armour contributions.
- Edge case: empty `hulls` list is either rejected at construction or produces a `ShipSpec` with `hulls.isEmpty()` and clear semantics — pick and test explicitly. (Empty hulls shouldn't happen in practice; the test documents the chosen invariant.)
- Regression: all existing `DemoScenarioConfig` ships construct successfully after the single→list wrap and pass `ShipConfigTest`'s validation assertions (positive mass, positive thrust, vertex count ≥ 3 per hull, system health > 0).
- Regression: `ShipPhysics` collision/integration continues to work for single-hull ships (covered by existing physics tests if any; otherwise a minimal integration sanity test).

**Verification:**
- All existing tests pass after the refactor.
- The project compiles and the desktop app runs.
- `DemoScenarioConfig` ships spawn and fight as before — no visible behaviour change yet.

---

### Unit 2: `TurretGunRegistry`

**Goal:** Introduce a lookup table that maps a `PlacedTurret.turretConfigId: String` to a runtime `GunData` record. Seed it with the guns currently defined in `DemoScenarioConfig`. This becomes the single source of truth for turret weapon specs, independent of the builder's UI.

**Requirements:** R31 (turret data must come from somewhere during conversion).

**Dependencies:** None. Independent of Unit 1.

**Files:**
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/TurretGunRegistry.kt`
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — `@Provides` the registry as a singleton.
- Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/TurretGunRegistryTest.kt`

**Approach:**
- Simple `object TurretGunRegistry` or a DI-injected class exposing `fun lookup(turretConfigId: String): GunData?`.
- Internally a `Map<String, GunData>`. Seeded at construction with entries for every turret id the current `DemoScenarioConfig` references (standard turret, light turret, heavy turret — extract the exact set while implementing).
- Unknown ids return `null`; the converter treats `null` as a validation failure in Unit 4.
- The registry keys match whatever string ids the builder's parts catalog already uses for turrets (`features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/PartsCatalog.kt` — inspect to find the actual ids).

**Patterns to follow:**
- `PartsCatalog` structure as a parallel "data table" pattern on the builder side.
- kotlin-inject `@Provides` convention in `AppComponent.kt` for DI wiring.

**Test scenarios:**
- Happy path: looking up a known `turretConfigId` (e.g., the standard-turret id used by the player ship) returns a non-null `GunData` with positive damage, positive reload, non-empty projectile stats.
- Happy path: every turret id present in `DemoScenarioConfig`'s current turret configs resolves to a valid `GunData` — no gaps.
- Edge case: looking up an unknown id returns `null`.
- Edge case: looking up an empty string returns `null`.

**Verification:**
- Unit tests pass.
- Every turret id currently used by `DemoScenarioConfig` is in the registry. A helper test enumerates them and asserts registry coverage.

---

### Unit 3: Polygon-based ship rendering

**Goal:** Replace the `Ship` actor's sprite rendering with polygon rendering over the hull vertices. Remove `drawable: DrawableResource` from `ShipConfig` and `Ship`. Each hull in `hulls` is drawn as a convex outline with a team-colour fill.

**Requirements:** R32 (no simulation data should exist only in code constants — drawable references were a code-constant leak).

**Dependencies:** Unit 1 (needs `hulls: List<HullDefinition>` to iterate).

**Files:**
- Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt` — remove `drawable` field.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — replace sprite-drawing path with polygon drawing; loop over `hulls`, apply ship pose, render each.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — `createShip` signature drops the drawable param.
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — remove `drawable = Res.drawable.ship_player_1` / `ship_enemy_1` lines.
- Possibly delete: `Res.drawable.ship_player_1`, `ship_enemy_1` sprite assets under `components/design/src/commonMain/composeResources/drawable/` — only if no other caller uses them.
- Test: no new unit test file (polygon rendering is inspected manually against the builder preview), but existing `ShipConfigTest` must still pass with the field removal.

**Approach:**
- Use the same drawing primitives the `Bullet` actor uses for its vector circle rendering (see recent `e67d2ea` and `da0a983` commits for the pattern — read `Bullet.kt` for the exact shape of the approach).
- For each hull in `hulls`, compute the world-space vertex positions by applying the ship's pose (position + rotation + mirror) to the hull's stored vertices, then draw a closed polygon.
- Team colour: player ships get one colour, enemy ships another. Hard-coded for Phase 3; a future rendering pass can introduce per-ship palettes.
- No per-frame `SceneOffset` allocations in the hot path — precompute reusable vertex buffers per ship where possible. See `docs/ai_kubriko_constraints.md`.

**Patterns to follow:**
- Bullet's recent vector-rendering refactor: `features/game/impl/.../game/actors/Bullet.kt` (or wherever bullet rendering moved to after `da0a983`).
- Ship builder canvas polygon rendering: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/canvas/ItemRenderer.kt` — same style the player sees in the builder.

**Test scenarios:**
- Happy path (visual): run the desktop app, confirm ships appear as polygon outlines in combat and are distinguishable by team colour.
- Happy path (compile-time): `ShipConfig` has no `drawable` field; the entire codebase compiles.
- Regression: existing ships continue to appear in the correct positions — no drawing-offset regressions from the sprite→polygon switch.
- Edge case: a ship with a multi-hull design draws all hulls at their correct transformed positions.

**Verification:**
- The desktop app renders ships as polygons and combat is playable.
- No references to `Res.drawable.ship_player_1` or `Res.drawable.ship_enemy_1` remain in runtime code (grep for residuals).
- Allocation-sensitive rendering: spot-check that the polygon draw path doesn't allocate `SceneOffset` per vertex per frame.

---

### Unit 4: `ShipDesignConverter` — the conversion layer

**Goal:** Pure function converting a `ShipDesign` to a `ShipConfig`. This is the core of Phase 3. Handles hull transformation, module aggregation, turret gun lookup, stats calculation, and validation of loose-typed strings.

**Requirements:** R31, R32.

**Dependencies:** Unit 1 (needs `hulls: List<HullDefinition>`), Unit 2 (`TurretGunRegistry`).

**Files:**
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt`
- Create: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverterTest.kt`
- Possibly modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt` — if the calculator needs lifting into `components/game-core/api` so the converter can use it (depends on current module boundary).

**Approach:**
- Signature roughly: `fun convert(design: ShipDesign, gunRegistry: TurretGunRegistry): Result<ShipConfig>` or equivalent returning either a success with `ShipConfig` or a descriptive failure.
- **Hull conversion** (`placedHulls` → `List<HullDefinition>`):
  - For each `PlacedHullPiece`, resolve its `itemDefinitionId` to the `ItemDefinition` in `design.itemDefinitions`.
  - Apply the placed transform (position, rotation, mirrorX, mirrorY) to the item's stored vertices to produce world-space (ship-space) vertices.
  - Build a `HullDefinition` with those vertices plus the `HullAttributes.armour` (promoted from `SerializableArmourStats`) and `mass`.
  - Each placed hull piece becomes one `HullDefinition` in the output list. Multi-hull is preserved.
- **Module conversion** (`placedModules` → `List<InternalSystemSpec>`):
  - Group by `systemType`.
  - For each group, map the string to `InternalSystemType` enum. Unknown strings → return failure with a descriptive message.
  - Aggregate within a group: sum `maxHp`, `mass`, use an averaging or max rule for `density` (decide during implementation; document the choice in the test).
  - Emit one `InternalSystemSpec` per resolved type.
  - If a design has no modules of a given type (e.g., no BRIDGE), that type is simply absent from the output. The runtime currently tolerates missing subsystems — `Ship` destruction only triggers on reactor destruction — so a ship without BRIDGE is valid.
- **Turret conversion** (`placedTurrets` → `List<TurretConfig>`):
  - For each `PlacedTurret`, look up `turretConfigId` in `TurretGunRegistry`. Unknown id → return failure.
  - Derive `offsetX/Y` from the placed turret's `position`.
  - Derive `pivotX/Y` from the item definition (centroid of turret polygon, or explicit pivot field if added; decide during implementation).
  - Build a `TurretConfig` with the resolved `GunData`.
- **MovementConfig** computed via the shared `ShipStatsCalculator` — call it on the design and copy thrust values into a new `MovementConfig`. This is the parity guarantee: what the builder shows and what combat uses are the same numbers.
- **CombatStats**: default `evasionModifier = 1.0f` (or the current `DemoScenarioConfig` default — match it for playability).
- **Validation outcomes**: the function returns `Result<ShipConfig>` (or a sealed `ConversionResult`). Failure cases include: unknown systemType string, unknown turret id, zero hull pieces, zero modules, malformed transforms.

**Patterns to follow:**
- `ShipStatsCalculator` as the canonical stats source — do not reimplement thrust aggregation.
- kotlinx.serialization polymorphic handling for resolving `ItemAttributes` subclasses.
- Pure-function pattern: no side effects, no I/O, no logging except via the `Result` return.

**Test scenarios:**
- Happy path: a minimal `ShipDesign` with one hull piece, one reactor module, one standard turret, and one default-engine module produces a `ShipConfig` with `hulls.size == 1`, `internalSystems` containing exactly a REACTOR and a MAIN_ENGINE entry, one `TurretConfig` with the correct `GunData`, and a non-zero `MovementConfig.forwardThrust`.
- Happy path: a multi-hull design (two placed hull pieces) produces a `ShipConfig` with `hulls.size == 2`.
- Happy path: two REACTOR modules in the design are aggregated into a single `InternalSystemSpec(REACTOR)` whose `maxHp` and `mass` are the sums of the two input modules.
- Happy path: transforms are respected — a hull piece placed at `(10, 20)` with rotation 90° produces a `HullDefinition` whose vertices are translated and rotated correspondingly. Include a numeric assertion on at least one vertex.
- Happy path: thrust values in the output `MovementConfig` match what `ShipStatsCalculator.calculateStats` produces for the same design. Identity assertion, not just "positive."
- Edge case: a design with zero placed modules produces a `ShipConfig` whose `internalSystems` is empty. Runtime tolerates this (ship has no destroyable reactor until one is added).
- Edge case: a design with a placed module whose `systemType` is "REACTOR " (trailing space) — decide whether the mapping trims whitespace or rejects. Document the choice.
- Edge case: a design with a hull piece referencing a non-existent `itemDefinitionId` is rejected with a descriptive error.
- Error path: a design with an unknown `systemType` string (e.g., "PLASMA_CORE") returns a failure with the unknown string in the error message.
- Error path: a design with a turret whose `turretConfigId` is not in the registry returns a failure with the missing id.
- Error path: a design with zero hull pieces is rejected (a ship must have at least one hull).
- Integration: take one of the target bundled default designs (Unit 5's output) and run it through the converter; assert the resulting `ShipConfig` is close enough to the current hard-coded `DemoScenarioConfig` player ship that playability is preserved. "Close enough" means same type counts, same hull vertex counts, same total mass within a small tolerance.

**Verification:**
- All converter unit tests pass.
- The converter is pure — no I/O, no `println`, no DI container access.
- Round-tripping a design through `ShipDesignConverter.convert` and back to a new `ShipDesign` (if a reverse converter is added later) would be lossless for the fields this plan cares about.

---

### Unit 5: Bundled default ship design JSON resources

**Goal:** Author the four replacement ship designs (player, light enemy, medium enemy, heavy enemy) as JSON files bundled into commonMain resources. These ship with the app and are loaded at game startup.

**Requirements:** R31, R32 (replacing `DemoScenarioConfig`).

**Dependencies:** Unit 4 (the converter needs to work so authored designs can be validated before committing).

**Files:**
- Create: `components/game-core/api/src/commonMain/composeResources/files/default_ships/player_ship.json`
- Create: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_light.json`
- Create: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_medium.json`
- Create: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json`
- Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/DefaultShipDesignsTest.kt`

**Approach:**
- Author the designs in the ship builder (this is the actual process — the dev opens the builder, builds the four ships, saves them, exports the JSON, commits it).
- Numeric values (hull vertices, mass, armour, thrust, HP, turret positions) should match the current `DemoScenarioConfig` values as closely as the builder allows — so that combat feel is preserved through the migration.
- The JSON files use the existing `ShipDesign` serialization format — no new format, no version bump.
- Each default design's `name` field matches a convention (e.g., `"Default Player"`, `"Default Enemy Light"`) so the loader can identify them.
- **This unit is partially content work.** The code deliverable is only the directory structure and the test that validates the JSON files parse and convert successfully. The JSON content itself is produced by the dev sitting in the builder.

**Patterns to follow:**
- Existing `ShipDesignSerializationTest.kt` for the JSON shape reference.
- Existing commonMain composeResources folder conventions.

**Execution note:** This unit has a content-authoring component that cannot be fully automated. The test scenarios validate what's committed, but the designs themselves are hand-authored in the builder UI.

**Test scenarios:**
- Happy path: each of the four JSON files parses as a valid `ShipDesign` without errors.
- Happy path: each parsed `ShipDesign` passes through `ShipDesignConverter.convert` successfully and produces a valid `ShipConfig`.
- Happy path: each converted `ShipConfig` has a `totalMass` within ±5% of the corresponding current `DemoScenarioConfig` ship's `totalMass`, and a `movementConfig.forwardThrust` within ±5% of the current value. This is the playability-preservation assertion.
- Happy path: each converted `ShipConfig` has the same number of turrets as its `DemoScenarioConfig` counterpart.
- Regression: `ShipDesignSerializationTest` continues to pass (the format is unchanged).

**Verification:**
- The four JSON files exist and are committed.
- All test scenarios pass.
- A manual check: loading each default design in the builder UI renders it correctly.

---

### Unit 6: `DefaultShipDesignLoader` — load bundled JSON resources

**Goal:** A loader that reads the bundled default-ship JSON files from commonMain resources and returns a `List<ShipDesign>` at startup. Exposes them to `GameStateManager` via DI.

**Requirements:** R31, R32.

**Dependencies:** Unit 5 (the files must exist to load them).

**Files:**
- Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/DefaultShipDesignLoader.kt`
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — `@Provides` the loader.
- Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/DefaultShipDesignLoaderTest.kt`

**Approach:**
- Loader signature: `suspend fun loadAll(): List<ShipDesign>`.
- Uses Compose Resources API (`Res.readBytes("files/default_ships/player_ship.json")`) or the existing platform `loadFile` abstraction — pick whichever is cleaner for reading bundled files. Decision deferred to implementation.
- The list of filenames to load is either hardcoded (four filenames) or discovered (list directory contents). Hardcoded is simpler and safer — the list is short and stable. Use that.
- Errors in parsing an individual file log a warning but do not crash startup. Missing files are a test failure (bundled resources should always be present).

**Patterns to follow:**
- `FileShipDesignRepository.loadAll()` for the parsing approach (kotlinx.serialization deserialize).
- kotlin-inject provisioning pattern for singleton DI.

**Test scenarios:**
- Happy path: `loadAll()` returns a non-empty list containing all four default designs.
- Happy path: each returned `ShipDesign` has a non-empty `name`, `placedHulls`, and `itemDefinitions`.
- Edge case: the order of returned designs is stable across calls (for deterministic startup).
- Integration: `loadAll()` returns designs that can be passed to `ShipDesignConverter.convert` without failure.

**Verification:**
- Unit tests pass.
- Starting the desktop app produces no loader errors in stdout/logs.

---

### Unit 7: Rewrite `GameStateManager.startDemoScene` to use the loaded designs

**Goal:** Replace the hard-coded `createShip(config = DemoScenarioConfig.playerShipConfig, ...)` calls in `startDemoScene` with: load the default designs → convert each to `ShipConfig` → call `createShip` per converted config. Preserve the current 2-player-vs-4-enemy layout and positions.

**Requirements:** R31, R32.

**Dependencies:** Units 1–6.

**Files:**
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — rewrite `startDemoScene` body.
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — `GameStateManager` gains `DefaultShipDesignLoader`, `ShipDesignConverter`, and `TurretGunRegistry` dependencies. Wire them via kotlin-inject.
- Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt` (create if absent) — minimal integration test that `startDemoScene` can run to completion.

**Approach:**
- At the top of `startDemoScene`, call `defaultShipDesignLoader.loadAll()` (suspending; `startDemoScene` is already inside a coroutine context).
- Partition the loaded designs by name or by a team marker embedded in the name convention — simpler: the player design is the one whose `name` starts with "Default Player", the rest are enemies.
- For each design, convert to `ShipConfig`. On failure, log a loud error and fall back to the old hardcoded config (or just skip). The test must cover the happy path.
- Call `createShip(config, position, teamId, targetProvider, aiModules, drawOrder)` once per converted config. Keep the existing position/team/AI arguments as they were — the layout is unchanged.
- The 2-player arrangement may reuse the same player design twice (place at two positions). The 4 enemy positions use the three enemy designs, repeating medium if needed. Preserve the current spawn count and arrangement.

**Patterns to follow:**
- The existing `startDemoScene` structure — just replace the data source for `config` without changing the orchestration.

**Test scenarios:**
- Happy path (integration): `startDemoScene` runs to completion with the real `DefaultShipDesignLoader`, converter, and `TurretGunRegistry`. After it returns, `playerShips.size == 2` and `enemyShips.size == 4` (matching current behaviour).
- Happy path: each spawned ship has a non-null position, a non-empty `hulls`, and a non-empty turret list (where applicable).
- Error path: if `DefaultShipDesignLoader.loadAll()` throws, `startDemoScene` surfaces a clear error rather than silently spawning zero ships. Decide between "throw and crash" and "log and no-op" during implementation; the test documents the choice.
- Regression: the desktop app starts, the combat scene spawns six ships, and combat is playable from the moment this unit lands.

**Verification:**
- Desktop app starts and combat is playable.
- Six ships spawn (2 player, 4 enemy).
- Ships visibly render as polygons (from Unit 3).
- Behaviour parity: subjective "combat feels the same" check against the branch before this plan.

---

### Unit 8: Retire `DemoScenarioConfig` as runtime data

**Goal:** Delete the hard-coded `ShipConfig` constants in `DemoScenarioConfig.kt`. Preserve any useful values as test fixtures under test sources if they're still referenced by tests. Enforces R32 ("no simulation data should exist only in code constants").

**Requirements:** R32.

**Dependencies:** Unit 7 (the game must already be loading from bundled designs before the constants can be deleted).

**Files:**
- Delete: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` (or reduce it to only constants that have surviving callers).
- Modify: any test file that still imports `DemoScenarioConfig` — either inline the test fixture or move a slimmed-down version to test sources.
- Create (if needed): `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/testfixtures/TestShipConfigs.kt` — for tests that genuinely need hand-crafted `ShipConfig` values without going through the converter.

**Approach:**
- `grep` for all references to `DemoScenarioConfig.playerShipConfig`, `enemyShipLightConfig`, `enemyShipMediumConfig`, `enemyShipHeavyConfig` and `DemoScenarioConfig` itself across the codebase. Production references must be zero after Unit 7. Test references move to the test-fixtures location.
- If a grep finds production references outside `GameStateManager.startDemoScene`, surface them as a scope expansion and handle them before deleting.
- After cleanup, delete the file.

**Patterns to follow:**
- Test-fixtures-in-test-sources pattern — fixtures under `commonTest` never get bundled into production.

**Test scenarios:**
- Regression: full test suite (`./gradlew allTests`) passes after `DemoScenarioConfig` is deleted.
- Regression: full build (`./gradlew build`) passes.
- Verification: `grep -r "DemoScenarioConfig" src/ features/ components/ composeApp/` returns no results in production sources (test sources may retain the name as a fixture file if useful, but the runtime path has no references).

**Verification:**
- The file is deleted (or reduced to only test-fixture code under test sources).
- All tests pass.
- The desktop app starts and combat runs, identical to Unit 7's state.

---

## System-Wide Impact

- **Interaction graph:** The conversion layer is a new pure-function seam. It's called once per ship at scenario startup. No hot-path changes; no per-frame impact. Polygon rendering (Unit 3) runs per frame but matches the existing bullet rendering pattern's allocation profile.
- **Error propagation:** `ShipDesignConverter.convert` returns a `Result<ShipConfig>`. `DefaultShipDesignLoader` returns a plain `List<ShipDesign>`; parse failures log and surface at startup. `GameStateManager.startDemoScene` is the only consumer of both; it decides whether to crash, log, or fall back on failure. Document the choice in Unit 7.
- **State lifecycle risks:** None introduced — the conversion is pure and runs once per scenario spawn. No mid-game reloading, no caching, no state to invalidate. The existing `ShipSystems`, destruction hooks, and actor lifecycle are unchanged.
- **API surface parity:** `ShipConfig.hulls` changing from `hull: HullDefinition` is a breaking change to the runtime data model. Unit 1 updates every consumer in one pass. No external consumers (the runtime model lives entirely in the repo). `ShipDesign.formatVersion` does **not** change — the builder's JSON format is unchanged.
- **Integration coverage:** Unit 4's converter tests exhaustively cover the conversion, Unit 7's integration test proves the end-to-end loader→converter→createShip path works in a real combat scene. Unit 8's regression pass proves nothing in production references the old hard-coded constants.
- **Unchanged invariants:** Frictionless physics is preserved exactly. `ShipNavigator.computeBrakingStrategy`'s `v²/2a` formula is untouched. The combat damage model (enum-based `ShipSystems`, `ArcDamageRouter`) is unchanged. Turret instantiation (`Turret` child actor with offset/pivot) is unchanged — only its *source* of config data moves from `DemoScenarioConfig` to the converter. The atmospheric brainstorm's commitments are not prejudged here — this plan is physics-agnostic.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Multi-hull collision semantics are undecided in current `ShipPhysics` | Unit 1 explicitly picks a simple iterate-per-hull approach and tests it. If performance or correctness breaks, follow-up work can introduce a simplified collision polygon. |
| Polygon rendering regresses visual clarity vs sprites | Unit 3 validates visually against the builder's canvas preview. If polygons are unreadable, fall back on team-colour fills + outlines; more advanced rendering is a future pass. |
| `DefaultShipDesignLoader` cannot read commonMain resources via Compose Resources API on all platforms (Android + Desktop) | Implementation decision in Unit 6 picks whichever works on both; test on both before landing. The existing `loadFile` platform abstraction is a fallback if Compose Resources has gaps. |
| `ShipStatsCalculator` currently lives in `features/ship-builder/impl`, not accessible to `components/game-core/api` where the converter lives | If the converter can't reach `ShipStatsCalculator` without violating module boundaries, Unit 4 includes lifting the calculator into `components/game-core/api`. Alternatively, duplicate the thrust-sum logic in the converter (small, stable). Decide during implementation; either path is valid. |
| Default designs authored in the builder don't produce numerically identical ships to `DemoScenarioConfig` | Unit 5's test scenarios assert ±5% tolerance. If the dev can't hit that tolerance through the builder (e.g., polygon fidelity differences), adjust the tolerance or relax the test. Behaviour parity is the actual goal, not numerical identity. |
| Hidden references to `DemoScenarioConfig` in places the grep misses | Unit 8's verification runs a full grep and a full test suite. Any hit gets addressed before deletion. |
| `TurretGunRegistry` doesn't cover every turret id the builder can emit | Unit 2 enumerates every turret id currently used by `DemoScenarioConfig` and the builder's parts catalog. Unknown ids fail loudly at conversion time, not silently. |

## Phased Delivery

Phase 3 of the ship builder is itself a single phase of work, but the implementation units naturally split into three sub-phases for review and landing:

**Sub-phase 3a — Data model (Units 1, 2, 3):** Extend `ShipConfig` to multi-hull, add the turret registry, replace sprite rendering with polygons. After 3a, the game still runs off `DemoScenarioConfig` but the data shape is ready for the converter. Each unit is independently testable and lands as its own commit.

**Sub-phase 3b — Conversion and content (Units 4, 5, 6):** Add the converter, author the bundled default designs, and implement the loader. After 3b, the converter is proven but nothing in the game has switched to it yet. This gives a natural validation gate — you can run the converter tests in isolation and confirm everything works before touching startup.

**Sub-phase 3c — Integration and cleanup (Units 7, 8):** Wire the loader+converter into `startDemoScene` and delete `DemoScenarioConfig` as runtime data. The "switchover moment". After 3c, Phase 3 is complete.

Each sub-phase ends with a working desktop app that can run combat. No mid-phase broken states.

## Documentation / Operational Notes

- **Update `docs/brainstorms/2026-04-06-ship-builder-requirements.md`** after Phase 3 ships: mark R31 and R32 as satisfied, strike through the "Game Integration (Phase 3)" section in Phased Delivery, or add a completion note.
- **Update `docs/ship.md`** to reflect the multi-hull data model change if it currently describes a single hull.
- **No operational monitoring needed** — this is a data-model refactor, not a runtime behaviour change. The success signal is "desktop app launches and combat is playable" and "all tests pass."
- **Post-Deploy Monitoring & Validation:** No additional operational monitoring required. This is a pre-release prototype change with no production impact.

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-06-ship-builder-requirements.md](../brainstorms/2026-04-06-ship-builder-requirements.md) — Phase 3 requirements R31, R32.
- **Downstream dependency:** [docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md](../brainstorms/2026-04-10-atmospheric-movement-requirements.md) — atmospheric movement brainstorm, which names this plan as its prerequisite.
- **Prior phase plans:** [docs/plans/2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md](2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md), [docs/plans/2026-04-06-002-feat-ship-builder-phase2-item-creation-plan.md](2026-04-06-002-feat-ship-builder-phase2-item-creation-plan.md).
- **Key runtime files:** `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt`.
- **Key builder files:** `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt`, `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt`, `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt`, `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt`.
- **Vector rendering precedent:** commits `e67d2ea` (bullet vector rendering refactor) and `da0a983` (bullet sprite removal) — the pattern Unit 3's polygon ship rendering follows.
