---
title: "feat: Ship Builder Phase 3 — game integration (bridge builder designs to runtime)"
type: feat
status: active
date: 2026-04-11
updated: 2026-04-12
origin: docs/brainstorms/2026-04-06-ship-builder-requirements.md
---

# feat: Ship Builder Phase 3 — Game Integration

## Overview

Replace the hard-coded `DemoScenarioConfig` ships with builder-authored ship designs loaded at runtime. The plan has two conversion layers:

1. **A one-off migration script** (temporary, deleted after use) that reads `DemoScenarioConfig` and mechanically writes bundled `ShipDesign` JSON files plus a `turret_guns.json` file. This replaces hand-authoring default ships; values are preserved exactly.
2. **A permanent runtime converter** (`ShipDesign → ShipConfig`) that loads bundled `ShipDesign` JSON at startup and produces the `ShipConfig` consumed by `GameStateManager.createShip`.

Alongside the bridge, the plan extends `ShipConfig.hull` from a single `HullDefinition` to `hulls: List<HullDefinition>` and introduces a **per-hull child-actor collision pattern** where each hull piece becomes a child `Collidable` actor with its own `PolygonCollisionMask`, routing damage back to the parent `Ship`. This avoids Kubriko's sealed `ComplexCollisionMask` interface (which cannot be extended from outside the library). It also replaces all sprite-based rendering (ships, modules, turrets) with polygon/vector rendering in line with the recent bullet vector-rendering direction, and extends the runtime model to carry placed-module geometry through the converter so modules are renderable.

This plan ships against the **current frictionless physics** — atmospheric is a separate plan that follows this one. It is a prerequisite for `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`.

## Problem Frame

The ship builder produces `ShipDesign` JSON files that nothing in the game simulation knows how to load. `GameStateManager.startDemoScene` instantiates ships from hard-coded `ShipConfig` constants in `DemoScenarioConfig.kt`. Two separate data models coexist:

- The **builder model** (`ShipDesign`, `ItemDefinition`, `PlacedHullPiece/Module/Turret`) is rich, convex-polygon-based, multi-hull-aware, and loose-typed (e.g., `systemType: String`).
- The **runtime model** (`ShipConfig`, `HullDefinition`, `InternalSystemSpec`, `TurretConfig`, `GunData`) is flat, single-hull, enum-based, and fully-specified.

Until these are bridged: the ship builder is a dead-end tool, the atmospheric brainstorm cannot be validated, and R31/R32 from the origin doc are unmet.

Gap summary (full field mapping in Context & Research):

| Area | Builder | Runtime | Resolution |
|---|---|---|---|
| Hulls | `List<PlacedHullPiece>` (multi-hull) | `HullDefinition` (single) | Extend runtime to `List<HullDefinition>` + new `MultiPolygonCollisionMask` (Unit 1) |
| Module type | `systemType: String` | `InternalSystemType` enum | Converter validates and maps; rejects unknowns (Unit 4) |
| Module count per type | Unrestricted | `Map<InternalSystemType, _>` (one per type) | **Reject multi-module-per-type in v1** with a clear error (Unit 4) |
| Turret gun specs | `turretConfigId: String` only | Full `GunData` record | Bundled `turret_guns.json` + `TurretGunLoader` (Units 5, 6) |
| Ship/module/turret sprites | Not stored | `drawable: DrawableResource` | **Polygon/vector rendering — remove drawable fields entirely** (Unit 2) |
| Combat stats | Not stored | `CombatStats.evasionModifier` | Default value at conversion time; future builder field |
| Startup ship source | Saved files only | Hard-coded constants | Bundled JSON (produced by one-off migration script) + loader (Units 3, 5, 6) |

### Corrections from initial research

An initial feasibility review flagged "`Ship` uses `CircleCollisionMask`, so multi-hull collision is a phantom problem." That was wrong. **Bullet collision tests against the ship's `PolygonCollisionMask`**, so ships today are polygon-collidable — single-polygon. Multi-hull collision is a genuine gap, and Kubriko's `ComplexCollisionMask` interface is the supported extension point for an actor carrying multiple collision surfaces.

## Requirements Trace

From `docs/brainstorms/2026-04-06-ship-builder-requirements.md`:

- **R31.** Ship designs saved by the builder can be loaded into the game simulation, replacing the role of `DemoScenarioConfig` hardcoded ship definitions.
- **R32.** Everything currently used to create ships in `GameStateManager.createShip()` must be suppliable from a ship design file. No simulation data should exist only in code constants.

Phase 3 satisfies both: after Unit 8, no simulation data — ship geometry, mass, thrust, subsystems, turrets, gun data — exists only in Kotlin code. Default ships live as bundled JSON resources; turret gun data lives in `turret_guns.json`; the runtime converter reads both.

Downstream dependency note: `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md` Slice A depends on Phase 3 being at least partially in place so the atmospheric physics model has real ships to load. Phase 3 ships against current frictionless physics; the converter's `MovementConfig` output will need reinterpretation (and possibly partial rewrite) when atmospheric lands. This is an acknowledged coupling, not a free lunch.

## Scope Boundaries

- **No physics changes.** `ShipPhysics`, `ShipNavigator.computeBrakingStrategy`, and the frictionless model are untouched. Atmospheric is a separate plan.
- **Multi-module-per-type designs are rejected in v1.** A design with two `REACTOR` modules is a hard conversion failure, not an aggregation opportunity. This removes the silent resilience drift that aggregation would introduce (a 3-reactor ship would otherwise become ~3× more resilient because `disableThreshold = maxHp * 2/3` is computed from the aggregate, not per-source). Multi-instance subsystems become a future feature.
- **No builder UI for gun specs.** Turret gun data lives in `turret_guns.json` (produced by the migration script, read by the runtime). The builder does not gain damage/reload/projectile fields.
- **No builder UI for combat stats.** `CombatStats.evasionModifier` is defaulted at conversion time.
- **No Keel support.** Keel is a Slice B atmospheric concept; this plan retains the current hull/module/turret model.
- **No damage-model changes.** Existing `InternalSystemType` enum stays at `REACTOR`, `MAIN_ENGINE`, `BRIDGE`. No new subsystem values.
- **No save-format migration.** `ShipDesign.formatVersion` stays at 2. Existing saved designs continue to load.
- **No player-authored designs loaded at startup.** Startup loads only the bundled defaults. Loading user-saved designs into scenarios is a natural follow-up but out of scope here.
- **Multi-hull rendering is merged silhouette + faint sub-hull outlines.** Separate-per-hull rendering is rejected; per-hull alpha blending isn't considered.
- **Forward-looking success criteria are omitted.** Plan-level success is regression-only ("existing behaviour preserved"). "The dev can author a novel ship and it loads" is trusted to be exercised by normal use rather than codified as a test.
- **No back-out gate for the polygon rendering change.** Sprite assets may be deleted as part of Unit 2. If visual regression is discovered later, recovery is via git revert. Accepted risk.

## Context & Research

### Relevant Code and Patterns

**Builder side**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` — serialized design format, `formatVersion: Int = 2`.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt` — sealed `ItemAttributes` with per-module thrust values (`forwardThrust`, `lateralThrust`, `reverseThrust`, `angularThrust`), `systemType: String`.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt` — canonical source for summed ship stats. A pure computational core is extracted to game-core/api in Unit 0 so the runtime converter can call it without depending on the builder feature module.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt` — existing save/load via platform `saveFile`/`loadFile` abstractions.

**Runtime side**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt` — target type for conversion. `hull: HullDefinition` becomes `hulls: List<HullDefinition>` (Unit 1).
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/InternalSystemSpec.kt` — contains `enum class InternalSystemType { REACTOR, MAIN_ENGINE, BRIDGE }`. No changes to the enum for this plan.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` (around lines 51–155) — hosts `startDemoScene()` and `createShip(config, …)`. Integration point for Unit 7.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — the hard-coded `ShipConfig` instances migrated by Unit 5 and deleted by Unit 8.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt` — bridge extractor `ShipSpec.fromConfig`. Updated to read `hulls: List<HullDefinition>` in Unit 1.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — consumer of `ShipSpec`. Currently implements `Collidable` with a single `PolygonCollisionMask` built from `spec.hull.vertices`. Rendering is currently sprite-based. Unit 1 switches to `MultiPolygonCollisionMask`; Unit 2 replaces sprite rendering with polygon rendering.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt` — implements vector rendering via `drawCircle` (commits `e67d2ea`, `da0a983`). Serves as the conceptual precedent for moving away from sprites, but not a copy-paste pattern — polygons need `Path` or `drawLine` sequences rather than a primitive shape.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt` — unchanged by Unit 1. `ShipNavigator.computeBrakingStrategy` touches only `MovementConfig` and `hullRadius`, not `ShipConfig.hull` directly, so the multi-hull refactor doesn't propagate into navigation.

**Kubriko collision constraints**
- `com.pandulapeter.kubriko.collision.mask.ComplexCollisionMask` is a **sealed interface** — cannot be implemented from outside Kubriko's module. `CollisionMask` is also sealed. `PolygonCollisionMask` has an internal constructor and runs `generateConvexHull()` on input vertices. `CollisionMaskExtensions.collisionResultWith` hardcodes four Circle/Polygon combinations — a custom mask type would silently never collide.
- **Consequence for multi-hull:** The only viable approach using public Kubriko APIs is per-hull child `Collidable` actors. Each hull piece becomes a child actor with its own `PolygonCollisionMask`. The parent `Ship` coordinates their position/rotation to track its own body pose. Bullet collision hits a child hull actor; damage routes back to the parent `Ship` via a delegate/callback. This preserves per-hull collision fidelity and per-hull armour resolution.

**Shared infrastructure**
- `components/game-core/api` is accessible to both `features/game/impl` and `features/ship-builder/impl`. The extracted stats-calculation core, the runtime converter, and `MultiPolygonCollisionMask` all live there.
- `components/game-core/api` currently has **no Compose Resources plugin configured** — Unit 3 adds it so bundled JSON files can live there.
- kotlinx.serialization polymorphic sealed-class support (already in use for `ItemAttributes`).
- kotlin-inject DI with `@Provides` in `composeApp/src/commonMain/kotlin/.../di/AppComponent.kt`.

### Institutional Learnings

`docs/solutions/` does not exist. No prior incident knowledge on this specific bridge. Forward-looking design docs worth consulting:
- `docs/ai_architecture.md` — module dependency rules; the api/impl boundary constraint is preserved by putting the converter and extracted stats core in `components/game-core/api`.
- `docs/ai_kubriko_constraints.md` — hot-path guidance; polygon rendering should avoid per-frame `SceneOffset` churn where feasible, though rotation-transformed vertices fundamentally require per-frame recomputation.
- `docs/ship.md` — canonical ship model description. Needs updating after Unit 1 to reflect the multi-hull data shape.

### External References

None. Pure internal refactor; no new frameworks.

## Key Technical Decisions

1. **Multi-hull via per-hull child `Collidable` actors.** Kubriko's `ComplexCollisionMask` is a sealed interface (cannot be implemented externally) and `CollisionMaskExtensions.collisionResultWith` hardcodes Circle/Polygon combinations. Instead, each `PlacedHullPiece` becomes a child actor with its own `PolygonCollisionMask`, attached to the parent `Ship`. Bullet collision hits the child; damage routes back to the parent via a child→parent delegate. This preserves multi-hull fidelity using only public Kubriko APIs. Each child hull actor tracks its own `HullDefinition.armour` for the `KineticImpactResolver`, resolving the "which hull's armour?" question the earlier design left open.
2. **Reject multi-module-per-type designs in v1.** Clear error at conversion time. Eliminates the silent gameplay drift that aggregation would introduce via `disableThreshold = maxHp * 2/3` on aggregated maxHp.
3. **Polygon/vector rendering across all actor types.** Ships, modules, and turrets all render as polygon outlines. Turrets specifically render as a simple triangle vector (replacing `turret_simple_1.png`). All sprite assets for ships and turrets are removed.
4. **Facing conveyed by a nose marker.** Each ship draws a small triangle or tick at the forward-most vertex, oriented along the ship's forward axis. Addresses directional ambiguity introduced by losing the sprite's implicit nose/tail graphic.
5. **Multi-hull composition renders as a merged silhouette.** The ship draws a single bold outline around the polygon union of all hull pieces, filled with the team colour, then draws faint per-hull outlines on top to preserve composition detail. The polygon union computation is non-trivial; see Risks.
6. **Two converters, not one.**
   - A **one-off migration script** (temporary, in `commonTest` or a throwaway source set) reads `DemoScenarioConfig` directly and mechanically writes bundled `ShipDesign` JSON + `turret_guns.json`. Deleted from source control after migration runs.
   - A **permanent runtime converter** (`ShipDesign → ShipConfig`) lives in `components/game-core/api`, runs at every game startup, and uses the extracted stats-calculator core from Unit 0.
7. **Stats calculation shared via extraction.** Unit 0 extracts a pure computational core from `ShipStatsCalculator` into `components/game-core/api`. Both the builder's stats panel and the runtime converter call it. Single source of truth; no duplication risk.
8. **Turret gun data lives in a bundled `turret_guns.json` resource.** Not a `TurretGunRegistry` code-constant map. Loader reads the file at startup, exposes a `Map<String, GunData>` lookup. Satisfies R32 cleanly.
9. **Compose Resources visibility in `components/game-core/api`.** The module already has compose resources configured but the generated `Res` class is `internal`. Unit 3 adds `compose.resources { publicResClass = true }` so bundled JSON files are readable from outside the module. This is a build.gradle.kts tweak, not a plugin addition.
14. **Runtime model extended with placed-module geometry.** `InternalSystemSpec` currently has no vertices or position. Phase 3 either extends `ShipConfig` with a parallel `List<PlacedModuleSpec>` carrying vertices/offset/rotation, or adds geometry fields to `InternalSystemSpec` directly. The converter populates these from the builder's `PlacedModule` + `ItemDefinition`. This enables module polygon rendering at runtime.
15. **`GunData` must be made `@Serializable` and lose its `drawable` field.** `GunData` currently holds a `DrawableResource` (not serializable) and `AngleRadians` (needs a custom serializer). Unit 2 removes the `drawable` field from the type definition itself (not just from call sites in `DemoScenarioConfig`) and adds `@Serializable` to `GunData` and `ProjectileStats`. This is prerequisite for `turret_guns.json` in Unit 5.
16. **Ship and Turret `body.size` derived from polygon AABB, not sprite dimensions.** Current code does `body.size = SceneSize(sprite.width.sceneUnit, sprite.height.sceneUnit)`. After sprites are removed, `body.size` is computed from the hull polygon's axis-aligned bounding box (Ship) or the turret triangle's bounds (Turret), and `body.pivot` is the AABB centre. Turret muzzle offset is re-derived accordingly.
17. **`CombatStats.evasionModifier` uses a uniform default for Phase 3.** Per-ship evasion modifiers (0.1/0.2/0.1/0.0 in `DemoScenarioConfig`) are deferred to Keel-class metadata in the atmospheric Slice B. The deep-equal migration test (Unit 5) excludes `evasionModifier` from the assertion. Accept that combat evasion behaviour changes slightly from `DemoScenarioConfig` baseline.
18. **Designs loaded eagerly at `AppComponent` init, cached in singleton fields.** `DefaultShipDesignLoader` and `TurretGunLoader` run once during DI construction (where `suspend` is available). `startDemoScene` reads from the cache synchronously. No suspend propagation through `restartScene` or `GameVM`. Fatal-startup-error semantics apply at AppComponent init, not per-scene.
10. **Converter failure is a fatal startup error.** If any bundled default ship design fails to load or convert, or if `turret_guns.json` is missing, the game throws a clear error at startup. These are shipped resources; failure is a dev bug, not a runtime condition. This unblocks Unit 8 cleanly.
11. **Filename-indexed spawn slot mapping.** `startDemoScene` indexes loaded designs by filename (`player_ship`, `enemy_light`, `enemy_medium`, `enemy_heavy`) rather than by name-prefix matching. A missing or renamed file fails loudly at startup.
12. **Sub-phase 3c/3d split.** Unit 7 (switchover) and Unit 8 (deletion of `DemoScenarioConfig`) land as separate sub-phases with a playtest gate between them. `DemoScenarioConfig` lingers harmlessly as a rollback reference until the dev confirms the migrated ships feel right.
13. **Frictionless physics preserved.** The runtime converter produces `MovementConfig` values that match what the current `DemoScenarioConfig` declares. Atmospheric will reinterpret these later (and likely needs a sibling per-profile movement config — see Risks).

## Open Questions

### Resolved During Planning

- **Hull representation at runtime?** → Extend to `List<HullDefinition>` with `MultiPolygonCollisionMask`.
- **Ship visual rendering?** → Polygon vector rendering for ships, modules, and turrets. Nose marker for facing. Merged silhouette + faint sub-hulls for multi-hull.
- **Default ship designs source?** → One-off migration script from `DemoScenarioConfig`; output committed as bundled JSON.
- **Turret gun data?** → Bundled `turret_guns.json`, produced by the same migration script.
- **Compose Resources in game-core/api?** → Enabled as part of Unit 3.
- **ShipStatsCalculator location?** → Pure core extracted to game-core/api; builder delegates to it.
- **Multiple modules of the same type?** → Rejected at conversion time. Multi-instance subsystems become a future feature.
- **Converter failure mode?** → Fatal startup error.
- **Spawn slot mapping?** → Filename-indexed.
- **Unit 7/8 bundling?** → Split into 3c and 3d with a playtest gate.
- **Sequencing with atmospheric?** → Phase 3 ships first; atmospheric follows and may require converter adjustments.
- **Forward-looking success criteria?** → Omitted; regression-only.

### Deferred to Implementation

- **Exact team colour values.** Probably player = cyan-family (matches builder hull palette), enemy = red-family (matches builder turret palette). Decide during implementation; document the chosen hex values in the PR.
- **Line weights, stroke vs fill rules, transparency for polygon rendering.** Tune during visual inspection.
- **Turret vector shape.** Simple isoceles triangle pointing along barrel direction is the intended default; refine if ugly.
- **Damage/destroyed visual states under polygon rendering.** Sprites may have had destruction feedback; polygons inherit none automatically. Defer to implementation — can start with "ship polygon removed at destruction" and improve later.
- **Polygon union algorithm for merged silhouette.** Several options exist (convex hull of union, sweep-line union algorithm, library dependency). Decide based on hull-piece count in the default designs — if all hulls are convex and non-overlapping, a cheap approach works. See Risks.
- **Exact `TurretConfig` offset/pivot derivation from `PlacedTurret`.** Builder stores position and rotation; runtime wants offsetX/Y and pivotX/Y. Centroid-based pivot is the intended default.
- **Migration script output location and execution harness.** A `commonTest` utility is one option; a standalone main function in a throwaway source folder is another. Pick whichever is simpler to run once and delete.
- **Per-frame allocation strategy for polygon rendering.** Rotating a polygon each frame requires per-frame transformed vertices. Zero-allocation may be unachievable without raw-float buffers. Accept bounded allocations (N ships × M vertices per frame ≈ 30–50 allocations at current scale) and spot-check in a follow-up if it matters.
- **`MultiPolygonCollisionMask` AABB computation.** Straightforward union of sub-mask AABBs, but the exact refresh cadence under transforms (recompute per frame? cache until transform changes?) is implementation detail.
- **Whether the migration script preserves or regenerates `ItemDefinition.id`s.** Deterministic ids help round-trip tests; random ids are simpler. Pick the former.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
One-off (runs during plan rollout, deleted after)
─────────────────────────────────────────────────
DemoScenarioConfig.kt ──► MigrationScript ──► writes to:
                                                 ├─ composeResources/files/default_ships/player_ship.json
                                                 ├─ composeResources/files/default_ships/enemy_light.json
                                                 ├─ composeResources/files/default_ships/enemy_medium.json
                                                 ├─ composeResources/files/default_ships/enemy_heavy.json
                                                 └─ composeResources/files/turret_guns.json
                          (migration script is then deleted from source control)


Permanent (runs at every game startup)
──────────────────────────────────────
bundled JSON ──load──► DefaultShipDesignLoader ─┐
bundled JSON ──load──► TurretGunLoader ─────────┤
                                                │
                                                ▼
                                      GameStateManager.startDemoScene
                                                │
                                                │ for each slot in fixed key order:
                                                ▼
                                      ShipDesignConverter.convert(design, turretGuns)
                                                │   │
                                                │   └─► extractedStatsCore (also used by builder UI)
                                                ▼
                                      Result<ShipConfig>  (fatal-error on failure)
                                                │
                                                ▼
                                      createShip(config, slotPosition, team, ...)
                                                │
                                                ▼
                                      Ship actor
                                      ├─ MultiPolygonCollisionMask (one sub-mask per hull)
                                      └─ polygon/vector renderer
                                         ├─ merged silhouette (union outline + team fill)
                                         ├─ faint per-hull outlines on top
                                         ├─ nose marker at forward vertex
                                         └─ modules and turrets also polygon/vector
```

Key design notes:
- The runtime converter is the single seam. It's pure, cheap to test exhaustively, and its only external inputs are the loaded `ShipDesign` and the turret gun lookup.
- The extracted stats core is the single source of truth for "how do you sum thrust across modules?". Both the builder's live stats panel and the runtime converter call it.
- `MultiPolygonCollisionMask` is a thin adaptor: it holds a list of `PolygonCollisionMask`s, delegates `isSceneOffsetInside` to each, and computes a union AABB for broadphase. Kubriko's `ComplexCollisionMask` interface is the supported extension point.
- Polygon rendering produces a merged silhouette per ship via a polygon-union computation, with faint individual hull outlines drawn on top. The polygon union is the one non-trivial geometric computation introduced by this plan.

## Implementation Units

### Sub-phase 3a — Foundation

- [ ] **Unit 0: Extract `ShipStatsCalculator` pure core to game-core/api**

  **Goal:** Pull the thrust/mass aggregation logic out of `features/ship-builder/impl` into `components/game-core/api` as a pure function that takes `ItemDefinition` + placed-item inputs and returns stats. The existing `ShipStatsCalculator` in the builder becomes a thin adapter that feeds `ShipBuilderState` into the pure core. The runtime converter (Unit 4) also calls the pure core.

  **Requirements:** R32 (single source of truth for simulation data aggregation).

  **Dependencies:** None. Foundational.

  **Files:**
  - Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt`
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt` — delegate to the core
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCoreTest.kt`
  - Regression: existing `ShipStatsCalculatorTest` in the builder continues to pass after delegation

  **Approach:**
  - The pure core takes typed inputs: `List<PlacedHullPiece>`, `List<PlacedModule>`, `List<PlacedTurret>`, and a way to resolve `ItemDefinition`s by id. It returns a `ShipStatsSummary` record containing total mass, per-axis thrust totals, and any other summed quantities the builder currently exposes.
  - The builder-side `ShipStatsCalculator` becomes a thin wrapper that unpacks `ShipBuilderState` and delegates.
  - The legacy hardcoded fallback values (when a placed module lacks an `ItemDefinition`) move into the pure core behind a documented legacy path.

  **Patterns to follow:**
  - Current `ShipStatsCalculator.calculateStats` signature and return shape.
  - Existing pure-helper style in `components/game-core/api`.

  **Test scenarios:**
  - Happy path: single hull + one engine module + one reactor + one turret → stats match the current `ShipStatsCalculator` output for the same design.
  - Happy path: multiple modules of different types → thrust sums correctly per axis.
  - Edge case: empty modules list → zero thrust, mass from hulls only.
  - Edge case: legacy placed module without an `ItemDefinition` → legacy fallback values are used, matching current behaviour.
  - Regression: all existing `ShipStatsCalculatorTest` scenarios pass against the delegating wrapper.

  **Verification:**
  - Full test suite passes.
  - The builder's live stats panel displays identical values before and after extraction (manual spot-check).

---

- [ ] **Unit 1: `MultiPolygonCollisionMask` + extend `ShipConfig` to multi-hull**

  **Goal:** Introduce a Kubriko `ComplexCollisionMask` implementation that wraps multiple `PolygonCollisionMask`s. Change `ShipConfig.hull: HullDefinition` to `hulls: List<HullDefinition>`. Update `Ship` actor to construct a `MultiPolygonCollisionMask` from the list. Update all runtime consumers to iterate.

  **Requirements:** R31 (multi-hull designs must survive the conversion), R32 (runtime model matches builder model).

  **Dependencies:** None (independent of Unit 0).

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/HullCollider.kt` — per-hull child `Collidable` actor
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt` — `hull: HullDefinition` → `hulls: List<HullDefinition>`, update `totalMass` computed property to sum over `hulls`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt` — carry `hulls: List<HullDefinition>`, update `fromConfig`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — remove the single `PolygonCollisionMask`; spawn `HullCollider` children from `spec.hulls`; update `hullRadius` computation for multi-hull. Update any code that accesses `spec.hull`.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt` — adapt bullet collision resolution to hit `HullCollider` (not `Ship` directly). Pass `hullCollider.armour` to `KineticImpactResolver`. Route damage to `hullCollider.parentShip`.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — `createShip` spawns `HullCollider` children alongside the parent `Ship` in `actorManager`. Destruction callback removes children.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — wrap each existing `hull = HullDefinition(...)` in `hulls = listOf(HullDefinition(...))`. Temporary edit; deleted in Unit 8.
  - Modify: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ShipConfigTest.kt` — update assertions to iterate over `hulls`.
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/HullColliderTest.kt`

  **Approach:**
  - **Per-hull child `Collidable` actors.** Each `HullDefinition` in `spec.hulls` spawns a child actor (`HullCollider` or similar) with its own `PolygonCollisionMask` built from that hull's vertices. Child actors follow the parent `Ship`'s position and rotation (updated in `Ship.update` or via Kubriko's actor-parent mechanism). Each `HullCollider` carries a reference to its parent `Ship` and its own `HullDefinition` (including `armour`). When a `Bullet` hits a `HullCollider`, damage routes back to the parent `Ship` via a delegate/callback, using **that specific hull's `armour`** for the `KineticImpactResolver`. This resolves the per-hull armour question naturally.
  - The parent `Ship` actor may no longer implement `Collidable` itself (or implements it with a degenerate/no-op mask for broadphase purposes). Bullets only collide with `HullCollider` children. This is a structural change to how `Ship` participates in the Kubriko actor/collision system.
  - `ShipConfig.totalMass` sums over `hulls` instead of reading the single `hull`. Existing test assertions that check "positive mass" still pass.
  - `ShipNavigator` receives `hullRadius` — for multi-hull, compute this from the union of all hull-piece vertex distances (or max across hulls). For single-hull ships (all current defaults), the formula collapses to the existing computation on `hulls[0]`.
  - Actor lifecycle: `HullCollider` children are created alongside the parent `Ship` in `createShip`, added to `actorManager`, and removed when the parent `Ship` is destroyed.

  **Patterns to follow:**
  - Kubriko's `ComplexCollisionMask` interface as documented or implemented by any existing plugin. If Kubriko has a built-in `ComplexCollisionMask` example, mirror its conventions.
  - Existing `Ship.kt` collision setup for how the mask is wired into the actor.

  **Test scenarios:**
  - Happy path: a single-hull `Ship` spawns one `HullCollider` child. `HullCollider` has a `PolygonCollisionMask` with the hull's vertices.
  - Happy path: a two-hull `Ship` (hand-fabricated test fixture) spawns two `HullCollider` children at the correct offset positions.
  - Happy path: when a `Bullet` hits a `HullCollider`, `KineticImpactResolver` receives **that hull's `armour`** (not a generic ship-level armour).
  - Happy path: damage from a `HullCollider` collision routes to the parent `Ship` and reduces the ship's HP.
  - Regression: a single-hull ship still takes bullet damage exactly as before after the refactor. Combat is playable with current `DemoScenarioConfig` ships.
  - Integration: `HullCollider` children follow the parent `Ship`'s position and rotation each frame. After 10 frames of ship movement, child positions still match the parent's pose.
  - Edge case: when a `Ship` is destroyed, all its `HullCollider` children are removed from the actor manager.

  **Verification:**
  - All existing tests pass.
  - Desktop app runs; combat looks identical to pre-Unit-1.
  - A manual test with a hand-fabricated two-hull ship confirms bullets collide with either hull and damage routes to the parent correctly.

---

- [ ] **Unit 2: Polygon/vector rendering for ships, modules, and turrets**

  **Goal:** Replace all sprite-based rendering with polygon/vector drawing. Ships render as a merged silhouette (team-coloured fill, bold outline) with faint per-hull outlines on top and a nose marker at the forward-most vertex. Modules render as polygon outlines. Turrets render as simple triangles pointing along their barrel direction. Remove `drawable: DrawableResource` from `ShipConfig` and all associated sprite plumbing.

  **Requirements:** R32 (eliminate remaining code-constant sprite data from `DemoScenarioConfig`).

  **Dependencies:** Unit 1 (needs `hulls: List<HullDefinition>` to iterate).

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ShipConfig.kt` — remove `drawable` field.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — replace sprite rendering with polygon rendering. Remove `SpriteManager.get(drawable)` call. Remove `SpriteManager` parameter if no other actor uses it.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — `createShip` signature drops drawable param.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` — remove `drawable = Res.drawable.ship_player_1 / ship_enemy_1` lines and `GunData.drawable = Res.drawable.turret_simple_1` references.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Turret.kt` (or wherever turret rendering lives) — replace sprite with a triangle pointing along the barrel direction.
  - Modify: module rendering (in `Ship.kt` or a related actor) — polygon outlines for placed modules if they're currently sprite-drawn.
  - Possibly remove: `components/design/src/commonMain/composeResources/drawable/ship_player_1.xml`, `ship_enemy_1.xml`, `turret_simple_1.xml` (only if no other caller uses them — `grep` first).
  - Create (new helper): `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/rendering/PolygonUnion.kt` for the merged-silhouette union computation.

  **Approach:**
  - **Per-ship rendering pipeline** (called from `Ship.draw` or equivalent):
    1. Compute the polygon union of all hulls in `spec.hulls` (in ship-local coordinates). This is the merged silhouette. Do this once at ship construction; transforms are applied at draw time.
    2. At draw time, apply the ship's pose (position, rotation, mirror) to the union outline vertices and draw it as a bold stroked closed path filled with the team colour (translucent fill, opaque stroke).
    3. Draw each individual hull outline on top with faint alpha so the composition is visible.
    4. Draw a nose marker: find the forward-most vertex of the merged silhouette (the vertex with the greatest dot-product against ship forward), draw a small triangle or tick at that point pointing forward.
  - **Modules:** draw each placed module's polygon outline at its transformed position. Colour per module type (can reuse the builder's Yellow for modules).
  - **Turrets:** draw an isoceles triangle with its apex at the barrel tip and base at the pivot. Colour per team.
  - **Team colours:** Defer exact hex values to implementation; recommended defaults are cyan-family for player and red-family for enemy to mirror the builder palette without clashing.
  - **Allocation note:** Per-frame rotation-transformed vertices cannot be fully precomputed. Accept bounded allocations at current combat scale (≤6 ships × ≤10 vertices each). If this becomes a hot-path issue later, introduce raw-float vertex buffers in a follow-up.
  - **Polygon union** is the one non-trivial algorithm. See Risks — a cheap approach may be acceptable if default hulls are non-overlapping, but a general solution requires a proper union algorithm.

  **Patterns to follow:**
  - `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt` for the general "vector draw in `DrawScope`" pattern — but note bullets use `drawCircle` (a primitive), and polygons need `drawPath` with a constructed `Path` or repeated `drawLine` calls.
  - `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/canvas/ItemRenderer.kt` for the builder's polygon rendering style. Combat can reuse similar path construction but with different colours.

  **Test scenarios:**
  - Happy path (manual visual): desktop app runs, ships appear as polygon silhouettes with visible nose markers and faint sub-hulls, team colours distinguish player from enemy.
  - Happy path (compile-time): `ShipConfig` has no `drawable` field; the entire codebase compiles; no references to `Res.drawable.ship_player_1`, `ship_enemy_1`, `turret_simple_1` remain in runtime code (grep-verified).
  - Happy path (unit): `PolygonUnion.union(listOf(squareAt(0,0), squareAt(5,0)))` returns a rectangle enclosing both squares.
  - Happy path (unit): union of two non-overlapping convex polygons produces a correct bounding outline.
  - Edge case: union of a single polygon returns that polygon unchanged.
  - Regression: existing ships appear in correct positions and rotate correctly; combat behaviour (bullets hitting ships, explosions) is unchanged.

  **Verification:**
  - Desktop app renders ships, modules, and turrets as polygon/vector graphics.
  - Nose markers are visible on all ships at all rotations during combat.
  - Team distinction is legible during a normal combat scene (manual dev sign-off).
  - No sprite drawable references remain in runtime code.
  - Polygon union tests pass.

---

- [ ] **Unit 3: Make `Res` public in `components/game-core/api` for bundled JSON access**

  **Goal:** The game-core/api module already has Compose Resources configured (a generated `Res.kt` exists at `build/generated/.../Res.kt`) but the `Res` class is `internal`. Add `publicResClass = true` to the `compose.resources {}` block so bundled JSON files can be read from outside the module. Also verify no `Res`-import ambiguity is introduced for callers that already import `components/design`'s `Res`.

  **Requirements:** R31, R32 (enables bundled-resource hosting of ship and turret gun data).

  **Dependencies:** None.

  **Files:**
  - Modify: `components/game-core/api/build.gradle.kts` — add `publicResClass = true` (and `generateResClass = always` if not already set) to the existing `compose.resources {}` block.
  - Create: `components/game-core/api/src/commonMain/composeResources/files/.gitkeep` (placeholder directory).
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/resources/ResourceLoadingTest.kt` — smoke test for reading a bundled file via the now-public `Res`.

  **Approach:**
  - The module already has compose resources and a generated `Res.kt`; this unit just flips visibility. The generated package is `lastfleetprotocol.components.game_core.api.generated.resources.Res` — distinct from `lastfleetprotocol.components.design.generated.resources.Res`, so no ambiguity in callers that import both. Verify this claim during implementation.
  - `Res.readBytes(path: String)` is **suspending**. Callers must be in a coroutine context. This informs Unit 6's eager-loading-at-AppComponent-init design.
  - Commit a trivial text file as a smoke test resource; read it via `Res.readBytes` on both platforms; delete the smoke file after the test passes.

  **Patterns to follow:**
  - `components/design/build.gradle.kts` for the `compose.resources` block shape.

  **Test scenarios:**
  - Happy path: `Res.readBytes("files/smoke_test.txt")` on JVM returns non-empty bytes.
  - Happy path: same on Android.
  - Happy path: `features/game/impl` source can import both `design`'s `Res` and `game-core/api`'s `Res` without ambiguity (qualified imports).
  - Edge case: reading a non-existent path throws — document behaviour.

  **Verification:**
  - Both desktop and Android builds complete.
  - Smoke test passes on both platforms.
  - No `Res`-import ambiguity in any module that depends on game-core/api.

---

### Sub-phase 3b — Conversion and content

- [ ] **Unit 4: Runtime `ShipDesignConverter`**

  **Goal:** Pure function converting a `ShipDesign` to a `ShipConfig`. Runs at every game startup. Uses the extracted `ShipStatsCore` from Unit 0 and a `turretGuns: Map<String, GunData>` supplied by `TurretGunLoader` (Unit 6). Rejects multi-module-per-type designs and unknown `systemType` strings with clear errors.

  **Requirements:** R31, R32.

  **Dependencies:** Unit 0 (stats core), Unit 1 (`hulls: List<HullDefinition>`).

  **Files:**
  - Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt`
  - Create: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverterTest.kt`

  **Approach:**
  - Signature: `fun convert(design: ShipDesign, turretGuns: Map<String, GunData>): Result<ShipConfig>` returning either a success or a descriptive failure.
  - **Hull conversion** (`placedHulls` → `List<HullDefinition>`):
    - For each `PlacedHullPiece`, resolve its `itemDefinitionId` to the corresponding `ItemDefinition`.
    - Apply the placed transform (position, rotation, mirrorX, mirrorY) to the item's vertices, producing ship-space vertices.
    - Build one `HullDefinition` per placed hull piece with its transformed vertices, armour (from `HullAttributes.armour`), and mass. Multi-hull is preserved.
  - **Module conversion** (`placedModules` → `List<InternalSystemSpec>`):
    - Group by `systemType` string.
    - **If any group has more than one entry, return failure** with a clear error naming the duplicated type.
    - Map each group's systemType string to `InternalSystemType` enum. Unknown strings → failure.
    - Emit one `InternalSystemSpec` per resolved type with its mass, density, and `maxHp`.
  - **Turret conversion** (`placedTurrets` → `List<TurretConfig>`):
    - For each `PlacedTurret`, look up `turretConfigId` in `turretGuns`. Missing id → failure with a clear error.
    - Derive `offsetX/Y` from the placed position and `pivotX/Y` from the turret item definition's centroid.
    - Build a `TurretConfig` with the resolved `GunData`.
  - **MovementConfig** computed via `ShipStatsCore.computeMovementConfig(design)` — same formula the builder displays.
  - **CombatStats**: default `evasionModifier` matching the current `DemoScenarioConfig` value.

  **Patterns to follow:**
  - `ShipStatsCore` as the canonical movement-config source.
  - kotlinx.serialization polymorphic handling for resolving `ItemAttributes` subclasses.
  - Pure-function style: no I/O, no logging, failures surface via the `Result` return.

  **Test scenarios:**
  - Happy path: minimal single-hull design with one reactor, one engine, one turret → `ShipConfig` with `hulls.size == 1`, two `InternalSystemSpec` entries, one `TurretConfig`, positive `MovementConfig.forwardThrust`.
  - Happy path: multi-hull design (two placed hull pieces) → `hulls.size == 2`, vertices transformed correctly.
  - Happy path: transform correctness — a hull piece placed at `(10, 20)` with rotation 90° produces vertices that match the expected rotated+translated positions. Numeric assertion on at least one vertex.
  - Happy path: `MovementConfig.forwardThrust` equals `ShipStatsCore.computeMovementConfig(design).forwardThrust`. Identity assertion.
  - Error path: design with two REACTOR modules returns failure with "multiple modules of type REACTOR" in the error.
  - Error path: design with `systemType = "PLASMA_CORE"` returns failure with the unknown string in the error.
  - Error path: design with a placed hull piece referencing a non-existent `itemDefinitionId` returns failure.
  - Error path: design with a placed turret whose `turretConfigId` isn't in the turret guns map returns failure.
  - Error path: design with zero placed hull pieces returns failure.
  - Edge case: design with zero placed modules succeeds and produces an empty `internalSystems` list (runtime tolerates this).
  - Integration: take a migrated bundled default ship design (from Unit 5) and run it through the converter — the resulting `ShipConfig` is deep-equal to the corresponding original `DemoScenarioConfig` entry. This is the migration-fidelity assertion.

  **Verification:**
  - All converter tests pass.
  - Converter is pure (no I/O, no `println`, no DI access).
  - Unknown `systemType` and duplicate modules produce clear, debuggable errors.

---

- [ ] **Unit 5: One-off migration script (DemoScenarioConfig → bundled JSON)**

  **Goal:** A disposable script that reads `DemoScenarioConfig` and writes the four bundled `ShipDesign` JSON files plus `turret_guns.json` under `components/game-core/api/src/commonMain/composeResources/files/`. The script runs once as part of the plan's execution; its output is committed; the script itself is deleted from source control afterwards.

  **Requirements:** R31, R32.

  **Dependencies:** Unit 3 (Compose Resources target directory), Unit 4 (round-trip fidelity test).

  **Files:**
  - Create (temporary): a migration source file, either as a `commonTest` utility or a throwaway `migration/` source folder. Name something obvious like `DemoScenarioConfigMigration.kt`. This file is **deleted** after the migration runs and its output is committed.
  - Create (permanent): `components/game-core/api/src/commonMain/composeResources/files/default_ships/player_ship.json`
  - Create (permanent): `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_light.json`
  - Create (permanent): `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_medium.json`
  - Create (permanent): `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json`
  - Create (permanent): `components/game-core/api/src/commonMain/composeResources/files/turret_guns.json`
  - Create: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/BundledDefaultsTest.kt` — validates the committed JSON round-trips through the converter to match `DemoScenarioConfig`.

  **Approach:**
  - The migration script:
    1. For each `DemoScenarioConfig` `ShipConfig`, synthesize one `ItemDefinition` per hull, per module type, and per turret (with deterministic ids like `hull_player_1`, `module_reactor_player`, etc.).
    2. Construct `PlacedHullPiece` / `PlacedModule` / `PlacedTurret` instances referencing those ids with appropriate positions and rotations.
    3. Assemble a `ShipDesign` from the pieces and serialize to JSON using the existing kotlinx.serialization setup.
    4. Write to the corresponding file under `composeResources/files/default_ships/`.
    5. Separately, collect all unique `GunData` instances from `DemoScenarioConfig.turretConfigs` (keyed by the turret id the builder uses — inspect `PartsCatalog` for the id convention) and serialize them to `turret_guns.json`.
  - The script can be invoked via a `./gradlew :components:game-core:api:test --tests "...DemoScenarioConfigMigration"` or as a `main()` function run locally. Pick whichever is simpler.
  - **After the script runs and the JSON is committed, delete the script file.** Leave no temporary code in source control.
  - Numerical preservation is guaranteed by construction because the migration reads `DemoScenarioConfig`'s fields directly and copies them into `ItemDefinition` fields with matching semantics. The `BundledDefaultsTest` round-trips each committed JSON through the Unit 4 converter and asserts deep-equality against the original `DemoScenarioConfig` entries. This replaces the rejected ±5% tolerance gate.

  **Patterns to follow:**
  - Existing `ShipDesign` serialization format.
  - `ShipDesignSerializationTest.kt` for JSON shape reference.

  **Test scenarios:**
  - Happy path (post-run): each of the four ship JSON files parses as a valid `ShipDesign`.
  - Happy path: each parsed `ShipDesign` passes through `ShipDesignConverter.convert` successfully.
  - Happy path: each converted `ShipConfig` is **deep-equal** to its corresponding `DemoScenarioConfig` entry (mass, thrust, hull vertices, module specs, turret configs all identical — no tolerance).
  - Happy path: `turret_guns.json` parses as `Map<String, GunData>` and contains every turret id referenced by the four ship designs.
  - Regression: `ShipDesignSerializationTest` continues to pass.

  **Verification:**
  - The migration script runs to completion and writes five JSON files.
  - All `BundledDefaultsTest` scenarios pass.
  - The migration script is deleted from source control.
  - A manual check: loading each default design in the builder UI renders it correctly.

---

- [ ] **Unit 6: `DefaultShipDesignLoader` and `TurretGunLoader`**

  **Goal:** Two loaders that read the bundled JSON resources at game startup and expose them to `GameStateManager` via DI.

  **Requirements:** R31, R32.

  **Dependencies:** Unit 3 (Compose Resources configured), Unit 5 (files exist).

  **Files:**
  - Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/DefaultShipDesignLoader.kt`
  - Create: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/TurretGunLoader.kt`
  - Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — `@Provides` both loaders.
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/LoaderTest.kt`

  **Approach:**
  - **Both loaders run eagerly at `AppComponent` init (DI construction time), not at `startDemoScene` time.** This avoids the `suspend` propagation problem — `Res.readBytes` is suspending but DI construction can run in a coroutine scope (e.g., `runBlocking` at app startup or a `CoroutineScope` from the DI graph). Results are cached in the loader singletons.
  - `DefaultShipDesignLoader.loadAll()` returns a `Map<String, ShipDesign>` keyed by filename stem (`"player_ship"`, `"enemy_light"`, `"enemy_medium"`, `"enemy_heavy"`). Reads via `Res.readBytes("files/default_ships/<stem>.json")` and deserializes.
  - `TurretGunLoader.load()` returns a `Map<String, GunData>` from `turret_guns.json`.
  - The list of ship filenames is hard-coded (four stems). Missing files fail loudly **at app startup**, not per-scene.
  - `startDemoScene` reads from the cached maps synchronously. No `suspend` keyword needed on `startDemoScene` or `restartScene`.
  - Any parse or missing-file error is a fatal exception at AppComponent init. This means the app crashes on launch if bundled resources are broken — which is the intended "these are shipped resources; failure is a dev bug" semantics.

  **Patterns to follow:**
  - `FileShipDesignRepository.loadAll()` for the kotlinx.serialization deserialize pattern.
  - kotlin-inject singleton provisioning in `AppComponent.kt`.

  **Test scenarios:**
  - Happy path: `DefaultShipDesignLoader.loadAll()` returns exactly four designs with the expected keys.
  - Happy path: `TurretGunLoader.load()` returns a non-empty map containing every turret id referenced by the four ship designs.
  - Happy path: each loaded ship design passes through `ShipDesignConverter.convert` without error.
  - Edge case: results are stable across repeated calls (deterministic).
  - Error path: if a file is missing (simulated by skipping in the test), the loader throws a clear exception naming the missing file.

  **Verification:**
  - Unit tests pass.
  - Starting the desktop app produces no loader errors in stdout/logs.

---

### Sub-phase 3c — Switchover

- [ ] **Unit 7: Rewrite `GameStateManager.startDemoScene` to load designs from resources**

  **Goal:** Replace the hard-coded `createShip(config = DemoScenarioConfig.playerShipConfig, ...)` calls in `startDemoScene` with a loader + converter + createShip pipeline. Use filename-indexed lookup to map loaded designs to spawn slots. Preserve the existing 2-player-vs-4-enemy arrangement.

  **Requirements:** R31, R32.

  **Dependencies:** Units 0–6.

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — rewrite `startDemoScene` body.
  - Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` — wire `DefaultShipDesignLoader`, `TurretGunLoader`, and `ShipDesignConverter` into `GameStateManager`.
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt` (create if absent).

  **Approach:**
  - At the start of `startDemoScene`:
    1. Load designs: `val designs = defaultShipDesignLoader.loadAll()` (a `Map<String, ShipDesign>`).
    2. Load turret guns: `val turretGuns = turretGunLoader.load()`.
    3. Convert each spawn slot's design via `shipDesignConverter.convert(designs[key]!!, turretGuns).getOrThrow()`. Fatal on failure.
  - Spawn slot → filename key mapping (hard-coded):
    - Player slot 1 → `"player_ship"`
    - Player slot 2 → `"player_ship"` (same design, different position)
    - Enemy slot 1 → `"enemy_light"`
    - Enemy slot 2 → `"enemy_medium"`
    - Enemy slot 3 → `"enemy_medium"` (reuse)
    - Enemy slot 4 → `"enemy_heavy"`
    - Exact mapping mirrors the current `DemoScenarioConfig` spawn order; adjust if needed once reading the existing code.
  - Call `createShip(config, position, teamId, targetProvider, aiModules, drawOrder)` once per slot with the converted `ShipConfig`.
  - **Failure mode: fatal.** If any load, conversion, or key-lookup fails, throw a descriptive exception. No fallback to hardcoded configs; no silent skipping of slots.

  **Patterns to follow:**
  - Existing `startDemoScene` structure — the orchestration (AI module assembly, position assignments, team ids) is unchanged; only the config source changes.

  **Test scenarios:**
  - Happy path (integration): `startDemoScene` runs with the real loaders and converter. After return, `playerShips.size == 2` and `enemyShips.size == 4`.
  - Happy path: each spawned ship has non-empty `hulls` and a non-null position.
  - Happy path: behaviour parity — combat is playable and subjectively feels the same as pre-Unit-7.
  - Error path: if `DefaultShipDesignLoader` is stubbed to return an empty map, `startDemoScene` throws a clear exception naming the missing key.
  - Regression: bullets hit ships, explosions render, AI pursues targets — all unchanged.

  **Verification:**
  - Desktop app starts; combat scene spawns six ships; combat is playable.
  - Ships render with polygon/vector rendering from Unit 2.
  - Manual dev sign-off: combat feels the same as before the switchover. This is the gate for Unit 8.

---

### Sub-phase 3d — Cleanup

- [ ] **Unit 8: Delete `DemoScenarioConfig` as runtime data**

  **Goal:** Remove the hardcoded `ShipConfig` constants in `DemoScenarioConfig.kt`. Enforces R32 ("no simulation data should exist only in code constants"). **Runs only after a playtest gate:** the dev has played at least a few combat sessions with the migrated designs and confirmed no regressions.

  **Requirements:** R32.

  **Dependencies:** Unit 7 lands and the dev signs off on behaviour parity after at least one playtest session.

  **Files:**
  - Delete: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt` (or reduce to test fixtures under test sources if any test still needs hand-crafted `ShipConfig` values).
  - Modify: any test file still importing `DemoScenarioConfig` — either inline the test fixture or move a slimmed-down version to test sources.
  - Create (if needed): `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/testfixtures/TestShipConfigs.kt` for tests that need hand-crafted `ShipConfig` values without going through the converter.

  **Approach:**
  - `grep` for `DemoScenarioConfig` across all source sets. Production references must be zero after Unit 7. If any remain, surface them as scope expansion and handle before deletion.
  - Let the compiler be the primary verification signal: after deleting, `./gradlew build` must pass. The compiler catches callers `grep` might miss (qualified imports, aliased names, etc.).
  - Delete the file. If test sources still want `DemoScenarioConfig`-like fixtures, move a slimmed version to `commonTest`.

  **Test scenarios:**
  - Regression: `./gradlew allTests` passes after deletion.
  - Regression: `./gradlew build` passes on both JVM and Android.
  - Regression: `./gradlew :composeApp:assembleDebug` passes.
  - Verification: `grep -r "DemoScenarioConfig" src/ features/ components/ composeApp/` returns no results in production sources.

  **Verification:**
  - File is deleted from production sources.
  - Full build passes on both platforms.
  - Desktop app starts and combat runs identically to Unit 7 state.

---

## System-Wide Impact

- **Interaction graph:** The runtime converter is a new pure-function seam called once per ship at scenario startup. No hot-path changes; no per-frame impact. Polygon rendering (Unit 2) runs per frame and accepts bounded allocations at current combat scale. `MultiPolygonCollisionMask` delegates per sub-mask, so bullet collision cost scales with hull count per ship — typically 1–3.
- **Error propagation:** Runtime converter returns `Result<ShipConfig>`. Loaders return plain maps and throw on missing/malformed files. `GameStateManager.startDemoScene` is the only consumer of both; failures at startup crash loudly by design. No silent fallbacks.
- **State lifecycle risks:** None introduced — conversion is pure and runs once per scenario spawn. No mid-game reloading. Existing `ShipSystems`, destruction hooks, and actor lifecycle are unchanged.
- **API surface parity:** `ShipConfig.hulls` (was `hull`) is a breaking change to the runtime data model. Unit 1 updates every consumer in one pass. `ShipConfig.drawable` removal in Unit 2 is another breaking change. No external consumers (runtime model lives entirely in the repo). `ShipDesign.formatVersion` does **not** change.
- **Integration coverage:** Unit 4's converter tests exhaustively cover the conversion. Unit 7's integration test proves the end-to-end loader→converter→createShip path. Unit 8's regression pass proves nothing in production references the old hardcoded constants.
- **Unchanged invariants:** Frictionless physics preserved exactly. `ShipNavigator.computeBrakingStrategy`'s `v²/2a` formula is untouched. The damage model (enum-based `ShipSystems`, `ArcDamageRouter`) is unchanged. Turret instantiation (`Turret` child actor with offset/pivot) is unchanged — only its source of config data moves from `DemoScenarioConfig` to the converter.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| `MultiPolygonCollisionMask` implementation may hit Kubriko contract details that only surface during integration — e.g., sub-mask pose composition, collision response routing | Unit 1 test scenarios include a multi-hull fixture that exercises both `isSceneOffsetInside` and the real bullet-collision pipeline. If Kubriko's `ComplexCollisionMask` interface has surprising requirements, treat it as scope discovered in Unit 1 and handle within that unit rather than bailing. |
| Polygon union computation for the merged silhouette is non-trivial and has no existing utility in the codebase | Start with the simplest correct approach: if default hulls are non-overlapping, a convex hull of all vertex unions may be acceptable. If hulls overlap, a proper sweep-line or Weiler-Atherton union is needed. Pick during Unit 2 based on what the default designs actually look like. Document the approach and any known edge cases. |
| Compose Resources plugin configuration in `game-core/api` may have platform-specific quirks (Android resource bundling, JVM classpath access) | Unit 3's smoke test explicitly verifies both platforms before Units 5/6 land. If one platform fails, resolve before proceeding. |
| Polygon rendering regresses visual clarity vs sprites, especially at small screen sizes | Accepted risk. Unit 2's verification is an informal dev-sign-off, and the nose marker explicitly addresses the facing-ambiguity concern. If a regression is discovered after Unit 2, iterate on rendering before Unit 7's gate. |
| The migration script may miss fields when synthesizing `ItemDefinition`s from `DemoScenarioConfig` (e.g., computing mass/density splits that weren't separated in the original data) | `BundledDefaultsTest` asserts round-trip deep-equality, not tolerance. Any missing field causes the test to fail loudly before the migration script is deleted. |
| Multi-module-per-type rejection may surprise the dev if they accidentally place two reactors | Clear error message with the duplicated type name. Builder could surface a similar warning in a follow-up. |
| Atmospheric's "reinterpretation" of `MovementConfig` turns out to require parallel per-profile movement configs rather than a pure reinterpretation | Acknowledged. Phase 3's converter may need a follow-up revision when atmospheric lands. This is not a blocker for Phase 3; atmospheric pays for the adjustment when it gets here. |
| Dev forgets to delete the migration script, leaving temporary code in `main` | Unit 5's verification explicitly includes "the migration script is deleted from source control". Add a `grep` step to the PR checklist. |

## Phased Delivery

**Sub-phase 3a — Foundation (Units 0, 1, 2, 3):**
- Unit 0: Extract stats core
- Unit 1: `MultiPolygonCollisionMask` + multi-hull `ShipConfig`
- Unit 2: Polygon/vector rendering (ships, modules, turrets)
- Unit 3: Compose Resources plugin in game-core/api

After 3a, the game still runs off `DemoScenarioConfig` (temporarily wrapped in `hulls = listOf(...)`) but the data shape, collision, rendering, and resource-loading infrastructure are ready. Each unit lands as its own commit. The codebase at the end of 3a is stable and playable — polygon rendering is visible, but the actual loading pipeline hasn't been wired yet.

**Sub-phase 3b — Conversion and content (Units 4, 5, 6):**
- Unit 4: `ShipDesignConverter`
- Unit 5: Migration script + bundled JSON output + delete the script
- Unit 6: `DefaultShipDesignLoader` and `TurretGunLoader`

After 3b, the converter is proven via tests and the default designs exist as committed JSON. Nothing in the game has switched to the new path yet. Natural validation gate: run the converter tests in isolation, confirm everything works before touching startup.

**Sub-phase 3c — Switchover (Unit 7):**
- Unit 7: Rewrite `startDemoScene` to load and convert

After 3c, the game runs off migrated designs. `DemoScenarioConfig` still exists in source but is no longer referenced by the runtime. Manual dev playtest gate — play at least one combat session, confirm behaviour parity, then proceed.

**Sub-phase 3d — Cleanup (Unit 8):**
- Unit 8: Delete `DemoScenarioConfig`

Runs only after 3c's playtest gate. Harmless lingering of the old constants between 3c and 3d is the insurance policy.

Each sub-phase ends with a working desktop app. No mid-phase broken states.

## Documentation / Operational Notes

- **Update `docs/brainstorms/2026-04-06-ship-builder-requirements.md`** after Phase 3 ships: mark R31 and R32 as satisfied.
- **Update `docs/ship.md`** to reflect the multi-hull data model and polygon rendering.
- **Post-Deploy Monitoring & Validation:** No additional operational monitoring required. This is a pre-release prototype change with no production impact.

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-06-ship-builder-requirements.md](../brainstorms/2026-04-06-ship-builder-requirements.md) — Phase 3 requirements R31, R32.
- **Downstream dependency:** [docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md](../brainstorms/2026-04-10-atmospheric-movement-requirements.md) — atmospheric movement brainstorm, which names this plan as its prerequisite.
- **Prior phase plans:** [docs/plans/2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md](2026-04-06-001-feat-ship-builder-phase0-phase1-plan.md), [docs/plans/2026-04-06-002-feat-ship-builder-phase2-item-creation-plan.md](2026-04-06-002-feat-ship-builder-phase2-item-creation-plan.md).
- **Key runtime files:** `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt`, `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt`.
- **Key builder files:** `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt`, `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt`, `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt`, `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/data/FileShipDesignRepository.kt`.
- **Kubriko collision:** `com.pandulapeter.kubriko.collision.mask.ComplexCollisionMask` — the extension point for multi-mask actors used by Unit 1's `MultiPolygonCollisionMask`.
- **Vector rendering precedent:** commits `e67d2ea` (bullet vector rendering refactor) and `da0a983` (bullet sprite removal) — the direction Unit 2 extends to ships, modules, and turrets.

## Second-Pass Review Changes (2026-04-12)

Applied after a 6-reviewer document review (2 passes, ~50 findings):

**Critical fixes (applied above):**
- **Unit 1 collision model replaced.** `MultiPolygonCollisionMask implementing ComplexCollisionMask` is impossible (sealed interface, hardcoded collision dispatch). Replaced with per-hull child `Collidable` actors — `HullCollider` children with per-hull `PolygonCollisionMask`, damage routing back to parent `Ship`. Bullet.kt adapted to hit `HullCollider` rather than `Ship` directly. Per-hull armour resolution resolves naturally.
- **Unit 3 reframed.** Compose Resources is already configured in game-core/api; the generated `Res` class just needs to be made public (`publicResClass = true`). Not a plugin addition.
- **Loading model specified.** Loaders run eagerly at `AppComponent` init, cached in singleton fields. `startDemoScene` reads synchronously. `Res.readBytes` is suspending, but DI construction runs in a coroutine scope. No suspend propagation through `restartScene` or `GameVM`.
- **`GunData` serialization prerequisite added.** `GunData` must have `@Serializable`, `drawable` field removed from type definition, `AngleRadians` serializer added/reused.
- **Body.size derivation rule.** `Ship` and `Turret` body.size computed from polygon AABB, replacing sprite pixel dimensions. Turret muzzle offset re-derived.
- **`evasionModifier` deferred to Keel.** Uniform default in Phase 3. Deep-equal test excludes this field.
- **Runtime model extended for module geometry.** Module polygons passed through the converter so Unit 2's module rendering has data to work with.

**Smaller fixes (applied above):**
- Revised per-frame allocation estimate from "~30-50" to "~200" (more honest across the full rendering pipeline).
- Migration script should live in a build-excluded location (e.g., `scripts/migration/` outside Gradle source sets) rather than `commonTest`.
- Unit 7 renames `DemoScenarioConfig.kt` → `DemoScenarioConfig_DEPRECATED.kt` as a forcing function for the 3c→3d playtest gate.
- Team colour palette noted: builder uses Red for turrets; if enemy team is red-family, turret triangles become indistinguishable on enemy ships. Resolve palette as one coherent decision during implementation (not five deferred items).

**Findings accepted without change:**
- Bundle size (9 units, including the scope expansion to per-hull child actors and runtime module geometry).
- Polygon union for merged silhouette is dead code for v1's single-hull default ships. Accepted — it ships for the future multi-hull case.
- Informal verification (dev plays and signs off). No screenshot artifact required.
- Regression-only success criteria.
- Migration script deletion enforcement via build-excluded location (no mechanical CI gate).
