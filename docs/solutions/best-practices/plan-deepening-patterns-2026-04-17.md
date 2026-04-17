---
title: Planning patterns — two-hook lifecycle, Result-loop boundaries, cross-hierarchy shared interfaces
date: 2026-04-17
category: docs/solutions/best-practices/
module: docs/plans (surfaced during Slice B plan review; lands in components/game-core + features/game/impl when Slice B ships)
problem_type: best_practice
component: documentation
severity: high
applies_when:
  - designing state-machine transitions whose consumers need different moments (observation vs cleanup)
  - writing bulk scene-setup or batch-conversion loops that call fallible converters returning Result
  - modelling a sealed hierarchy where variants need distinct identity but share behaviour consumed by aggregation loops
  - reviewing a plan before implementation and spotting callback-collapse, getOrThrow-in-loop, or nullable-field-merge smells
tags: [planning, architecture, state-machines, error-handling, sealed-classes, kotlin, api-design, review-patterns]
---

# Planning patterns — two-hook lifecycle, Result-loop boundaries, cross-hierarchy shared interfaces

> **Note on `component:`** — the schema's `component` enum is Rails-shaped. These are architectural-design patterns extracted from a plan review; the honest fit is `documentation` (the artefact is design documentation for plan authors and reviewers). Kotlin-specific shape (sealed classes, `Result`) lives in tags.

## Context

Slice B of the atmospheric movement feature was drafted in one pass, then stress-tested by a five-reviewer deepening pass on 2026-04-17 (coherence, feasibility, scope-guardian, design-lens, adversarial). Three reviewer findings reshaped the plan's architecture before a single line of Slice B code was written. Each finding is a distilled planning lesson about where roles need to be split — sometimes at runtime (hooks that fire at different moments) and sometimes at compile time (interfaces that cut across sealed hierarchies). These patterns live in the plan today and will land in code when Slice B is implemented.

Plan: `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md` (deepened commit `9a36ad2`).

Companion doc — physics/runtime patterns from the same slice's *shipping* arc: `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`.

## Guidance

**Unifying theme:** when one "thing" is secretly two roles that fire at different moments or serve different consumers, give each role its own seam. A single callback doing double duty, a `.getOrThrow()` at a per-item boundary, or a sealed-only hierarchy with cast-based reads will each collapse the seam and force a bad compromise.

### Pattern A — Two-hook lifecycle split

When a state machine has a transition whose consumers care about two different moments — "any transition" vs. "terminal only" — expose two hooks, not one.

```kotlin
sealed interface ShipLifecycle {
    data object Active : ShipLifecycle
    data class LiftFailed(val remainingDriftMs: Int) : ShipLifecycle
    data class Destroyed(val cause: DestructionCause) : ShipLifecycle
}

class Ship(
    // every transition, for observers (match tally, UI, debug)
    var onLifecycleTransition: ((Ship, ShipLifecycle) -> Unit)? = null,
    // terminal only, for cleanup (actor removal, cause-tagged log)
    var onDestroyedCallback:   ((Ship, DestructionCause) -> Unit)? = null,
)
```

`GameStateManager` subscribes to `onLifecycleTransition` and re-tallies via `playerShips.none { it.lifecycle is Active }` so victory fires the instant a Keel is destroyed (entry into `LiftFailed`), even though the ship's actor remains in the scene for ~3 seconds of drift. `onDestroyedCallback` runs at the terminal `Destroyed` transition and handles actor cleanup (`actorManager.remove`, drop from the backing team list, `println` with `cause`).

### Pattern B — `Result`-loop over `.getOrThrow()` at scene-setup boundaries

Scene-setup entry points that spawn N items from M designs must iterate `Result`s, not throw on the first failure. Throwing kills downstream invariant checks and turns recoverable dataset problems into hard crashes.

```kotlin
designs.forEach { design ->
    converter.convertShipDesign(design)
        .onSuccess { config ->
            val stats = calculateShipStats(design)
            if (!stats.isFlightworthy) {
                println("[combat] refused to spawn unflightworthy: '${design.name}'")
                return@forEach
            }
            createShip(config, team = design.team)
        }
        .onFailure { err ->
            println("[combat] design '${design.name}' failed conversion: ${err.message}")
        }
}
// post-loop fallback — if every enemy failed one gate or the other, the
// player wins by default; do NOT depend on a single getOrThrow to stop
// the scene mid-setup.
if (enemyShips.isEmpty()) _gameResult.value = VICTORY
```

With this shape, the Unit 4 flightworthiness recheck at spawn time is actually reachable. With the v1 `.getOrThrow()` shape, the converter's own Keel-required invariant threw first and the recheck was dead code.

### Pattern C — Shared-semantics interface across a sealed hierarchy

When two sealed variants need distinct identity (own enum value, own fields, own UI category) but share some behaviour, put the shared contract on an interface that sits *across* the hierarchy, not inside it.

```kotlin
interface ExternalPartAttributes {
    val armour: SerializableArmourStats
    val forwardDragModifier: Float
    val lateralDragModifier: Float
    val reverseDragModifier: Float
}

sealed interface ItemAttributes {
    val mass: Float

    data class HullAttributes(...)   : ItemAttributes, ExternalPartAttributes
    data class ModuleAttributes(...) : ItemAttributes
    data class TurretAttributes(...) : ItemAttributes
    data class KeelAttributes(...)   : ItemAttributes, ExternalPartAttributes
}

// drag aggregation reads one combined list through the interface — no cast
val exteriorParts: List<ExternalPartAttributes> =
    placedHulls.mapNotNull { (resolve(it) as? HullAttributes) } +
    listOfNotNull(placedKeel?.let { resolve(it) as? KeelAttributes })
val forwardDrag = exteriorParts.sumOf { it.forwardDragModifier.toDouble() }
```

One loop, one mental model, no cast-fallthrough, identity preserved at the placement layer (`PlacedKeel?` stays a separate nullable singleton from `placedHulls`).

## Why This Matters

**Pattern A.** The v1 single callback `onDestroyedCallback(Ship, DestructionCause)` forced a binary choice. Fire it on `LiftFailed` entry → match ends correctly but the ship's actor vanishes mid-drift, breaking the 3-second "death spiral" visual. Fire it on terminal `Destroyed` → cleanup is right but the player sees the victory banner 3 seconds after the Keel-kill moment they can feel. Two hooks cost one extra function signature and dissolve the tradeoff; the backing-list tally becomes a `.none { is Active }` filter rather than a list-emptiness check. Adversarial reviewer caught this — it would not have been caught by unit tests because the two bugs cancel at the per-callback level.

**Pattern B.** `converter.convertShipDesign(design).getOrThrow()` inside `startDemoScene` made the spawn-time flightworthiness recheck (Unit 4) dead code: the converter's own Keel-required invariant would throw first, propagate out of the `suspend` scene-transition function, and take down the whole scene. A per-item `Result`-loop keeps the recheck reachable and lets one bad design skip instead of killing the whole scene. The pattern also gives the implementer a single "every design got rejected" post-loop fallback point — no silent "no ships spawned and game hangs" mode.

**Pattern C.** Two alternatives failed. The v1 "phantom entry" cast trick (iterate `placedHulls + Keel`, cast each to `HullAttributes`) would drop the Keel from drag aggregation silently because `KeelAttributes` fails the cast — a zero-log bug where ships just feel wrong. Merging Keel into `HullAttributes` with nullable `lift`/`shipClass`/`maxHp` forces every hull-reader into null-checks *and* loses exactly-one-Keel enforcement at the placement layer (the nullable `placedKeel: PlacedKeel?` singleton becomes a length-constrained list). Parallel loops (drag aggregation in `ShipStatsCore`, drag aggregation again in `ShipDesignConverter`) duplicate the logic and invite drift the next time a drag field is added. The interface cuts all three failure modes.

## When to Apply

- A lifecycle callback's consumers split into "every transition" vs. "terminal" audiences (match-tally vs. actor cleanup; UI update vs. persistence; observer vs. finalizer).
- A scene-setup, bulk-load, or batch-process entry point iterates N items and any per-item failure should degrade, not crash the whole operation.
- A downstream safety check exists after an operation that currently `.getOrThrow()`s — the check is dead code until the surrounding iteration is a `Result`-loop.
- Two sealed variants share runtime behaviour (drag, armour, rendering, collision) but need different identity, placement rules, or UI category.
- You are tempted to reach for `as?` on a sealed variant inside an aggregation loop.
- A reviewer asks "what happens if this fires at moment X vs. moment Y?" and both answers are "it breaks something."

## Examples

All three patterns live today in `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md`:

- Pattern A — Unit 3 "Approach" (two-hook lifecycle) + HL Technical Design state diagram with `[T]/[M]/[D]` transition-hook annotations + Key Technical Decision 5.
- Pattern B — Unit 4 "Approach" (Result-loop replaces `getOrThrow` inside `startDemoScene`) + "Verification" (greps for removal of `.getOrThrow()`).
- Pattern C — Unit 1 "Files" (`ExternalPartAttributes` interface) + Key Technical Decision 1 + Unit 2 "Approach" replacement of the v1 "phantom entry" cast.

When Slice B is implemented, the patterns will touch:
- `components/game-core/api/.../shipdesign/ItemDefinition.kt` — `ExternalPartAttributes` interface (Pattern C).
- `features/game/impl/.../actors/ShipLifecycle.kt` (new) — sealed type (Pattern A).
- `features/game/impl/.../actors/Ship.kt` — two callbacks (Pattern A).
- `features/game/impl/.../managers/GameStateManager.kt` — Result-loop in `startDemoScene` (Pattern B), lifecycle-hook subscription (Pattern A).
- `components/game-core/api/.../shipdesign/ShipDesignConverter.kt` — `Result.failure` on null Keel (Pattern B).

Only the plan file exists today; no Slice B commits yet. This doc and its sibling (`physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`) will gain back-references when those commits land.

## Related

- **Slice B plan** (source material): `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md`
- **Sibling — physics/runtime patterns from Slice A**: `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`
- **Slice A plan**: `docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md`
- **Origin brainstorm**: `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`
