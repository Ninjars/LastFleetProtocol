---
title: "feat: Atmospheric Movement Slice B — Keels, lift gate, and lift-failure death"
type: feat
status: active
date: 2026-04-16
deepened: 2026-04-17
origin: docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md
---

# feat: Atmospheric Movement Slice B — Keels, Lift Gate, and Lift-Failure Death

## Overview

Slice A made hull shape drive movement. Slice B makes the **Keel** — a new hull-part type — drive ship *class* and lift budget. Every ship must carry exactly one Keel, total part mass must be ≤ total Keel lift (enforced at two gates), and destroying a Keel's HP triggers a new `LIFT_FAILED` lifecycle state: controls disengage, the ship drifts under drag, and it despawns after a short window. Destruction cause (hull vs lift) is distinguished in the game's output so combat outcomes remain legible.

This is Slice B only — wind, Lift Modules, power/heat, effective-mass softening, salvage, polygon-derived MoI, and a visible combat-log UI are all deferred to separate brainstorms.

## Problem Frame

Slice A proved that hull shape drives movement feel (per-axis drag from polygon geometry + per-piece modifiers). But the third axis of ship differentiation — class identity and the trade-off between mass (firepower, armour) and lift (mobility ceiling) — doesn't exist yet. A fighter feels like a cruiser feels like a bomber, because lift is free.

The brainstorm's fix is the Keel as a class-defining, lift-providing hull piece, enforced exactly-one-per-ship by construction, with two consequences: build-time unflightworthy designs are blocked, and runtime Keel destruction is a *distinct* death condition from HP destruction — the ship drifts, the combat-outcome record tags the cause, and playtesters can see whether a match was won by out-shooting or out-engineering an opponent. (see origin: `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`)

Slice A has shipped (merge `02d1950` on `main`). Its data structures (`HullAttributes` drag modifiers, `MovementConfig` drag coefficients, `ShipStats` terminal velocities, turn-rate rotation) are the foundation Slice B builds on.

## Requirements Trace

From the origin doc (Slice B only):

- **R8.** Introduce Keel as a new hull-part item type. Keels participate in hull geometry and drag derivation like any other hull piece but live in their own parts-panel category and carry a lift value.
- **R9.** Every ship has exactly one Keel. Enforced by construction via a mandatory Keel-picker first step when starting a new design. The chosen Keel becomes the first placed hull piece (at origin), before the canvas is interactable. No second-Keel path exists.
- **R10.** The Keel defines the ship's class (fighter, frigate, cruiser, etc.) and provides the ship's total lift value.
- **R11.** Keels are distinct `ItemDefinition` instances in a new Keel category. Data structure is compatible with a future unlock/progression system without committing to it (no `tier` or `prerequisites` field).
- **R14.** Total ship mass ≤ total Keel lift. Lift consumption is identical to mass (one budget). Failing this check renders the ship unflightworthy and blocks it from combat.
- **R17.** Keel is damageable; HP tracked via a new `KEEL` entry in `InternalSystemType`. Damage routed to the Keel subsystem via arc-based routing (Keel becomes the side-arc primary — see Key Decision 4). The brainstorm's "damage intersects the Keel's hull polygon" language is deferred: polygon-based spatial damage targeting is part of a later damage-model slice that will add per-internal-module geometry and penetration simulation. The Slice B interpretation still satisfies R17's spirit — Keel has HP, takes damage from combat, and is destroyable — while avoiding a routing refactor that the damage-model slice will supersede.
- **R19.** When the Keel's HP reaches zero, the ship enters a `LIFT_FAILED` lifecycle state: controls disengage, momentum continues under drag, ship is non-participating. After a short window (~3s, tuned in playtest), the ship despawns. For combat outcome purposes, a `LIFT_FAILED` ship is counted as destroyed at the moment of Keel failure, not at despawn. The destruction cause (`LIFT_FAILURE` vs `HULL`) is preserved in the destruction callback and surfaced in the game's output stream for log legibility.
- **R20.** The ship builder surfaces lift capacity vs mass as a live stat in the stats panel, updating as parts are added/removed.
- **R21.** The ship builder displays a Flightworthiness Indicator in the header of the stats panel. States: `Flightworthy` (green), `No Keel` (red, pre-first-step only — cannot occur once the mandatory Keel-picker commits), `Mass exceeds lift` (red, with current mass/lift values).
- **R24.** Flightworthiness is enforced at the combat-load gate: `GameStateManager` recomputes `isFlightworthy` from each design at spawn and refuses to load an unflightworthy ship. The brainstorm's R24(a) ("builder prevents saving as ready for combat") is intentionally **not** implemented as a separate save path in Slice B — no Slice B consumer reads a persisted "ready" marker, so the combat-load gate is the sole authoritative enforcement point. The builder-side signal for R24's intent is the Flightworthiness Indicator (R21) and the live Lift/Mass readout (R20), which make the failure state obvious without adding a ceremonial save flow. A future combat-selection UI may revisit this if it needs a persisted ready-state flag.

## Scope Boundaries

- **Wind, Lift Modules, power, and heat remain deferred.** No plumbing for any of these in Slice B.
- **No Keel progression / unlock system.** Keels are distinct `ItemDefinition` records. No `tier`, `prerequisites`, or gating scaffolding.
- **No effective-mass soft-lift dial.** R14 is a hard gate only. Excess lift does not translate to extra mobility.
- **No salvage tracking.** Lift-killed ships are destroyed with a distinct cause tag and nothing else is persisted.
- **No visible combat-log UI.** Destruction-cause plumbing exists through to `println`, but no on-screen combat log is added. A future feature may surface it.
- **No polygon-derived moment of inertia.** Slice A used mass-as-MoI stand-in; Slice B does not touch angular dynamics.
- **No multi-hull constraints on Keel placement.** The Keel is placed at origin as the first step and the user may reposition it anywhere like any other hull piece. No "Keel must be central" or "Keel must be inside another hull" rule.
- **Polygon-based damage targeting of internal systems is deferred.** A future damage-model slice will introduce per-internal-module geometry, impact-location routing, and penetration simulation — which will let the Keel be targeted by hits to its polygon specifically. Slice B uses arc-based routing only (Keel is the side-arc primary). This is a scope simplification from the brainstorm's R17 language.
- **No authoring bounds on Keel drag modifiers or lift values.** A player can author a Keel with high lift and tiny drag modifiers, producing a degenerate "extreme speed" build. This is intentional progression space for Slice B — the brainstorm's Keel concept is explicitly about Keel choice being a meaningful build axis, which implies designable trade-offs. If playtest reveals an obvious dominant strategy that kills variety, a future brainstorm can introduce authoring clamps or a points-budget system. No preemptive clamping in this slice.
- **No existing-design migration.** `ShipDesign.formatVersion` bumps 2 → 3. Saved v2 designs are invalidated; prototype stance (consistent with Slice A's R25 "replace wholesale").
- **Keel-specific drag modifiers are not a new concept.** The Keel's `HullAttributes` carries the same per-axis drag modifier fields every hull piece has; the Keel's shape contributes to drag via the same R29 aggregation as every other placed hull piece.

## Context & Research

### Relevant Code and Patterns

**Type system (game-core/api):**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt` — `ItemType` enum + `ItemAttributes` sealed interface. Existing `HullAttributes` is the closest analogue for `KeelAttributes` (same armour + drag-modifier fields plus a `lift` float and a `shipClass` string).
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` — `formatVersion: Int = 2`; `PlacedHullPiece` is the existing placement type to extend (or mirror) for Keels.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/InternalSystemSpec.kt` — `InternalSystemType` enum. Add `KEEL`.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt` — central `calculateShipStats` aggregator. Add `totalLift` and `isFlightworthy` fields to `ShipStats`; extend the aggregator to read from the placed Keel.

**Conversion:**
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt` — `convertShipDesign` builds `ShipConfig` from `ShipDesign`. Extend it to (a) require exactly one Keel, (b) emit a `KEEL` `InternalSystemSpec` derived from Keel's `maxHp`, (c) ensure the Keel's polygon contributes to drag aggregation exactly like other hull pieces. `LEGACY_MODULE_HP` already uses `InternalSystemType` as its key and will be extended.

**Damage routing (game/impl):**
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouter.kt` — arc-based primary routing. Slice B adds a polygon-intersection pre-check: if the impact point is inside any Keel `HullCollider`, route to `KEEL`; otherwise fall through to existing arc logic. Overflow still cascades to other systems.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/InternalSystem.kt` — per-system HP and disabled/destroyed flags. No changes needed; `KEEL` becomes a fourth instance.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/ShipSystems.kt` — add `isKeelDestroyed()` convenience, following the existing `isReactorDestroyed()` pattern.

**Ship lifecycle (game/impl):**
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — currently uses `var isDestroyed: Boolean` plus `onDestroyedCallback: ((Ship) -> Unit)?`. Replace with a three-state `lifecycle: ShipLifecycle` (Active / LiftFailed(remainingMs) / Destroyed) and extend the callback to `(Ship, DestructionCause) -> Unit`. `checkDestruction()` becomes `updateLifecycle(dtMs)` that handles both transitions and the drift countdown.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — consumes the callback and closes out the match; extends to log cause and spawn-gate unflightworthy configs.

**Builder UX:**
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderState.kt` — add `EditorMode.PickingKeel` as a new sealed variant that runs before `EditingShip`.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderScreen.kt` — right-panel composition already switches on `EditorMode`; add a third branch that shows the Keel-picker panel and nothing else (canvas hidden or overlay-disabled during picking).
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/ItemAttributesPanel.kt` — existing `HullAttributesContent` is the template for a new `KeelAttributesContent` (armour + drag + mass + lift + ship-class name).
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/StatsPanel.kt` — add a header row above the existing mass/thrust/terminal-velocity rows for the flightworthiness indicator, plus a lift/mass readout near the mass row.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` — add a disabled-state for the Keel category post-first-step; reuses the existing category-section pattern.
- `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` — `autoSave()` currently saves on every change; extend with a notion of "ready for combat" that gates on `stats.isFlightworthy`. WIP save path stays permissive.

**Content:**
- `components/game-core/api/src/commonMain/composeResources/files/default_ships/*.json` — four bundled ships. Each needs a Keel `ItemDefinition` in `itemDefinitions` and a `placedKeel` (or equivalent) placement, with the old `placedHulls` trimmed to non-Keel pieces. Ships' mass budgets need to fit under Keel lift values — content-tuning decision during the content unit.

### Institutional Learnings

- Slice A plan `docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md` deliberately deferred the format bump to Slice B and left `HullAttributes.{forward,lateral,reverse}DragModifier` with defaults of 1.0 so existing designs remained loadable. Slice B's format bump is the natural cut-over point.
- `docs/ai_kubriko_constraints.md` flags per-frame `SceneOffset` allocations. The LIFT_FAILED drift countdown must not allocate per frame (store the remaining-ms float on `Ship`, decrement in place).
- Slice A's plan learned (via commit `e95a5af`) that oscillation fixes come from removing velocity-correction loops rather than tuning them. For Slice B, `LIFT_FAILED` physics should *disable the navigator entirely* and leave drag alone — not try to "stop thrusting but keep some control".

### External References

No external research needed. This is a domain-specific mechanical change; the patterns to follow are all local (the existing `InternalSystemType`, `ArcDamageRouter`, and `EditorMode` patterns). The math is trivial (one sum, one comparison, one countdown).

## Key Technical Decisions

1. **`KeelAttributes` is a sibling of `HullAttributes`, not a subtype — but both implement `ExternalPartAttributes`.**
   Keels are functionally hull pieces for drag/collision purposes, but the builder category, the mandatory-one constraint, and the `lift` + `shipClass` + `maxHp` fields warrant a distinct `ItemAttributes` variant. Shared exterior-part semantics (armour, per-axis drag modifiers, vertices contributing to the ship's outer silhouette) are expressed via a new `ExternalPartAttributes` interface that both `HullAttributes` and `KeelAttributes` implement. Drag aggregation iterates a single combined list of exterior parts read via the interface — no cast-fallthrough bug, one mental model for drag, and a natural extension point if future hull-like part types (e.g., wings, stabilisers) emerge. The sibling structure is load-bearing beyond the extra fields: the `ItemType.KEEL` enum value drives the builder's parts-panel category split (own "Create Keel" action, own picker catalogue in Unit 5, disabled post-commit), and type-driven dispatch is what makes "exactly one Keel" trivially expressible at the `PlacedKeel?` layer. Collapsing to `HullAttributes` with nullable `lift` / `shipClass` / `maxHp` fields would trade polymorphism for property-introspection filtering, which is fragile as categories multiply.

2. **`PlacedKeel` is a new placement record, not a `PlacedHullPiece` with a flag.**
   Mirrors the `PlacedHull` / `PlacedModule` / `PlacedTurret` separation already in `ShipDesign`. Keeps the "exactly-one" invariant trivially expressible as `placedKeel: PlacedKeel?` (nullable singleton) on `ShipDesign`, rather than a list with a cardinality constraint. The converter can refuse to build a `ShipConfig` when `placedKeel == null`.

3. **Three-state `ShipLifecycle` sealed type replaces the binary `isDestroyed` flag.**
   `Active`, `LiftFailed(remainingMs: Int)`, `Destroyed`. The data class form lets us count down a drift window without a side-channel timer field, and `Destroyed` can carry the cause inline so the callback signature changes once and never again. `isValidTarget()` becomes `lifecycle is Active`.

4. **Keel is routed by arc like every other internal system — it becomes the side-arc primary.**
   `ArcDamageRouter.routeDamage` keeps its existing shape. The arc-to-system map changes from `{forward→BRIDGE, rear→MAIN_ENGINE, sides→REACTOR}` to `{forward→BRIDGE, rear→MAIN_ENGINE, sides→KEEL}` with REACTOR demoted to side-arc overflow. Overflow pools automatically include KEEL because `InternalSystemType.entries` now has four values. This is the minimal R17 interpretation: "Keel is an `InternalSystemType` with arc-based routing." Polygon-based damage targeting of internal systems (including making the Keel separately targetable by impact location) is **deferred to a later damage-model slice** that will introduce per-module geometry and penetration simulation. Trading spatial fidelity for scope: the Keel still has a visible effect on combat (side hits now cause lift failure instead of reactor kills), but we don't pay for a routing refactor that the damage-model slice will supersede.

5. **Single `onLifecycleTransition` hook on `Ship`; subscribers branch on the new state.**
   - Signature: `((Ship, ShipLifecycle) -> Unit)?`. Fires at *every* state change (`Active → LiftFailed`, `Active → Destroyed(HULL)`, `LiftFailed → Destroyed(LIFT_FAILURE)`).
   - `GameStateManager` subscribes once and branches inside: match-tally via `playerShips.none { it.lifecycle is Active } / enemyShips.none { ... }` runs on every transition (gated by `_gameResult.value == null` to avoid re-firing); terminal cleanup (`println` with cause + remove from backing team list) runs only when `newState is ShipLifecycle.Destroyed`.
   - Ship handles its own `actorManager.remove(this)` on the `Destroyed` transition — a self-cleanup symmetric with `actorManager.add` at creation, not something the subscriber needs to orchestrate.
   - R19's "counted as destroyed at Keel failure" still holds: the tally fires at the `LiftFailed` transition even though the actor remains in the scene for the drift window.
   - **Rationale for a single hook:** an earlier design split observer-vs-terminal concerns into two callbacks (`onLifecycleTransition` + `onDestroyedCallback`). During execution of Unit 3 this was refactored back to one: the "two different moments" argument collapses when the subscriber can simply branch on the sealed `ShipLifecycle` value, and a single hook makes the "cleanup only at Destroyed" invariant visible in one place instead of split across two method signatures. Two independent subscription points invited drift; `when (newState) { is Destroyed -> cleanup; else -> observe }` is the idiomatic Kotlin shape.

6. **`DestructionCause` is an enum on the `Destroyed` state, not a separate callback.**
   `HULL` and `LIFT_FAILURE` cover R19 legibility. `GameStateManager` extracts the cause via `when (newState) { is ShipLifecycle.Destroyed -> newState.cause; ... }` inside its single hook and logs `"[combat] $teamId ship destroyed: $cause"` via `println`. A visible combat log UI is deferred; the plumbing needed to support one is already in place once the cause flows through.

7. **`LIFT_FAILED` disables the navigator and the thrust path, then lets drag finish the ship.**
   `Ship.update` skips `navigator.navigate()` and `applyThrust` when `lifecycle is LiftFailed`; `applyDrag` and `integrate` still run. This produces the natural "drift to rest" feel without new physics code and without a half-working control surface during the drift window. Turrets stop firing (they check `lifecycle is Active` via `isValidTarget`).

8. **`ShipDesign.formatVersion` bumps 2 → 3; no migration.**
   Consistent with Slice A's R25 posture (replace wholesale). The existing single format-check in `ShipDesignSerializationTest` updates; no backward-compat shim. Bundled ships get rewritten as part of Unit 7.

9. **Mandatory Keel-picker is a new sealed `EditorMode` variant, not a modal or a flag on `EditingShip`.**
   When a design is new (no Keel placed yet), `EditorMode.PickingKeel` is the starting state. The builder's right panel shows a Keel-picker list; the canvas and parts panel are hidden or disabled. Picking a Keel commits it to `placedKeel` at origin and transitions to `EditingShip`. This matches the existing `EditingShip` / `CreatingItem` pattern (panel swap) rather than introducing modal infrastructure. Modelling as a sealed variant rather than a flag on `EditingShip` also makes invalid transitions unrepresentable — `PickingKeel` can't enter `CreatingItem`, can't save, can't load; the compiler enforces this rather than runtime `if (awaitingKeelPick)` guards scattered across the intent-reducer.

10. **Flightworthiness is a derived read-only flag on `ShipStats`, not a persisted flag.**
   `isFlightworthy = totalLift > 0f && totalMass <= totalLift`. Computed by `calculateShipStats` on every change. The builder reads it; the combat-load path re-computes from the design's `ShipStats` at spawn. No "flightworthy" bit stored on the design.

## Open Questions

### Resolved During Planning

- **R9 UX shape?** → Inline `EditorMode.PickingKeel` with a right-panel picker list. Canvas hidden during picking. Matches existing panel-swap pattern. (Brainstorm punted to implementation; resolved here because it affects sequencing and state-machine shape.)
- **R27 multi-hull Keel placement constraint?** → None. Keel auto-places at origin during the picker step; user may reposition it anywhere. No "must be central / internal / exposed" rule.
- **R17 damage routing: arc or polygon?** → Arc-based only. Keel becomes the side-arc primary (demoting REACTOR to side-arc overflow). Polygon-based spatial routing deferred to a later damage-model slice. This is a scope simplification from the brainstorm; the Keel is still a legitimate combat target because side hits now cause lift failure instead of reactor kills.
- **R19 destruction-cause log?** → Enum on the destruction callback; `GameStateManager` emits `println`. No visible combat log UI.
- **R19 drift window?** → 3s default; stored as a `const` in `Ship.kt`; easy to tune during playtest.
- **`KeelAttributes` shape vs `HullAttributes` reuse?** → Separate `@SerialName("keel")` variant on `ItemAttributes`. Shares the armour, drag modifier, and mass fields; adds `lift: Float` and `shipClass: String`. Slightly redundant but avoids nullable fields on `HullAttributes`.
- **`PlacedKeel` on `ShipDesign`: list of zero-or-one, or nullable singleton?** → Nullable singleton (`placedKeel: PlacedKeel? = null`). Expresses "exactly-one or none" directly; converter refuses a null keel.
- **`ShipLifecycle`: sealed data classes or enum + side field?** → Sealed (`Active`, `LiftFailed(remainingMs)`, `Destroyed(cause)`). Avoids a side-channel timer and keeps the cause inline.
- **Format-version bump target?** → 3. No migration code. Bundled ship JSONs get rewritten in Unit 7.
- **Ship builder save gate when unflightworthy?** → No separate save gate in Slice B. All saves are WIP (existing autoSave). Unit 4's combat-load gate is the sole authoritative check. Avoids a ceremonial "ready" flag that no Slice B consumer reads. Builder-side visibility is provided by the Flightworthiness Indicator and Lift/Mass readout, not by save-button state.

### Deferred to Implementation

- **Exact Keel catalogue and content values.** The plan commits to at least one Keel per archetype (fighter / frigate / cruiser / bomber) but the lift/armour/HP numbers are feel-driven and iterated in the content unit.
- **Richer drift visual effects.** Slice B includes a minimum viable signal (half-alpha desaturation of the ship polygon during `LiftFailed`, Unit 3). Richer effects — smoke particles, heading-indicator fadeout, turret slouch, sparks at the Keel-failure moment — remain deferred. Add if playtest shows the minimum signal is insufficient.
- **Flightworthiness indicator visual style.** Colour tokens and layout follow Material 3 theme conventions; exact pixel layout decided during Unit 6.

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

**Data model additions (game-core/api):**

```
ItemType:        HULL | MODULE | TURRET | KEEL
ItemAttributes:  HullAttributes | ModuleAttributes | TurretAttributes |
                 KeelAttributes(armour, mass, drag modifiers, lift, shipClass)
ShipDesign:      ... + placedKeel: PlacedKeel? (nullable singleton, formatVersion=3)
ShipStats:       ... + totalLift: Float + isFlightworthy: Boolean
InternalSystemType: REACTOR | MAIN_ENGINE | BRIDGE | KEEL
```

**Runtime state machine (`Ship.lifecycle`):**

```
               Keel HP = 0               driftMs counts down to 0
Active ──────────────────────> LiftFailed ──────────────────────> Destroyed(LIFT_FAILURE)
  │         [T][M]                 [T]                                   ▲  [T][M][C]
  │                                                                      │
  │ Reactor HP = 0 [T][M][C]                                             │
  └──────────────────────────────────────────────────────────────────────┘
                                                             Destroyed(HULL)
```

- `[T]` = `onLifecycleTransition` hook fires. Single hook; subscribers branch on the new state.
- `[M]` = match-result re-tally branch runs inside the hook. Fires at `LiftFailed` entry AND at `Destroyed(HULL)` entry — both are R19 "moment of destruction" events. Gated by `_gameResult.value == null` so it doesn't re-fire at the terminal `LiftFailed → Destroyed(LIFT_FAILURE)` transition.
- `[C]` = terminal-cleanup branch runs inside the hook: cause-tagged `println` + remove from `GameStateManager`'s backing team list. Only runs when `newState is ShipLifecycle.Destroyed`. Ship also self-removes from `actorManager` on `Destroyed` — that's Ship-internal, not driven by the hook.

`LiftFailed` disables the navigator, turrets, and thrust application; drag and integration keep running, so the ship drifts. During drift, the ship remains in `GameStateManager`'s backing team list but no longer counts as Active — so the match can have already resolved.

**Damage routing (`ArcDamageRouter.routeDamage`):**

```
arc determined by impact direction (unchanged):
  forward 90° arc → BRIDGE           (primary); overflow = shuffled {KEEL, REACTOR, MAIN_ENGINE}
  rear 90° arc    → MAIN_ENGINE      (primary); overflow = shuffled {KEEL, REACTOR, BRIDGE}
  side arcs       → KEEL             (primary); overflow = shuffled {REACTOR, BRIDGE, MAIN_ENGINE}
```

The only change from Slice A is the side-arc primary (REACTOR → KEEL) and KEEL joining the overflow pool in the other two arcs. No signature change to `routeDamage`; no new per-hit polygon test; no `isKeel` tag on colliders. Spatial fidelity (hitting the Keel's polygon specifically) is deferred to a later damage-model slice.

**Builder state machine (`EditorMode`):**

```
new design → PickingKeel ──(Keel chosen)──> EditingShip
                              (auto-place at origin, Keel category disabled in parts panel)

load existing design with placedKeel ≠ null → EditingShip directly
load existing design with placedKeel == null → PickingKeel (corrupt/pre-v3 recovery)
```

## Implementation Units

### Phase B1 — Data Model Foundation

- [x] **Unit 1: Type system extension for Keels**

  **Goal:** Extend the shared type system so Keels have first-class representation: new `ItemType` value, new `ItemAttributes` variant, new `InternalSystemType` enum value, new `PlacedKeel` placement record, `placedKeel: PlacedKeel?` on `ShipDesign`, new `ShipStats` fields (`totalLift`, `isFlightworthy`), and a bumped `formatVersion`.

  **Requirements:** R8, R10, R11, R17

  **Dependencies:** None.

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt` — add `ItemType.KEEL`; introduce `ExternalPartAttributes` interface carrying `armour: SerializableArmourStats`, `forwardDragModifier: Float`, `lateralDragModifier: Float`, `reverseDragModifier: Float` (the fields drag aggregation needs); make `HullAttributes` and the new `KeelAttributes` both implement it; add `ItemAttributes.KeelAttributes(@SerialName("keel") armour, sizeCategory, mass, drag modifiers, maxHp, lift, shipClass)`; extend the `itemType` `when` branch.
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt` — add `PlacedKeel` record (same shape as `PlacedHullPiece`); add `placedKeel: PlacedKeel? = null` to `ShipDesign`; bump `formatVersion` default to 3.
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/InternalSystemSpec.kt` — add `KEEL` to `InternalSystemType` enum.
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt` — add `totalLift: Float` and `isFlightworthy: Boolean` to `ShipStats`.
  - Modify: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignSerializationTest.kt` — update `formatVersion` assertion to 3; add round-trip coverage for `KeelAttributes` and `PlacedKeel`.
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignSerializationTest.kt`

  **Approach:**
  - All new fields have defaults so existing construction sites compile. `calculateShipStats` computation of `totalLift` / `isFlightworthy` is left at `0f` / `false` in Unit 1; Unit 2 populates them.
  - `ExternalPartAttributes` is a sealed-hierarchy-compatible interface (not `sealed` itself — it sits orthogonally to `ItemAttributes`). Future exterior part types (wings, stabilisers) can implement it without a cascading refactor of every drag consumer.
  - Keep `KeelAttributes` separate from `HullAttributes` to avoid nullable lift/class fields polluting hull pieces. Shared fields come from `ExternalPartAttributes`, not inheritance.
  - `KeelAttributes` carries `maxHp: Float` (authored per-Keel, not a legacy constant) — Unit 2's converter reads this when emitting the `KEEL` `InternalSystemSpec`.
  - `PlacedKeel` does not need `parentHullId`: it IS a hull piece for placement purposes.

  **Patterns to follow:**
  - Existing `HullAttributes` polymorphic serialization with `@SerialName`.
  - Existing `PlacedHullPiece` field layout (id, itemDefinitionId, position, rotation, mirrorX/Y).

  **Test scenarios:**
  - Happy path: `ShipDesign(formatVersion = 3, placedKeel = PlacedKeel(...))` round-trips with `Json.encodeToString` / `decodeFromString` without data loss.
  - Happy path: A v3 ship with `placedKeel = null` round-trips (represents a design in `PickingKeel` state — Unit 5 consumes this).
  - Happy path: `ItemDefinition` containing a `KeelAttributes` round-trips and produces `itemType == ItemType.KEEL`.
  - Edge case: v3 JSON without a `placedKeel` field (omitted) deserializes with `placedKeel = null`.
  - Edge case: `KeelAttributes` with lift of 0 is representable (for test fixtures).
  - Edge case: `InternalSystemType.valueOf("KEEL")` succeeds; `InternalSystemType.entries.size == 4`.

  **Verification:**
  - All existing tests pass after the `formatVersion` assertion is updated.
  - Full build compiles; `when`-exhaustiveness warnings for `InternalSystemType` or `ItemAttributes` are resolved.

---

- [x] **Unit 2: Lift/flightworthy computation + Keel as hull piece in conversion**

  **Goal:** Teach `calculateShipStats` and `convertShipDesign` about the Keel. Stats compute `totalLift` from the placed Keel, compute `isFlightworthy = totalLift > 0 && totalMass <= totalLift`, and include the Keel polygon in drag aggregation. The converter emits a `KEEL` `InternalSystemSpec`, registers the Keel's hull polygon for collision/drag alongside the other hull pieces, and refuses to produce a `ShipConfig` when `placedKeel == null`.

  **Requirements:** R10, R14, R17, R27 (Slice A carry-over: Keel is a hull piece for drag purposes)

  **Dependencies:** Unit 1.

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt` — extend `calculateShipStats` signature to accept a nullable `placedKeel: PlacedKeel?`; fold Keel mass into `totalMass`; compute `totalLift`; compute `isFlightworthy`; fold Keel polygon into drag-coefficient aggregation.
  - Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt` — require `design.placedKeel != null` or return `Result.failure`; build a `KEEL` `InternalSystemSpec(maxHp = keelAttrs.maxHp, ...)`; include the Keel polygon in `HullDefinition` emission so `HullCollider` child-actor creation covers it; pass `placedKeel` through to its own `calculateShipStats` call. **Do not** add `LEGACY_MODULE_HP[InternalSystemType.KEEL]` — converter failure is the gate; a legacy fallback for KEEL would contradict it.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculator.kt` — the real indirection behind `ShipBuilderVM`'s live recompute. Extend `calculateStats(state)` to read `state.placedKeel` and pass it through.
  - Modify (if needed): `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` — only if the VM constructs a `calculateShipStats` call directly rather than via `ShipStatsCalculator`; verify during implementation.
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCoreTest.kt` — ~11 existing `calculateShipStats(...)` callsites need updating (add `placedKeel = null` or a test fixture).
  - Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverterTest.kt`
  - Test: `features/ship-builder/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/stats/ShipStatsCalculatorTest.kt` — existing fixtures need updating for the new parameter.

  **Approach:**
  - `totalLift = keelAttrs?.lift ?: 0f`; `isFlightworthy = totalLift > 0f && totalMass <= totalLift`. If no keel placed, `isFlightworthy = false`.
  - **Keel's drag contribution via `ExternalPartAttributes`:** the drag-coefficient aggregation loop iterates a single `List<Pair<PlacementRecord, ExternalPartAttributes>>` built by resolving each `placedHull` (attrs cast as `HullAttributes`, which implements `ExternalPartAttributes`) and the `placedKeel` if present (attrs cast as `KeelAttributes`, which also implements it). The loop reads `forwardDragModifier` / `lateralDragModifier` / `reverseDragModifier` through the interface — no cast-fallthrough `continue` branch, one iteration, one mental model. Mass is still accounted separately per attribute subtype (`HullAttributes.mass` + `armour`-derived mass vs `KeelAttributes.mass`), because mass-accounting rules diverge.
  - Converter emits `KEEL` `InternalSystemSpec` with `maxHp` from `KeelAttributes.maxHp` (added in Unit 1). A Keel's HP is authored per-Keel, not a legacy constant.

  **Patterns to follow:**
  - Existing `ShipDesignConverter.convertShipDesign` hull iteration and `InternalSystemSpec` emission for the three existing systems.
  - Existing `calculateShipStats` drag-coefficient aggregation loop.

  **Test scenarios:**
  - Happy path: design with `placedKeel.lift = 100f` and total mass 80f → `isFlightworthy == true`, `totalLift == 100f`.
  - Happy path: design with total mass 120f and lift 100f → `isFlightworthy == false`.
  - Happy path: design with `placedKeel == null` → `isFlightworthy == false`, `totalLift == 0f`.
  - Happy path: converter produces a `ShipConfig` whose `internalSystems` includes `InternalSystemType.KEEL` with `maxHp` from the Keel's attributes.
  - Happy path: Keel polygon contributes to per-axis drag coefficients (a ship with a large Keel has higher drag than one with a small Keel, all else equal).
  - Edge case: `convertShipDesign(design with placedKeel = null)` returns `Result.failure` with a descriptive error.
  - Edge case: A design with the minimum representable Keel (tiny polygon, lift = 1.0, mass such that totalMass ≤ lift) converts successfully.
  - Integration: Calling `convertShipDesign` on a `ShipDesign` constructed from Unit 1's serialization round-trip produces a `ShipConfig` with `KEEL` slotted into `internalSystems`.

  **Verification:**
  - `ShipStatsCoreTest` and `ShipDesignConverterTest` pass with the new cases.
  - Builder's live stats panel is able to display `isFlightworthy` + `totalLift` for a placeholder Keel (panel wiring is Unit 5; this unit just confirms the computation is reachable).

---

### Phase B2 — Runtime: Damage Routing and Lifecycle

- [x] **Unit 3: Keel arc routing and LIFT_FAILED lifecycle**

  **Goal:** Promote `InternalSystemType.KEEL` to the side-arc primary in `ArcDamageRouter` (demoting REACTOR to side-arc overflow). Introduce a three-state `ShipLifecycle` sealed type (`Active`, `LiftFailed(remainingMs)`, `Destroyed(cause)`) replacing `isDestroyed`. When the Keel's `InternalSystem` hits 0 HP, the ship transitions to `LiftFailed` with a 3-second countdown; navigator, thrust, and turrets disengage; drag and integration continue. When the countdown reaches 0 (or when the reactor is separately destroyed), the ship transitions to `Destroyed(cause)` and fires the destruction callback with the appropriate `DestructionCause`.

  **Requirements:** R17, R19

  **Dependencies:** Unit 2 (runtime has a `KEEL` `InternalSystem` to damage).

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipLifecycle.kt` — sealed interface `ShipLifecycle` with `Active`, `LiftFailed(remainingMs: Int)`, `Destroyed(cause: DestructionCause)`; enum `DestructionCause { HULL, LIFT_FAILURE }`.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` — replace `isDestroyed` with `lifecycle`; rename `checkDestruction` → `updateLifecycle(dtMs)`; replace the old `onDestroyedCallback: ((Ship) -> Unit)?` with a single `onLifecycleTransition: ((Ship, ShipLifecycle) -> Unit)?` (subscribers branch on the new state to separate observer vs terminal concerns); Ship self-removes from `actorManager` on the `Destroyed` transition; gate `isValidTarget()` on `lifecycle is Active`; skip navigator, thrust, AND turret updates from `Ship.update` when `lifecycle !is Active` (ship is the authority on whether its turrets fire — do NOT try to have `Turret` self-check parent, since `Turret` only sees the generic `Parent` interface); in the polygon-fill draw path, apply half-alpha desaturation when `lifecycle is LiftFailed` so the ship visibly dims during drift.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouter.kt` — update the arc-to-system map: side arcs now return `InternalSystemType.KEEL` as primary instead of `REACTOR`. Overflow logic is unchanged (still `InternalSystemType.entries.filter { it != primaryType }.shuffled(random)`) — KEEL automatically joins the overflow pool for forward/rear arcs via the enum extension from Unit 1. No signature change. No polygon intersection. No collider identification.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/ShipSystems.kt` — add `isKeelDestroyed()` companion to the existing `isReactorDestroyed()`.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — wire the single `onLifecycleTransition` hook on ship creation. Inside the hook, re-tally match results via `playerShips.none { it.lifecycle is ShipLifecycle.Active } / enemyShips.none { ... }` (gated by `_gameResult.value == null`) — so victory fires at `LiftFailed` entry, not at drift-end. When `newState is ShipLifecycle.Destroyed`, branch to terminal cleanup: cause-tagged `println` + remove from the backing team list. `LiftFailed` ships remain in the backing team list until the terminal transition — the match-result decision uses the `none { is Active }` filter over the backing list.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/input/InputController.kt` — replace `!ship.isDestroyed` guard with `ship.lifecycle is ShipLifecycle.Active`.
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/debug/DebugVisualiser.kt` — ditto.
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouterTest.kt`
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipLifecycleTest.kt` (new)
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt` — extend (or create) to assert the match-result-at-LiftFailed behaviour against the backing-list tally.

  **Execution note:** Characterization-first. The existing `isDestroyed` binary has well-tested behaviour via `checkDestruction`; capture a test that asserts the current "reactor destroyed → callback fires → ship removed" flow *before* replacing `isDestroyed`. This catches any subtle inversion during the lifecycle refactor.

  **Approach:**
  - `ShipLifecycle` sealed interface keeps `remainingMs` in the `LiftFailed` data class — no side-channel timer field on `Ship`.
  - `updateLifecycle` called once per frame from `Ship.update` after physics integration. Transition order: (a) if `Active` and reactor destroyed → `Destroyed(HULL)`; (b) if `Active` and keel destroyed → `LiftFailed(DRIFT_WINDOW_MS)`; (c) if `LiftFailed`, decrement `remainingMs`; (d) if `LiftFailed` and `remainingMs ≤ 0` → `Destroyed(LIFT_FAILURE)`.
  - **Every transition fires `onLifecycleTransition`.** Subscribers branch on the new state via `when`. `GameStateManager`'s hook runs match-tally on every transition (gated by `_gameResult.value`) and runs the terminal-cleanup branch only when `newState is ShipLifecycle.Destroyed`. R19's "counted as destroyed at Keel failure moment" is satisfied by the re-tally at `LiftFailed` entry; actor cleanup waits for the terminal `Destroyed` transition so the ship is visibly present during drift. Ship removes itself from the actor manager on `Destroyed` — that's Ship-internal, not driven by the hook. See Key Technical Decision 5 for why this is a single hook rather than two.
  - **Reactor-and-keel same-frame precedence:** the `updateLifecycle` transition order above checks reactor first. If both systems hit 0 HP on the same frame (e.g., a single side-arc hit destroys KEEL and its overflow cascade destroys REACTOR), the ship goes `Active → Destroyed(HULL)` directly, skipping `LiftFailed`. `onLifecycleTransition` fires once (for the `Destroyed(HULL)` transition); `onDestroyedCallback` fires once. No drift. Rationale: reactor destruction is final combat death; drift is only interesting when the reactor survives.
  - **Arc router overflow.** The existing `InternalSystemType.entries.filter { it != primaryType }.shuffled(random)` behaviour is preserved. With KEEL joining the enum, it automatically joins the overflow pool for forward and rear arcs (so a big front-arc hit can eventually over-penetrate into the Keel). For side arcs, REACTOR now appears in the overflow. This is the same "simplified damage model" Slice A preserved — full fidelity waits for the damage-model slice.
  - **Turrets gated at the ship level, not self-checking.** `Turret` only sees the generic Kubriko `Parent` interface; it can't reach `Ship.lifecycle`. `Ship.update` is responsible for calling each turret's update only when `lifecycle is Active`. This also sidesteps the Kubriko ActorManager child-update-ordering race (turrets firing a final shot on the frame their parent transitions).
  - `DRIFT_WINDOW_MS = 3000` as a companion `const` in `Ship.kt`. Tuneable in playtest.
  - **Minimum viable drift visual.** Ship's polygon-fill draw path applies a half-alpha desaturation when `lifecycle is LiftFailed`. One branch in the draw routine; no new assets, no particle system. This is the minimum signal needed to satisfy the brainstorm's "visibly drift before despawning" success criterion — richer effects (smoke trail, sparks, heading-indicator fadeout, turret slouch) remain deferred.

  **Patterns to follow:**
  - Existing `ArcDamageRouter.routeDamage` cascade pattern for overflow — unchanged except for the side-arc primary.
  - Existing `isReactorDestroyed()` / `isMainEngineDisabled()` pattern in `ShipSystems`.

  **Test scenarios:**
  - Happy path: forward-arc hit on a ship with intact systems → damage routes to `BRIDGE` (unchanged from Slice A). Overflow pool now includes KEEL.
  - Happy path: rear-arc hit → damage routes to `MAIN_ENGINE` (unchanged). Overflow pool includes KEEL.
  - Happy path: side-arc hit → damage routes to `KEEL` (**changed** — was REACTOR). Overflow pool now includes REACTOR.
  - Happy path: ship's KEEL HP driven to 0 by sustained side-arc fire → `lifecycle` transitions to `LiftFailed(3000)`; `onLifecycleTransition` fires with the new state; `onDestroyedCallback` has *not* yet fired; turrets stop firing (verified by asserting turret update is skipped when parent lifecycle is `LiftFailed`).
  - Happy path: during `LiftFailed`, `Ship.update` still runs `applyDrag` and `integrate` — ship continues moving with decelerating velocity.
  - Happy path: `LiftFailed` `remainingMs` reaches 0 → `lifecycle` becomes `Destroyed(LIFT_FAILURE)`; `onLifecycleTransition` fires; `onDestroyedCallback` fires with `LIFT_FAILURE`; `GameStateManager` removes actor and backing-list entry.
  - Happy path: reactor HP driven to 0 while `Active` (e.g., via forward-arc overflow cascading into REACTOR) → `lifecycle` becomes `Destroyed(HULL)` directly (no `LiftFailed` transition); `onLifecycleTransition` fires once; `onDestroyedCallback` fires with `HULL`; no drift window.
  - Happy path: single massive side-arc hit zeros KEEL *and* (via overflow) zeros REACTOR in the same damage call — `updateLifecycle` at end-of-frame resolves reactor first → `Destroyed(HULL)` directly; `onLifecycleTransition` fires once for the terminal transition only; no transient `LiftFailed` state observed externally.
  - Edge case: overflow damage to an already-zero `KEEL` system correctly cascades to the other three systems (no double-damage, no NaN). Applies when a new hit arrives after Keel has already been destroyed.
  - Edge case: a `LiftFailed` ship is not a valid turret target (`isValidTarget == false`), so other ships stop shooting at it.
  - Integration: `GameStateManager` receives `onLifecycleTransition(ship, LiftFailed(3000))` when the enemy ship's Keel is destroyed → immediately re-tallies via `enemyShips.none { it.lifecycle is Active }` → `_gameResult.value == VICTORY` while the ship's actor is still in the scene drifting.
  - Integration: with `_gameResult.value` already set to VICTORY, the subsequent `onDestroyedCallback` call at drift-end does *not* re-fire the result — it only performs actor cleanup. Tested by asserting `_gameResult.value` is not re-assigned on the second hook.
  - Integration: end-to-end combat — a full update tick with a `LiftFailed` ship produces movement from drag alone with no thrust force accumulating; turret child-actors are skipped by the parent's update.

  **Verification:**
  - `ArcDamageRouterTest` covers the polygon-intersection branch in addition to the existing arc branches.
  - `ShipLifecycleTest` covers all three transitions and the drift-window countdown.
  - Manual desktop run: a ship taking Keel damage visibly drifts for ~3s before despawning; turrets stop firing the moment the Keel fails.
  - `println` output during combat shows `"[combat] enemy ship destroyed: LIFT_FAILURE"` vs `"... : HULL"` distinctly.

---

### Phase B3 — Combat-Load Flightworthiness Gate

- [x] **Unit 4: Combat-load flightworthiness recheck**

  **Goal:** `GameStateManager` refuses to spawn a `Ship` whose design is either structurally invalid (converter `Result.failure`) or unflightworthy (`!isFlightworthy`). The check is independent of the builder-side gate so that any save/share/import path added later remains safe. Also removes the existing `.getOrThrow()` call that would crash the scene setup if any bundled design fails to convert.

  **Requirements:** R24

  **Dependencies:** Unit 2 (`isFlightworthy` is computable and converter returns `Result.failure` on null Keel).

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — replace the existing `convertShipDesign(...).getOrThrow()` call in `startDemoScene` with a `Result`-destructuring per-design loop: on `failure`, `println` the error and skip; on `success`, run the flightworthiness gate (`calculateShipStats` on the design, check `isFlightworthy`); skip unflightworthy with a `println`; only spawn designs that pass both. After the loop, if either team has zero spawned ships, fire the corresponding `_gameResult` fallback.
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt` (new or extension of existing, depending on what already exists)

  **Approach:**
  - **Two gate paths, identical downstream handling.** Both conversion-failure and flightworthy-failure emit a descriptive `println`, skip `createShip`, and fall through to the post-loop tally.
    - Conversion failure (Unit 2's `Result.failure` path — typically null Keel, unknown module `systemType`, or no hull pieces): `println("[combat] design '${design.name}' failed conversion: ${error.message}")`.
    - Flightworthy failure (over-mass): `println("[combat] refused to spawn unflightworthy design: '${design.name}' (mass=$totalMass, lift=$totalLift)")`.
  - **Compute the flightworthiness check from the `ShipDesign`, not the already-converted `ShipConfig`.** `calculateShipStats(design.placedHulls, design.placedModules, design.placedTurrets, design.placedKeel, resolveItem)` is the authoritative computation. The converter runs regardless but its output is discarded for an unflightworthy design.
  - **Post-loop tally.** After all designs have been processed, if `playerShips.isEmpty()` set `_gameResult = DEFEAT`; if `enemyShips.isEmpty()` set `_gameResult = VICTORY`. If both teams successfully spawn, the normal lifecycle-driven tally (from Unit 3's `onLifecycleTransition` hook) takes over.
  - For Slice B, all four bundled designs will both convert cleanly and be flightworthy after Units 7–8. The refusal paths exist for runtime safety, not as common cases.

  **Patterns to follow:**
  - Existing `GameStateManager.startDemoScene` spawn loop (replace its `.getOrThrow()` calls).
  - Unit 3's `onLifecycleTransition` wiring (tally behaviour is consistent between spawn-time refusal and runtime destruction).

  **Test scenarios:**
  - Happy path: four flightworthy, convertible designs spawn normally; `playerShips.size == 2`, `enemyShips.size == 3` (matching the current scene composition).
  - Edge case: a design with `placedKeel == null` → converter returns `failure` → `println` emitted → spawn skipped → team list does not grow.
  - Edge case: a design with mass > lift → conversion succeeds → flightworthy check fails → `println` emitted → spawn skipped.
  - Edge case: all enemy designs fail one gate or the other → `enemyShips.isEmpty()` after loop → `_gameResult.value == VICTORY`.
  - Edge case: all player designs fail one gate or the other → `_gameResult.value == DEFEAT`.
  - Edge case: no design triggers `getOrThrow()` or any other unhandled exception even when every bundled design is malformed — the method returns normally with the appropriate `_gameResult`.
  - Integration: a design constructed in-memory with mass 120 and a Keel lift of 100 is rejected at the flightworthy gate (not the converter gate), verifying the two paths are distinguished.

  **Verification:**
  - `GameStateManager` tests assert both refusal paths and the consequent match-result fallback.
  - Tampering with a bundled JSON to make it unflightworthy produces a console refusal and a sensible match result rather than a crash.
  - Grepping the repo for `.getOrThrow()` in `GameStateManager.kt` returns no results after this unit lands.

---

### Phase B4 — Builder UX

- [x] **Unit 5: Mandatory Keel-picker first step**

  **Goal:** When a new design is started (or when a loaded design has `placedKeel == null`), the builder enters `EditorMode.PickingKeel`: the canvas and the parts panel are hidden/disabled, and the right panel shows a Keel picker. Selecting a Keel commits it at origin as `placedKeel` and transitions to `EditorMode.EditingShip`. Post-commit, the Keel category in the parts panel is disabled — no second-Keel path.

  **Requirements:** R9

  **Dependencies:** Unit 1 (type system has `PlacedKeel`); Unit 8 (bundled Keels exist to pick — can land in parallel if a placeholder hard-coded Keel is used temporarily).

  **Files:**
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderState.kt` — add `EditorMode.PickingKeel` sealed variant.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderScreen.kt` — add a third branch in the right-panel `when` that renders a `KeelPickerPanel`; hide/disable the canvas and parts panel while in this mode.
  - Create: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/KeelPickerPanel.kt` — list of available Keel `ItemDefinition`s with name, class, and lift shown; selection button commits.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderVM.kt` — initial `EditorMode` is `PickingKeel` when `placedKeel == null`; on selection, place a `PlacedKeel` at origin and transition; load path routes through `PickingKeel` if loaded design has no Keel.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` — add a Keel category section that is visually disabled once a Keel is placed (prevents the "create a second Keel" path).
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderInputReducer.kt` (or wherever the `DeleteItem` intent is handled) — guard Keel deletion: if the selected placement is the `placedKeel`, no-op the delete. Repositioning, mirroring, and rotating the Keel are still allowed.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/entities/ShipBuilderIntent.kt` — add `CancelKeelPick` intent (reuses the existing back-to-landing navigation side-effect).
  - Modify: `components/design/src/commonMain/composeResources/values/strings.xml` and `components/design/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/ui/resources/LFRes.kt` — add strings for picker title, Keel category label, flightworthiness states (used in Unit 6 too).

  **Approach:**
  - The Keel category in the parts panel is always visible but visually disabled post-commit. This makes the exactly-one invariant legible to the user — they see the category greyed out rather than it silently disappearing.
  - Selecting a Keel creates a `PlacedKeel` at origin with the Keel's id, synthesises any `HullCollider` / rendering state that a placed hull piece normally has, and transitions to `EditingShip`.
  - When a loaded design has a non-null `placedKeel`, the builder skips straight to `EditingShip` (the existing path).
  - **Keel deletion is blocked at the intent-reducer level, not hidden in the UI.** The transform toolbar and any canvas delete shortcut continue to render for the selected Keel, but the `DeleteItem` intent becomes a no-op when the target is the `placedKeel`. Whether to emit a toast / warning toast or to silently ignore is a small UX decision picked during implementation. Rationale: this preserves R21's invariant that `No Keel` is unreachable post-commit, which is load-bearing for the Flightworthiness Indicator's state derivation (Unit 6). If the user wants a different Keel, the path is to load-or-start-a-new-design, not in-place deletion.
  - **Picker has an explicit exit path.** A `CancelKeelPick` intent pops the user back to the landing screen (same navigation target as the existing back action from `EditingShip`). No design state is persisted when cancelling — `PickingKeel` is pre-commit by construction. The picker renders a Cancel button alongside the Keel list.

  **Patterns to follow:**
  - Existing `ItemCreationAttributesPanel` as a template for the full-height right-panel layout during a non-`EditingShip` mode.
  - Existing `EditorMode` sealed-variant dispatch in `ShipBuilderScreen`.

  **Test scenarios:**
  - Happy path: a fresh builder session starts in `EditorMode.PickingKeel`; canvas is not interactable.
  - Happy path: picking a Keel commits a `PlacedKeel` at origin with the Keel's `ItemDefinition` id and transitions to `EditingShip`.
  - Happy path: once `placedKeel != null`, the parts panel's Keel category renders as disabled.
  - Happy path: loading an existing design with `placedKeel != null` starts in `EditingShip` directly, bypassing the picker.
  - Edge case: loading a design with `placedKeel == null` (e.g., corrupt or pre-v3 JSON) routes through `PickingKeel` as a recovery path.
  - Edge case: attempting to save or enter `CreatingItem` mode while in `PickingKeel` is a no-op or is disabled.
  - Edge case: dispatching `DeleteItem` on the `placedKeel`'s id is a no-op — `placedKeel` remains non-null, the Keel remains on the canvas. Same assertion via `MirrorItemX` / rotate / reposition: those succeed (Keel can be moved, just not removed).
  - Edge case: dispatching `CancelKeelPick` while in `PickingKeel` emits the landing-screen navigation side-effect and does not persist any design state. Verify no autosave fires.

  **Verification:**
  - Desktop run: starting a new design presents the Keel picker; committing a Keel reveals the canvas with the Keel placed at the centre.
  - Parts panel's Keel category is visibly disabled after commit.

---

- [x] **Unit 6: Flightworthiness indicator and lift/mass readout**

  **Goal:** Stats panel header shows a three-state Flightworthiness Indicator (`Flightworthy` / `No Keel` / `Mass exceeds lift`); body shows `Lift` and `Mass` as live stats. The existing autoSave path is the only save path — no "save as ready" distinction is introduced. R24's spawn-time gate is fully covered by Unit 4.

  **Requirements:** R20, R21. R24 is covered at the spawn-side only (Unit 4); the builder-side gate is not needed in Slice B because no save path produces anything a combat loader would consume as "marked ready" — all designs are WIP until Unit 4 recomputes flightworthiness at spawn.

  **Dependencies:** Unit 2 (`isFlightworthy` + `totalLift` computed), Unit 5 (`PickingKeel` mode + Keel-deletion block together mean `No Keel` is reachable only during initial picker and on loaded-corrupt-design recovery — not during steady-state editing).

  **Files:**
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/StatsPanel.kt` — add a header row above the existing `Stats` title that renders the indicator (coloured background + state-specific text); add two `StatRow`s — one for `Lift`, one flagging `Mass` red when it exceeds Lift. The composable signature grows a `placedKeel: PlacedKeel?` parameter (or accepts the full `ShipBuilderState` and reads `.placedKeel` internally — pick during implementation).
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/ShipBuilderScreen.kt` — pass `placedKeel` into `StatsPanel`. No new button.
  - Modify: `components/design/src/commonMain/composeResources/values/strings.xml` and `.../LFRes.kt` — `builder_flightworthy`, `builder_mass_exceeds_lift`, `builder_no_keel`, `builder_lift`.

  **Approach:**
  - **Three-state indicator derivation.** `StatsPanel` locally computes the display state from `(placedKeel, stats)`:

    ```
    when {
      placedKeel == null -> FlightworthinessDisplay.NoKeel
      stats.isFlightworthy -> FlightworthinessDisplay.Flightworthy
      else -> FlightworthinessDisplay.MassExceedsLift(stats.totalMass, stats.totalLift)
    }
    ```

    `isFlightworthy` on `ShipStats` stays a single bool — it's the *gating* signal used by Unit 4 at combat-load. The three-state display is purely a builder-UI concern. Keep the local display sealed-type private to the composable; don't leak it into `ShipStats`.
  - **Indicator colours.** Green (`MaterialTheme.colorScheme.successContainer` or the Material 3 equivalent) for `Flightworthy`; red (`errorContainer`) for both failure states. Text differentiates: `No Keel — pick one first` vs `Mass 120 / Lift 100`.
  - **Lift / Mass presentation.** Two separate rows (not a combined `120/100` string — easier to read and no ambiguity about which side is which). Tint the `Mass` value red when it exceeds `Lift`; leave `Lift` neutral.
  - **No separate "save as ready" flow.** Every save is a WIP save via the existing autoSave path. The user can edit an unflightworthy ship indefinitely without losing work, and the only thing stopping an unflightworthy design from reaching combat is Unit 4's spawn-time check. Rationale: no consumer reads a persisted `isReadyForCombat` flag in Slice B, so adding the intent + button + field + format-bump coordination would be ceremonial. A future combat-selection UI that needs to list "ready" designs will compute that from the stored designs via `isFlightworthy`, not from a persisted flag.

  **Patterns to follow:**
  - Existing `StatRow` composable.

  **Test scenarios:**
  - Test expectation: none for the composable itself — pure UI change, verified by running the builder.
  - Happy path: (unit test on the derivation helper if extracted) `placedKeel = null` → `NoKeel`; `placedKeel != null` + `isFlightworthy == true` → `Flightworthy`; `placedKeel != null` + `isFlightworthy == false` → `MassExceedsLift` with the current mass/lift values.

  **Verification:**
  - Desktop run: indicator colour + text changes as parts are added/removed; adding enough mass to exceed lift flips the indicator and tints the Mass row red.
  - Removing the last module to return mass below lift flips the indicator back to green.

---

### Phase B5 — Keel Content

- [x] **Unit 7: Keel attribute authoring UI**

  **Goal:** The part editor supports authoring Keel items. Reuses the `HullAttributesContent` layout for shared fields and adds a `lift` numeric field and a `shipClass` text field. The parts-panel "Create Keel" button enters `EditorMode.CreatingItem(itemType = ItemType.KEEL)`.

  **Requirements:** R8, R10, R11

  **Dependencies:** Unit 1 (`KeelAttributes` exists).

  **Files:**
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/ItemAttributesPanel.kt` — add `KeelAttributesContent` composable mirroring `HullAttributesContent` plus lift + shipClass fields; add a `when` branch for `ItemType.KEEL` in the `ItemAttributesPanel`; extend `ItemAttributes.isValidForSave` so Keels reject `lift ≤ 0`.
  - Modify: `features/ship-builder/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/shipbuilder/ui/composables/PartsPanel.kt` — add a `Create Keel` action to the new Keel category.
  - Modify: `components/design/src/commonMain/composeResources/values/strings.xml` and `.../LFRes.kt` — `builder_lift`, `builder_ship_class`, `builder_create_keel`.

  **Approach:**
  - Reuse the three drag-modifier fields and the mass/armour fields from the hull flow. The Keel-specific fields are `lift: Float` (error state when ≤ 0) and `shipClass: String` (free-form for v1; future progression work may enumerate).

  **Patterns to follow:**
  - Existing `HullAttributesContent` and `ModuleAttributesContent`.
  - Slice A Unit 6's `isValidForSave` zero-drag validation.

  **Test scenarios:**
  - Test expectation: none for the composable — verified by running the builder.
  - Happy path: (VM test) creating a Keel with lift > 0 and positive drag modifiers saves and appears in the Keel category.
  - Edge case: (VM test) `ItemAttributes.isValidForSave` returns false for `KeelAttributes` with `lift = 0` → Finish button disabled.

  **Verification:**
  - Desktop run: `Create Keel` in the parts panel produces an authorable Keel definition; saved Keels appear in the picker in Unit 5.

---

- [x] **Unit 8: Bundled Keel content + updated default ship designs**

  *Landed out-of-order between Units 4 and 5 (commit `05e56b8`) to unblock
  end-to-end demo scene testing. Content approach matches the plan
  (per-ship embedded Keel, not a shared catalogue; formatVersion 3;
  shipClass per archetype). Unit 5 then added a parallel small catalogue
  (`PartsCatalog.keelItems`) mirroring the four ship Keels so the picker
  has something to show in a fresh design.*

  **Goal:** Ship at least one Keel `ItemDefinition` per bundled archetype (player / light / medium / heavy) — either as separate per-ship Keels or a shared small catalogue — and update the four bundled `default_ships/*.json` to reference them. Each default ship's `placedHulls` loses one entry (which becomes the Keel), `placedKeel` is added, and the formatVersion is bumped to 3. Lift values are tuned so each ship is flightworthy under its current part mass.

  **Requirements:** R11, R14 (content)

  **Dependencies:** Units 1, 2, 7.

  **Files:**
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/player_ship.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_light.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_medium.json`
  - Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json`

  **Approach:**
  - Pick one hull piece per ship (the main body) and re-author it as a Keel with a lift value ≥ that ship's total part mass by a comfortable margin (e.g., 1.2×). Preserve the existing drag modifier values applied in Slice A Unit 7.
  - Bump `formatVersion` to 3.
  - Assign each ship a `shipClass` string: `"fighter"` (player, light), `"frigate"` (medium), `"cruiser"` (heavy). Names are descriptive; they do not drive behaviour yet.

  **Execution note:** This is content work. Compute predicted lift-vs-mass with a small script (as in Slice A Unit 7) before playing to confirm none are marginal. Re-tune during playtest.

  **Test scenarios:**
  - Test expectation: none — content JSON.
  - Verification runs through the existing `ShipDesignSerializationTest` (which will already assert v3 from Unit 1) — new JSONs must deserialize cleanly.

  **Verification:**
  - `./gradlew :components:game-core:api:jvmTest` passes including the deserialization of the updated JSONs.
  - Desktop run: all four bundled ships spawn (Unit 4 doesn't refuse any of them), all display `Flightworthy` in the builder if loaded.
  - Keel destruction on any of the four causes drift-then-despawn as expected.

---

## System-Wide Impact

- **Interaction graph:** `Ship` replaces the old `isDestroyed` flag + `onDestroyedCallback` with a single `lifecycle: ShipLifecycle` state plus a single `onLifecycleTransition: (Ship, ShipLifecycle) -> Unit` hook. `GameStateManager` subscribes once and branches inside the hook: match-result re-tally on every transition (fires at `LiftFailed` entry and at `Destroyed(HULL)` entry), terminal cleanup (cause-tagged log + backing-list removal) only when `newState is Destroyed`. Ship self-removes from `actorManager` on the `Destroyed` transition. The backing `playerShips`/`enemyShips` lists retain `LiftFailed` ships during drift — the match-result decision uses `none { it.lifecycle is Active }` over the backing list, not list-emptiness. `isValidTarget()` moves from `!isDestroyed` to `lifecycle is Active`, which affects turret target acquisition and navigator combat-target validity. Turrets are gated at the ship level (`Ship.update` skips turret update calls when `lifecycle !is Active` via the `firingEnabled` flag set synchronously at the transition) rather than self-checking, sidestepping the Kubriko ActorManager child-update-ordering race. Damage routing in Slice B is arc-only (KEEL becomes the side-arc primary); polygon-based targeting is deferred to a future damage-model slice.
- **Error propagation:** `ShipDesignConverter` now fails cleanly when `placedKeel == null`. `GameStateManager.startDemoScene` no longer uses `.getOrThrow()` — per-design conversion failure and flightworthiness failure both emit a `println` and skip `createShip`, with the match-result fallback handled post-loop. The builder's save path is unchanged (autoSave is always permissive); unflightworthy state is surfaced visually via the Flightworthiness Indicator, not by blocking saves.
- **State lifecycle risks:** The `LiftFailed` drift window is the first non-binary ship state in the game. The key risk is double-counting: a `LiftFailed` ship is both visible on the battlefield and "already destroyed for tally purposes". Handled by the single `onLifecycleTransition` hook with in-handler branching — match-result re-tally runs on every transition (gated so it doesn't re-fire once resolved); terminal cleanup runs only when `newState is Destroyed`. The backing team list retains `LiftFailed` ships during drift; the tally filters them out via `none { it.lifecycle is Active }`. Reactor and Keel paths both eventually converge on `Destroyed`, but only the reactor path triggers immediate actor cleanup.
- **API surface parity:** `Ship.isDestroyed` is deleted, as is the old `onDestroyedCallback`. Every caller migrates to `lifecycle is ShipLifecycle.Destroyed` (or the inverted `is Active` for the common case). Callsites: `InputController`, `DebugVisualiser`, `Ship.isValidTarget`, `GameStateManager` (which subscribes to the single new `onLifecycleTransition` hook). Turrets are *not* in this list — they're gated at the ship level (parent skips their update calls when `lifecycle !is Active`) rather than self-checking parent state.
- **Integration coverage:** The critical integration path is the full "Keel damage → LiftFailed → drift → Destroyed" flow. Unit 3's integration test exercises this with a real `Ship`, a real `ShipPhysics`, and a multi-frame update loop. The secondary integration is "unflightworthy design → GameStateManager refuses → match resolves" — Unit 4 covers this.
- **Unchanged invariants:** Slice A's drag physics, turn-rate rotation, drag-aware braking, and navigator structure are untouched. Bullet physics is unchanged. The mass-as-MoI stand-in remains. `MovementConfig` is untouched. `ShipConfig.internalSystems` gains one entry but is otherwise structurally the same. The builder's canvas interactions are unchanged outside of the `PickingKeel` / `EditingShip` mode gate.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Destruction-callback signature change breaks everything that references `isDestroyed` | The migration is mechanical — Grep for `isDestroyed` before changes; cover every callsite in Unit 3. Characterization test before the refactor (Unit 3's Execution note) anchors current behaviour. |
| `LiftFailed` drift leaves turrets shooting at a corpse (or being shot at) | Two-sided guard: `isValidTarget` returns false during `LiftFailed` (caller audit ensures turrets and navigator both respect it for target selection), AND the parent `Ship.update` skips its own turret children's update calls when `lifecycle !is Active` (prevents in-flight shots from firing on the transition frame). Both paths unit-tested in Unit 3. |
| Match-result logic miscounts during drift — a `LiftFailed` ship is "on the battlefield" but the opposing team should already have won | Single `onLifecycleTransition` hook with in-handler branching: match-result re-tally via `playerShips.none { it.lifecycle is Active } / enemyShips.none { ... }` fires at every transition (gated by `_gameResult.value == null`); terminal cleanup (backing-list removal) runs only when `newState is Destroyed`. Test asserts victory fires at the Keel-destruction moment, not at despawn, and does not re-fire on the subsequent `Destroyed(LIFT_FAILURE)` transition. |
| Format bump breaks any saved designs outside the bundled set | Prototype stance: no saved designs outside the bundled set are in scope. Consistent with Slice A's Unit 1 deferral of the bump. Document in release notes (once notes exist). |
| Builder's `PickingKeel` flow strands a user with no saveable state if they abandon partway | `PickingKeel` is pre-commit; no autosave fires because `placedKeel == null` and the design is effectively empty. Cancelling returns to the landing screen with nothing persisted — matches existing "new design" abandonment behaviour. |
| `shipClass` as a free-form string invites typos and drift from a future canonical list | Accepted. Future progression brainstorm will formalise. For now, class is a label, not a gate. |
| Same-frame reactor-and-keel destruction produces ambiguous cause | Explicitly order-of-precedence: reactor destruction takes priority and maps to `Destroyed(HULL)` with no drift. A side-arc hit that zeros KEEL and overflows into REACTOR in one damage call still resolves to `Destroyed(HULL)` because `updateLifecycle` runs end-of-frame and checks reactor first. Documented in Unit 3's transition rule and tested. |
| Side arcs now route to KEEL instead of REACTOR — reactor becomes harder to kill, Keel becomes the new go-to target for sustained side fire | Intentional. This is how the Keel earns its place as a combat target in Slice B without polygon routing. Playtest may reveal that reactor kills become too rare; if so, tune arc widths or overflow order. Not a blocker. |

## Documentation / Operational Notes

- **Update `docs/ship.md`** with the Keel concept, lift budget, and the two-gate flightworthiness enforcement.
- **Update `docs/game_design.md`** with the lift-failure death condition and the `shipClass` label axis.
- **No deployment or monitoring changes.** Solo prototype. The `println` destruction log is the only new observable output.
- **Combat log UI** is explicitly deferred; the cause plumbing is a forward-compatibility investment. A future brainstorm decides the visible form.

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md](../brainstorms/2026-04-10-atmospheric-movement-requirements.md) — Slice B requirements R8–R11, R14, R17, R19, R20, R21, R24.
- **Preceding slice (shipped):** [docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md](2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md).
- **Key runtime files:**
  - `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt`
  - `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouter.kt`
  - `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`
- **Key data-model files:**
  - `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesign.kt`
  - `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ItemDefinition.kt`
  - `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/InternalSystemSpec.kt`
  - `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt`
  - `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/ShipDesignConverter.kt`
