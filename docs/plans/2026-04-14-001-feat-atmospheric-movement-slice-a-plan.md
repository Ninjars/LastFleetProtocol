---
title: "feat: Atmospheric Movement Slice A — directional drag and physics reframe"
type: feat
status: active
date: 2026-04-14
origin: docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md
---

# feat: Atmospheric Movement Slice A — Directional Drag and Physics Reframe

## Overview

Replace the frictionless Newtonian physics model with quadratic directional drag. Hull shape now affects movement: per-hull-piece drag modifiers produce different terminal velocities per axis, producing the "naval feel" (committed passes, momentum costs on turning, visible skid) the brainstorm targets. The builder's stats panel switches from acceleration to terminal velocity. The AI navigator's braking math is patched to work under drag without oscillating.

This is Slice A only — Keels, lift, and the second death condition (Slice B) are a separate plan.

## Problem Frame

Ships currently feel the same regardless of hull shape. Movement is driven by scalar thrust values in `MovementConfig` with no drag, no terminal velocity, and no relationship between polygon geometry and performance. The `v² / 2a` braking formula in `ShipNavigator.computeBrakingStrategy` assumes constant deceleration and must be reworked. (see origin: `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`)

Phase 3 (game integration) has shipped — the builder→simulation pipeline works, ships load from bundled JSON, and all rendering is polygon-based. This unblocks Slice A.

## Requirements Trace

From the origin doc (Slice A only):

- **R1.** Replace frictionless physics with atmospheric drag. Full replacement, no coexistence.
- **R2.** Quadratic drag (`F = k·v²`). Not linear.
- **R3.** Per-axis drag coefficients computed at ship-load time from hull geometry.
- **R4.** Smooth angular interpolation of drag at runtime — no step discontinuity when rotating.
- **R5.** Per-axis terminal velocity: `v_t = √(thrust / dragCoeff)`.
- **R6.** Angular rotation uses a **turn-rate model** (degrees per second) instead of the current angular-momentum/torque model. Turn rate is a computed ship stat, derived from `angularThrust / totalMass`. This replaces angular force accumulation, angular drag, and moment-of-inertia concerns with a single rate-limited rotation. Simplifies rotation-time calculations for the navigator (angle / turnRate).
- **R22.** `MovementConfig` thrust values preserved structurally, reinterpreted as fighting drag.
- **R23.** Builder stats panel: terminal velocity per axis replaces acceleration.
- **R27.** Multi-hull drag derivation from the union of hull pieces.
- **R28.** Per-hull-piece drag modifiers (forward, lateral, reverse) authored in the builder.
- **R29.** Aggregate drag coefficient: bounding-box extent × area-weighted modifier average (initial approximation; perimeter-visibility is a follow-up).
- **R30.** Interim AI braking patch: `computeBrakingStrategy` replaced with drag-aware closed-form stopping distance. Acceptance: AI ships approach targets without oscillating.

## Scope Boundaries

- **Slice B (Keels, lift, death condition) is a separate plan.** No Keel data model, no `KEEL` enum, no `LIFT_FAILED` state, no flightworthiness indicator.
- **Wind interface (R7) is deferred.** Wind is a concept that may be followed up later. No wind-force plumbing, no wind parameter on the physics interface in this slice.
- **Full AI rewrite is deferred.** The navigator receives a targeted braking-distance patch (R30, Unit 5). No higher-level tactics (skid maneuvers, drag-aware intercept planning), no long-range navigation overhaul.
- **Wind is deferred.** The physics loop is structured to allow a future wind force, but no wind is applied.
- **Perimeter-visibility weighting for R29 is deferred.** Initial implementation uses area-weighted modifier average.
- **No `ShipDesign.formatVersion` bump in Slice A.** The new `HullAttributes` fields (`forwardDragModifier` etc.) have defaults of `1.0f`, so existing designs remain loadable. The format bump (2 → 3) happens in Slice B when the Keel item type is introduced.
- **Content retuning (new thrust/drag values for bundled ships) is in scope** — ships will feel different post-drag; the JSON files must be updated so combat is playable.

## Context & Research

### Relevant Code and Patterns

**Physics core:**
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt` — semi-implicit Euler integration. `applyThrust()` accumulates forces; `decelerate()` applies linear opposing force; `integrate()` resolves `F=ma` per frame. Drag replaces the existing `decelerate()` mechanism.

**Navigation / AI braking:**
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt` — `computeBrakingStrategy()` at lines 279–402 uses `v²/(2a)` in three places (lines 96, 343, 391). `MAX_SPEED_FACTOR = 10f` at line 433 is used for a speed cap that terminal velocity replaces. `navigate()` decomposes velocity correction into forward/lateral components and applies thrust.

**Data model (post-Phase 3):**
- `components/game-core/api/.../data/MovementConfig.kt` — four thrust floats. Gains four drag-coeff floats.
- `components/game-core/api/.../data/ShipConfig.kt` — `hulls`, `movementConfig`, etc. No changes needed beyond `MovementConfig` extension.
- `components/game-core/api/.../shipdesign/ItemDefinition.kt` — `HullAttributes` gains three drag modifier floats (default 1.0).
- `components/game-core/api/.../stats/ShipStatsCore.kt` — `ShipStats` gains terminal velocity fields.
- `components/game-core/api/.../shipdesign/ShipDesignConverter.kt` — computes drag coefficients during conversion.

**Builder UI:**
- `features/ship-builder/impl/.../ui/composables/StatsPanel.kt` — currently shows mass, thrust, and acceleration. Acceleration section replaced with terminal velocity.

### Institutional Learnings

- `docs/ai_kubriko_constraints.md` documents the actor drawing coordinate system (body-local, clipped to body.size) and hot-path allocation guidance. Drag computation should avoid per-frame `SceneOffset` churn.

## Key Technical Decisions

1. **Drag coefficients live in `MovementConfig`** alongside thrust values. Four new fields: `forwardDragCoeff`, `lateralDragCoeff`, `reverseDragCoeff`, `angularDragCoeff`. This keeps all movement physics in one record, threaded through `ShipSpec` to the physics system without new plumbing.

2. **Drag-aware stopping distance is closed-form:**
   `x = (m / 2k) · ln((thrust + k·v₀²) / thrust)` where `k` = effective drag coefficient, `v₀` = current speed, `m` = mass, `thrust` = braking thrust. Replaces `v²/(2a)` in three navigator formulas. One `ln()` call per braking-strategy evaluation — cheap.

3. **Angular interpolation of drag (normalized):**
   `forwardComp = max(0, cos(θ))`, `reverseComp = max(0, -cos(θ))`, `lateralComp = |sin(θ)|`. Normalize: `total = forwardComp + reverseComp + lateralComp`; then `effectiveDrag = (forward·forwardComp + reverse·reverseComp + lateral·lateralComp) / total`. This ensures diagonal movement is not penalized by an interpolation artifact — effective drag at 45° is between the forward and lateral values, not above both. Drag force opposes velocity direction, not ship heading. This produces smooth skid.

4. **Area-weighted modifier aggregation for initial R29:**
   Each hull piece's drag modifiers (default 1.0) are averaged weighted by the piece's polygon area relative to total hull area. Simple, deterministic, no geometry-intersection needed. Perimeter-visibility is a follow-up.

5. **Terminal velocity replaces MAX_SPEED_FACTOR:**
   Currently `maxSpeed = thrust/mass * 10f`. Under drag, terminal velocity is `sqrt(thrust / dragCoeff)` — emergent, not arbitrary. Navigator uses this as the speed cap.

6. **Turn-rate model replaces angular momentum.** Instead of angular thrust → torque → angular velocity → angular drag, rotation is rate-limited: the ship turns at `turnRate` degrees per second (derived from `angularThrust / totalMass * TURN_RATE_SCALE`). This eliminates angular force accumulation, angular drag, and MoI concerns. Navigator rotation-time calculation becomes trivially `angleToRotate / turnRate`. `MovementConfig` retains `angularThrust` for the input; `ShipStats` gains `turnRate` as the derived output. No `angularDragCoeff` field needed.

## Open Questions

### Resolved During Planning

- **Stopping-distance formula?** → Closed-form: `(m/2k) · ln((thrust + k·v₀²) / thrust)`. Verified analytically.
- **Angular interpolation function?** → cos/sin component blending (see Key Decision 3).
- **Angular model?** → Turn-rate (degrees/sec) replaces angular momentum/drag. Derived from `angularThrust / totalMass`. Eliminates angular drag, MoI, and complex rotation-time estimation.
- **Drag modifier range?** → 0.5–2.0 with 1.0 default. Three floats on `HullAttributes`.
- **Initial R29 approximation?** → Area-weighted modifier average.
- **Where do drag coefficients live?** → `MovementConfig` extension (three linear drag coefficients; no angular drag coefficient).
- **RHO (air density constant)?** → Dimensionless tuning constant, default 1.0, stored as a companion object constant in `ShipStatsCore`. Scales all drag coefficients uniformly. Adjusted during Unit 7 content tuning if drag feels too weak/strong.
- **Angular interpolation normalization?** → Yes, normalize by weight sum. Diagonal movement is not penalized.
- **Zero-drag fallback?** → When `dragCoeff < epsilon`, fall back to frictionless `v²/(2a)` formula (the mathematical limit as k→0). Clamp all speed results to terminal velocity.
- **R7 (wind interface)?** → Deferred entirely. Wind is a concept for future exploration, not a plumbing requirement for Slice A.

### Deferred to Implementation

- **Exact drag coefficient tuning for bundled default ships.** Feel-driven; iterate during content retuning unit. Scope: one tuning session producing distinct archetypes; polish is follow-up.
- **`TURN_RATE_SCALE` constant value.** Controls how `angularThrust / totalMass` maps to degrees-per-second turn rate. Pick during implementation; tune in Unit 7.
- **Exact UI layout for terminal velocity in the stats panel.** Follow existing `StatRow` pattern; may add a drag coefficient readout for debugging.
- **Navigator snap-to-stop radius.** Near zero speed, quadratic drag vanishes and ships may undershoot destinations. The navigator should apply a small braking thrust at low speeds near the destination. The snap-to-stop radius may need increasing. Verify during Unit 5.
- **PolygonArea utility signature.** The duplicated/extracted shoelace utility operates on `List<SceneOffset>` or raw floats, not `Offset` (the existing function in ship-builder/impl takes `Offset`). Small signature decision during Unit 2.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification.*

```
Per-frame physics loop (Ship.update → ShipNavigator.navigate → ShipPhysics.integrate):

  1. Navigator evaluates destination, computes desired correction
  2. Navigator applies thrust forces via physics.applyThrust()
     (unchanged — thrust still pushes the ship)
  3. NEW: Navigator rotates ship toward desired heading at turnRate deg/sec
     (replaces angular force/torque — simple rate-limited rotation)
  4. NEW: Physics computes drag force:
     a. θ = angle between velocity vector and ship forward axis
     b. fwd = max(0,cos θ); rev = max(0,-cos θ); lat = |sin θ|
     c. effectiveDrag = (forward*fwd + reverse*rev + lateral*lat) / (fwd+rev+lat)
        [normalized — diagonal is not penalized]
     d. F_drag = effectiveDrag · |v|² · (-v_hat)   [opposes velocity]
     e. Add F_drag to accumulatedForce
  5. NEW: At low speed near destination, navigator applies small braking thrust
     to bring ship to rest (quadratic drag vanishes near zero velocity)
  6. Physics.integrate() runs semi-implicit Euler (unchanged linear math, new drag force included)
     Angular integration removed — rotation handled by turn-rate in step 3

At ship-load time (ShipDesignConverter):
  - For each hull piece: read HullAttributes.{forward,lateral,reverse}DragModifier
  - Compute polygon area per piece (shoelace formula)
  - Aggregate: per-axis dragCoeff = boundingBoxExtent * Σ(modifier_i * area_i) / Σ(area_i) * RHO
  - Store in MovementConfig.{forward,lateral,reverse}DragCoeff
  - Angular drag coefficient: constant or derived from hull area (tune during implementation)

Terminal velocity (builder stats):
  - v_t = sqrt(thrust / dragCoeff) per axis
  - Displayed in stats panel replacing acceleration
```

## Implementation Units

### Phase A1: Data Model and Computation

- [x] **Unit 1: Add drag fields to data model**

  **Goal:** Extend `HullAttributes`, `MovementConfig`, and `ShipStats` with drag-related fields so downstream units have the data structures they need.

  **Requirements:** R3, R22, R28

  **Dependencies:** None.

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt` — add `forwardDragModifier`, `lateralDragModifier`, `reverseDragModifier` to `HullAttributes` (default 1.0f)
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/MovementConfig.kt` — add `forwardDragCoeff`, `lateralDragCoeff`, `reverseDragCoeff` (default 0f). No `angularDragCoeff` — angular model uses turn-rate, not drag.
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt` — add `terminalVelForward`, `terminalVelLateral`, `terminalVelReverse`, `turnRate` to `ShipStats`
  - Modify: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignSerializationTest.kt` — verify new fields round-trip with defaults
  - Test: existing serialization tests + new round-trip for drag modifier fields

  **Approach:**
  - All new fields have sensible defaults (1.0 for modifiers, 0.0 for coefficients) so existing code and JSON continue to work without changes.
  - No `formatVersion` bump — defaults handle backward compatibility.
  - `ShipStats.terminalVelForward` etc. are computed fields populated by `calculateShipStats()` in Unit 2.

  **Patterns to follow:**
  - Existing `HullAttributes` field pattern (`mass: Float`).
  - Existing `MovementConfig` field pattern.

  **Test scenarios:**
  - Happy path: `HullAttributes` with explicit drag modifiers (0.5, 1.5, 2.0) serializes and deserializes correctly.
  - Happy path: `HullAttributes` without drag modifiers deserializes with defaults (1.0, 1.0, 1.0).
  - Happy path: `MovementConfig` with drag coefficients serializes if needed (not currently serialized, but verify construction).
  - Edge case: drag modifier of 0.0 is valid (frictionless in that axis).

  **Verification:**
  - All existing tests pass (backward-compatible defaults).
  - Full build compiles.

---

- [x] **Unit 2: Drag coefficient computation in ShipStatsCore + ShipDesignConverter**

  **Goal:** Compute per-axis drag coefficients from hull geometry and modifiers. Populate `MovementConfig.dragCoeff*` in the converter. Compute terminal velocities in `ShipStats`.

  **Requirements:** R3, R5, R27, R29

  **Dependencies:** Unit 1.

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt` — add drag computation + terminal velocity to `calculateShipStats()`
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt` — compute drag coefficients from hull pieces, populate `MovementConfig`
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/geometry/PolygonArea.kt` — if not already accessible from game-core/api, extract or duplicate the shoelace area utility
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCoreTest.kt`
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverterTest.kt`

  **Approach:**
  - **Aggregate drag per axis (R29 initial approximation):**
    1. For each placed hull piece, compute polygon area (shoelace formula)
    2. Read `HullAttributes.{forward,lateral,reverse}DragModifier` (default 1.0)
    3. Weighted average per axis: `Σ(modifier_i · area_i) / Σ(area_i)`
    4. Compute bounding box extent (width for forward/reverse, height for lateral)
    5. Per-axis drag coefficient = extent × weighted_modifier_average × `RHO` (air density constant — tune as a global parameter)
  - **Angular drag coefficient:** Initially derive from total hull area × a constant. Tune during implementation.
  - **Terminal velocity:** `v_t = sqrt(thrust / dragCoeff)` per axis. If `dragCoeff` is 0 (no drag), terminal velocity is `Float.MAX_VALUE` (effectively unlimited, matching old behaviour).
  - **`PolygonArea` location:** Currently in `features/ship-builder/impl/.../geometry/PolygonArea.kt`. The shoelace formula is a pure function. Either extract to `components/game-core/api` or duplicate the 10-line function in the stats core. The latter is pragmatic; extraction is cleaner.

  **Patterns to follow:**
  - Existing `calculateShipStats()` pattern for aggregation.
  - Existing `ShipDesignConverter.convertShipDesign()` for hull iteration.

  **Test scenarios:**
  - Happy path: single hull with default modifiers (1.0) → drag coefficient proportional to bounding box extent.
  - Happy path: two hull pieces with different modifiers → area-weighted average produces expected coefficient.
  - Happy path: terminal velocity = `sqrt(1200 / dragCoeff)` for forward axis with 1200f thrust.
  - Happy path: converter produces a `MovementConfig` with non-zero drag coefficients for a standard design.
  - Edge case: hull with zero area (degenerate) → fallback drag coefficient (non-zero).
  - Edge case: zero thrust → terminal velocity is 0 (can't move in that axis).
  - Edge case: zero drag coefficient → terminal velocity is `Float.MAX_VALUE`.

  **Verification:**
  - All stats core and converter tests pass.
  - Builder's stats panel shows non-zero terminal velocity values (may display alongside existing acceleration until Unit 6 replaces it).

---

### Phase A2: Physics and Wiring

- [x] **Unit 3: Quadratic drag in ShipPhysics**

  **Goal:** Add quadratic drag force integration to `ShipPhysics`. Drag is applied per frame as a force opposing velocity, with magnitude proportional to `v²` and the effective drag coefficient (smoothly interpolated and normalized from per-axis values). Replace angular momentum model with turn-rate rotation.

  **Requirements:** R1, R2, R4, R6

  **Dependencies:** Unit 1 (drag coefficient fields exist on `MovementConfig`).

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt` — add `applyDrag()` method, replace angular momentum with turn-rate rotation, remove/deprecate old `decelerate()` and angular force accumulation
  - Modify: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysicsTest.kt` — replace existing `decelerate()` test with drag-based equivalent
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysicsTest.kt`

  **Execution note:** Write characterization tests for the existing `decelerate()` behaviour before modifying, so regressions in the integration path are caught. Note: an existing test at line ~141 directly calls `decelerate()` and will break when the method is removed — replace it.

  **Approach:**
  - **New method `applyDrag(movementConfig, shipRotation, deltaMs)`:**
    1. Compute angle `θ` between current velocity and ship forward direction
    2. Compute weights: `fwd = max(0, cos θ)`, `rev = max(0, -cos θ)`, `lat = |sin θ|`
    3. **Normalize:** `effectiveDrag = (forwardDrag·fwd + reverseDrag·rev + lateralDrag·lat) / (fwd + rev + lat)`
    4. Drag force magnitude = `effectiveDrag · |velocity|²`
    5. Drag direction = `-velocity_normalized` (opposes velocity)
    6. Add to `accumulatedForce`
    7. Cap drag force magnitude so it can't reverse velocity within a single frame (same guard as existing `decelerate()`)
    8. **Zero-drag guard:** if `effectiveDrag < epsilon`, skip drag application (frictionless fallback).
  - **Turn-rate rotation (replaces angular momentum):**
    - Remove `accumulatedTorque`, `applyAngularForce()`, `decelerateAngular()`, and the angular integration path from `integrate()`.
    - Add `rotateToward(targetAngle, turnRate, deltaMs)` — simple rate-limited rotation: `rotation += clamp(angleDelta, -turnRate*dt, turnRate*dt)`.
    - Ship heading changes are now deterministic and predictable, not momentum-based.
  - **`applyDrag()` is called once per frame** from `Ship.update()` or `ShipNavigator.navigate()`, before `integrate()`. This replaces the current `decelerate()` call path.
  - **`decelerate()`, `decelerateAngular()`, and angular force methods are removed.**

  **Patterns to follow:**
  - Existing `applyThrust()` pattern for force accumulation.
  - Existing velocity-reversal guard in `decelerate()`.

  **Test scenarios:**
  - Happy path: ship with forward velocity and non-zero forward drag → velocity decreases each frame.
  - Happy path: ship at terminal velocity (thrust = drag) → velocity is approximately constant.
  - Happy path: ship moving forward with no thrust → drag decelerates to zero.
  - Happy path: smooth interpolation — ship moving at 45° between forward and lateral → effective drag is a blend of both coefficients.
  - Happy path: turn-rate rotation — ship facing 0° asked to face 90° at 60 deg/sec → reaches target in ~1.5 seconds.
  - Happy path: turn-rate rotation — ship doesn't overshoot the target angle.
  - Edge case: zero velocity → no drag force applied (no division by zero).
  - Edge case: very high velocity → drag force is large but capped to not reverse velocity in one frame.
  - Edge case: zero drag coefficients → no drag applied (frictionless fallback).
  - Edge case: diagonal movement (45°) → effective drag is between forward and lateral values (normalized, not above both).
  - Integration: thrust + drag reach equilibrium at terminal velocity (within tolerance over N frames).

  **Verification:**
  - A ship under constant thrust converges to terminal velocity (not unbounded acceleration).
  - A coasting ship decelerates to zero (not continues forever).
  - Rotation is rate-limited and deterministic — no angular momentum accumulation or overshoot.

---

- [x] **Unit 4: Wire drag through Ship and Navigator**

  **Goal:** Connect the drag coefficients from `ShipSpec.movementConfig` into the physics loop. Navigator calls `applyDrag()` each frame. Navigator's desired-speed computation uses terminal velocity instead of `MAX_SPEED_FACTOR`.

  **Requirements:** R1, R4, R22

  **Dependencies:** Unit 2 (terminal velocity computed in ShipStats/MovementConfig), Unit 3 (drag forces in ShipPhysics).

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt` — replace `MAX_SPEED_FACTOR * thrust/mass` with terminal velocity; call `physics.applyDrag()` in the navigate loop; remove `ANGULAR_DRAG` constant
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — ensure `applyDrag()` is called each frame (either in `update()` directly or via navigator)
  - Remove: references to `physics.decelerate()`, `physics.decelerateAngular()`, `MAX_SPEED_FACTOR`, and `ANGULAR_DRAG` from the navigator

  **Approach:**
  - **Navigator.navigate():** Replace `maxSpeed = movementConfig.forwardThrust / physics.mass * MAX_SPEED_FACTOR` with `maxSpeed = terminalVelocity(movementConfig)` where terminal velocity is `sqrt(forwardThrust / forwardDragCoeff)`.
  - **Drag call placement:** The navigator already calls `physics.decelerate()` in certain states (arriving, no destination). Replace ALL `decelerate()` calls with a single `physics.applyDrag(movementConfig, body.rotation, deltaMs)` called once per frame, unconditionally — drag is always active, not just during braking.
  - **Navigator.navigate() braking threshold:** Currently at line 96 uses `sqrt(2 * decel * distance)`. This is updated in Unit 5; for this unit, just cap it by terminal velocity.

  **Patterns to follow:**
  - Existing `navigator.navigate()` call in `Ship.update()`.

  **Test scenarios:**
  - Happy path: ship under thrust reaches terminal velocity and stabilises (doesn't accelerate beyond).
  - Happy path: ship with no destination and no thrust decelerates to zero via drag.
  - Integration: full Ship.update() cycle with drag produces a stable equilibrium under constant thrust.

  **Verification:**
  - Desktop app runs; ships move, reach terminal velocity, and decelerate when thrust stops.
  - No oscillation or jittering at terminal velocity.
  - `MAX_SPEED_FACTOR` constant is deleted.

---

### Phase A3: Navigator Patch, UI, and Content

- [x] **Unit 5: Interim AI braking patch (R30)**

  **Goal:** Replace the three `v²/(2a)` braking formulas in `ShipNavigator.computeBrakingStrategy()` with the closed-form drag-aware stopping distance. AI ships approach targets without oscillating.

  **Requirements:** R30

  **Dependencies:** Unit 4 (drag is active; terminal velocity is the speed cap).

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt` — replace `computeBrakingStrategy()` internals

  **Approach:**
  - **New stopping distance formula:** `x = (m / 2k) · ln((thrust + k·v₀²) / thrust)` where `k` = effective drag coefficient (blended per the current velocity direction), `v₀` = current speed, `m` = mass, `thrust` = available braking thrust.
  - **Three replacement sites:**
    1. Line ~343: `currentStopDist = stoppingDistanceUnderDrag(speed, currentDecel, effectiveDrag, mass)`
    2. Line ~391: Same formula in the rotate-then-brake candidate
    3. Line ~96: `desiredSpeed = min(terminalVelocity, speedForBrakingDistance(distanceToTarget, ...))`
  - **`speedForBrakingDistance(distance)`** is the inverse of the stopping-distance formula: `v₀ = sqrt((thrust/k) · (exp(2k·x/m) - 1))`. Clamp result to terminal velocity to prevent runaway values at large distances.
  - **Zero-drag guard:** when `dragCoeff < epsilon`, fall back to the frictionless `v²/(2a)` formula (the mathematical k→0 limit).
  - **Rotation time is now trivial:** `angleToRotate / turnRate` (degrees / degrees-per-second). No complex angular-acceleration estimation. The rotate-then-brake candidate scoring simplifies significantly.
  - **Low-speed near-destination braking:** At near-zero velocities, quadratic drag vanishes. The navigator must apply a small braking thrust (opposing velocity) when close to the destination and below a speed threshold, so the ship actually comes to rest. This replaces the current `decelerate()` call that served this purpose.
  - **Acceptance criterion:** AI ships approach a target, slow down, and stop within approximately 2× the computed braking distance. No oscillation. No thrashing between rotation decisions.

  **Test scenarios:**
  - Happy path: `stoppingDistanceUnderDrag(speed=100, thrust=1200, dragCoeff=0.5, mass=80)` returns a reasonable positive distance.
  - Happy path: stopping distance at terminal velocity is finite and larger than at half terminal velocity.
  - Happy path: `speedForBrakingDistance(distance=500, ...)` returns a speed less than terminal velocity.
  - Edge case: speed = 0 → stopping distance = 0.
  - Edge case: very high speed → stopping distance grows logarithmically (not quadratically — this is the key difference from `v²/2a`).
  - Integration: AI ship navigating to a point arrives and stops without overshooting repeatedly.

  **Verification:**
  - AI combat is playable — enemy ships engage player ships without jittering.
  - Ships arriving at destinations don't overshoot by more than ~2× braking distance.

  **Actual implementation (divergence from plan):**
  - The navigator was fully rewritten (`3cdeef9`, `e95a5af`), not surgically patched at three sites. The pre-existing `computeBrakingStrategy()` / rotate-then-brake candidate scoring was removed.
  - `stoppingDistanceUnderDrag(speed, thrust, dragCoeff, mass)` is the sole drag-aware brake calculation, with a `dragCoeff < DRAG_EPSILON` → `v²/(2a)` fallback.
  - No `speedForBrakingDistance` inverse is used. Oscillation was instead cured by removing the velocity-correction loop entirely: cruising applies thrust proportional to target-direction alignment and lets drag cap speed at terminal velocity naturally; braking stops thrusting and lets drag decelerate, with `applyLowSpeedBrake` for the near-zero regime where quadratic drag vanishes.
  - Turn-rate rotation (`rotateToward` with `turnRate = computeTurnRate(movementConfig, mass)`) makes rotation-time trivially `angleDelta / turnRate`; the rotate-then-brake candidate scoring simplified out of existence.
  - R30 acceptance criterion (no oscillation, drag-aware braking) is met; the specific internal structure outlined in this unit's Approach section is not the final shape.

---

- [x] **Unit 6: Builder stats panel — terminal velocity display (R23)**

  **Goal:** Replace the acceleration readouts in the builder's stats panel with per-axis terminal velocity. Show forward, lateral, reverse terminal velocities.

  **Requirements:** R23

  **Dependencies:** Unit 2 (terminal velocity computed in `ShipStats`).

  **Files:**
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/StatsPanel.kt` — replace acceleration `StatRow`s with terminal velocity
  - Modify: `components/design/src/commonMain/composeResources/values/strings.xml` — add string resources for terminal velocity labels
  - Modify: `components/design/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/resources/LFRes.kt` — add new string references

  **Approach:**
  - Remove the four acceleration `StatRow` entries (forward, lateral, reverse, angular accel).
  - Add three terminal velocity `StatRow` entries (forward, lateral, reverse). Angular terminal velocity is less useful to display and can be omitted initially.
  - Label as "Max Speed" or "Terminal Vel." for clarity.
  - Values come from `ShipStats.terminalVelForward` etc., formatted to one decimal place.
  - If `terminalVel` is `Float.MAX_VALUE` (no drag), display "∞" or "Unlimited".

  **Patterns to follow:**
  - Existing `StatRow` pattern in `StatsPanel.kt`.
  - String resource convention via `LFRes.String`.

  **Test expectation:** None — pure UI change, verified by running the builder and inspecting the stats panel.

  **Verification:**
  - Builder shows terminal velocity values that change when hull pieces are added/removed (because drag coefficients change).
  - Values are non-zero for ships with hull pieces.

  **Additional changes shipped with this unit (beyond the stated scope):**
  - Hull drag modifier authoring UI in `ItemAttributesPanel.HullAttributesContent` — three `NumericField`s for forward/lateral/reverse drag modifiers, written straight into `HullAttributes.{forward,lateral,reverse}DragModifier`. Without these fields the builder could not tune the R28 per-piece modifiers at all, which would block Unit 7. Added as commit `c565467`.
  - Builder-level validation that rejects zero (or negative) drag modifiers: `NumericField` gained an `isError` param, each drag field sets it when `value <= 0f`, and the Finish button is disabled via a new `ItemAttributes.isValidForSave()` extension. Zero drag on an axis means infinite terminal velocity — unflyable. Data model still accepts 0 (per Unit 1's edge-case test); this is a UI-layer invariant only. Added as commit `23aa9f0`.
  - `StatsPanel` additionally displays `turnRate` alongside the three terminal velocities (not just the three axis terminal velocities originally scoped). The turn rate is a newly derived stat from Unit 3's turn-rate model.

---

- [x] **Unit 7: Default ship design retuning**

  **Goal:** Update the bundled default ship design JSON files with appropriate drag modifier values and retuned thrust values so combat is playable under the new drag model.

  **Requirements:** R22, R28

  **Dependencies:** Units 1–5 (drag model fully operational).

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/player_ship.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_light.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_medium.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json`

  **Approach:**
  - Add `forwardDragModifier`, `lateralDragModifier`, `reverseDragModifier` fields to each hull piece's `HullAttributes` in the JSON.
  - Tune drag modifiers by ship archetype:
    - **Player ship:** streamlined (low forward drag ~0.7, moderate lateral ~1.2)
    - **Light enemy:** nimble (low all-around drag ~0.8)
    - **Medium enemy:** balanced (default ~1.0)
    - **Heavy enemy:** bulky (high forward drag ~1.3, high lateral ~1.8)
  - Adjust thrust values so terminal velocities produce playable combat (ships should reach combat speed within a few seconds, not instantly).
  - This is content/tuning work — iterate by running the game and adjusting values.

  **Execution note:** This unit is content work. Run the game, fight a match, adjust values, repeat until combat feels right. The specific numbers are not knowable at planning time.

  **Test expectation:** None — feel-driven content tuning. Verified by the developer playing combat.

  **Verification:**
  - All four ship types feel distinct: light is fast and nimble, heavy is slow but tanky, player is responsive.
  - Combat produces committed-pass dynamics: ships can't pivot-and-dash; turning at speed causes visible skid.
  - The 60-second sanity check from the brainstorm's success criteria passes.

  **Initial values applied (seed for feel-tuning iteration):**
  Drag modifiers only — existing thrust values left alone since `e95a5af` retuned RHO=0.005 specifically for the previous thrust numbers. With those thrusts and the archetype modifiers below, the predicted per-axis terminal velocities (in scene units/sec) are:

  | Ship | bbox | fwd / lat / rev modifier | Vt fwd | Vt lat | Vt rev |
  |------|------|-------------------------|--------|--------|--------|
  | player_ship  | 112×74  | 0.7 / 1.2 / 1.2 | 55.3 | 33.6 | 27.3 |
  | enemy_light  | 84×60   | 0.8 / 0.8 / 0.8 | 54.6 | 35.4 | 34.5 |
  | enemy_medium | 104×84  | 1.0 / 1.0 / 1.0 | 36.7 | 20.7 | 21.9 |
  | enemy_heavy  | 104×100 | 1.3 / 1.8 / 1.8 | 27.2 | 10.5 | 12.7 |

  Light roughly matches player forward; heavy is ~half player forward with a very poor lateral (expected committed-pass skid). Developer playtest of the 60-second sanity check is still required to confirm feel — if combat is sluggish, iterate by raising engine thrusts or lowering drag modifiers; if too twitchy, invert. Numbers derived from the Unit 2 formula `dragCoeff = extent × modifier × RHO`.

---

## System-Wide Impact

- **Interaction graph:** Drag force is added to `ShipPhysics.accumulatedForce` each frame alongside thrust. No callbacks, no middleware — purely additive. The navigator's thrust application and the drag application both feed into the same `integrate()` call.
- **Error propagation:** No new error paths. Drag is a mathematical operation on existing velocity; division by zero is guarded (zero velocity = no drag). Zero drag coefficient = no drag force (backward-compatible).
- **State lifecycle risks:** None. Drag is stateless — computed from current velocity each frame. No persistent state to invalidate, no caching, no cleanup.
- **API surface parity:** `MovementConfig` gains four fields but with defaults of 0.0 (no drag). Existing callers that construct `MovementConfig` without drag values get the current frictionless behaviour. This is a soft migration — nothing breaks until drag values are populated.
- **Integration coverage:** The terminal-velocity equilibrium test (Unit 3) is the critical integration test — it proves thrust + drag interact correctly over multiple frames. The navigator braking test (Unit 5) proves the full navigation→physics→position loop works.
- **Unchanged invariants:** Bullet physics is untouched (bullets don't experience drag). Turret rotation is unchanged. HullCollider collision is unchanged. The damage model is unchanged. Ship rendering is unchanged. The builder's canvas interactions are unchanged (drag modifiers are just data fields on `HullAttributes`, authored alongside armour and mass).

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Drag coefficients produce unflyable ships (too slow, can't reach targets) | Unit 7 content tuning. Start with low drag values and increase. Terminal velocity display in builder gives immediate feedback. |
| AI navigator thrashes under drag (oscillates at destination) | Unit 5 acceptance criterion: no oscillation. Closed-form stopping distance should prevent this; if not, add a dead-zone around the destination. |
| Smooth angular interpolation produces unexpected behaviour at exact 0° and 90° boundaries | cos/sin are continuous at these points. Test with ships moving exactly forward and exactly lateral. |
| `PolygonArea` not accessible from `ShipStatsCore` in game-core/api | Extract or duplicate the shoelace formula (~10 lines). Resolved during Unit 2. |
| Existing saved designs (from builder) load with drag modifiers defaulting to 1.0 | Intentional. Default modifiers produce non-zero drag when combined with hull area. Ships will fly differently but won't crash. |
| Drag math introduces per-frame SceneOffset allocations | Use raw floats for the angle/magnitude computation; only construct the final drag force SceneOffset once. See `docs/ai_kubriko_constraints.md`. |

## Documentation / Operational Notes

- **Update `docs/ship.md`** to describe the atmospheric drag model, terminal velocity, and how hull shape affects movement.
- **Update `docs/ai_kubriko_constraints.md`** if the drag computation path reveals new hot-path patterns worth documenting.
- **Post-Deploy Monitoring & Validation:** No additional operational monitoring required. Pre-release prototype; success signal is "combat feels naval" via developer playtest.

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md](../brainstorms/2026-04-10-atmospheric-movement-requirements.md) — Slice A requirements R1–R7, R22–R23, R27–R30.
- **Phase 3 plan (prerequisite, completed):** [docs/plans/2026-04-11-001-feat-ship-builder-phase3-game-integration-plan.md](2026-04-11-001-feat-ship-builder-phase3-game-integration-plan.md)
- **Key physics file:** `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt`
- **Key navigator file:** `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt`
- **Drag math reference:** stopping distance under quadratic drag: `x = (m/2k)·ln((thrust+k·v₀²)/thrust)`. Inverse: `v₀ = sqrt((thrust/k)·(exp(2k·x/m) - 1))`. Derivation: from `m·v·dv/dx = -(thrust + k·v²)`, integrate with substitution `u = thrust + k·v²`.
