---
title: Planning patterns — sealed-state hooks, Result-loop boundaries, cross-hierarchy shared interfaces
date: 2026-04-17
last_updated: 2026-04-18
category: docs/solutions/best-practices/
module: docs/plans (surfaced during Slice B plan review; Patterns A, B, C landed in components/game-core + features/game/impl during Slice B Units 1-3)
problem_type: best_practice
component: documentation
severity: high
applies_when:
  - designing state-machine transitions whose consumers need different moments (observation vs cleanup)
  - writing bulk scene-setup or batch-conversion loops that call fallible converters returning Result
  - modelling a sealed hierarchy where variants need distinct identity but share behaviour consumed by aggregation loops
  - reviewing a plan before implementation and spotting getOrThrow-in-loop or nullable-field-merge smells
  - deciding whether a proposed "two seams for two roles" split is load-bearing or over-engineering
tags: [planning, architecture, state-machines, error-handling, sealed-classes, kotlin, api-design, review-patterns]
---

# Planning patterns — sealed-state hooks, Result-loop boundaries, cross-hierarchy shared interfaces

> **Note on `component:`** — the schema's `component` enum is Rails-shaped. These are architectural-design patterns extracted from a plan review; the honest fit is `documentation` (the artefact is design documentation for plan authors and reviewers). Kotlin-specific shape (sealed classes, `Result`) lives in tags.

## Context

Slice B of the atmospheric movement feature was drafted in one pass, then stress-tested by a five-reviewer deepening pass on 2026-04-17 (coherence, feasibility, scope-guardian, design-lens, adversarial). Three reviewer findings reshaped the plan's architecture before a single line of Slice B code was written. Each finding is a distilled planning lesson about where roles need to be split — sometimes at runtime (hooks that fire at different moments) and sometimes at compile time (interfaces that cut across sealed hierarchies). Units 1–3 landed Slice B's data model and lifecycle runtime on 2026-04-17 and 2026-04-18; this doc has been updated to reflect what actually shipped (noting where execution refined the plan).

Plan: `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md`.

Companion doc — physics/runtime patterns from the same slice's *shipping* arc: `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`.

## Guidance

**Unifying theme:** when one "thing" is secretly two roles that fire at different moments or serve different consumers, give each role its own seam. A single callback doing double duty, a `.getOrThrow()` at a per-item boundary, or a sealed-only hierarchy with cast-based reads will each collapse the seam and force a bad compromise.

### Pattern A — Sealed-state lifecycle hook with in-handler branching

When a state machine has a transition whose consumers care about different moments (observation vs. terminal cleanup), surface one hook that takes the full sealed state and let subscribers branch inside a `when`. The sealed type carries the moment; splitting hooks by moment duplicates the type's job.

```kotlin
sealed interface ShipLifecycle {
    data object Active : ShipLifecycle
    data class LiftFailed(val remainingDriftMs: Int) : ShipLifecycle
    data class Destroyed(val cause: DestructionCause) : ShipLifecycle
}

class Ship(
    // Every transition. Subscribers branch on the new state.
    var onLifecycleTransition: ((Ship, ShipLifecycle) -> Unit)? = null,
) {
    // Ship handles its own actor-manager removal on Destroyed — that's a
    // Ship-internal self-cleanup symmetric with actorManager.add at creation,
    // NOT something a subscriber needs to orchestrate via a second callback.
}
```

Subscriber branches inside its single hook:

```kotlin
ship.onLifecycleTransition = { ship, newState ->
    // Match-tally — fires on every transition, gated so it doesn't re-fire
    // after a result has already been set.
    if (gameResult.value == null) retallyMatchResult()

    // Terminal cleanup — only fires when newState is Destroyed.
    if (newState is ShipLifecycle.Destroyed) {
        println("[combat] ${ship.teamId} destroyed: ${newState.cause}")
        teamList.remove(ship)
    }
}
```

#### Why not two hooks?

The initial Slice B plan specified two callbacks — `onLifecycleTransition` (every transition) and `onDestroyedCallback` (terminal only) — to "keep each concern explicit". During execution of Unit 3 this was refactored to one. The argument for the split (*two different moments need two different callbacks*) collapses once the sealed state carries the moment: `when (newState)` on a sealed type is exhaustive, branch-local, and impossible to forget. Two independent subscriptions invited drift and made the "cleanup only at Destroyed" invariant live in two places instead of one. Kotlin's sealed type already enforces it in one.

**The planning lesson, revised:** when a plan proposes splitting a callback into two "because they fire at different moments", first ask whether the consuming code already has (or can trivially get) a sealed type that encodes the moment. If yes, the split is over-engineering; a single hook with in-handler `when` is the idiomatic shape. Reserve multiple hooks for cases where the consumers are genuinely decoupled (different classes, different modules, different subscribe-at-different-times lifetimes) — not merely different temporal semantics within one subscriber.

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

**Pattern A.** The brainstorm-era single callback `onDestroyedCallback(Ship)` forced a binary choice: fire it on `LiftFailed` entry → match ends correctly but the ship's actor vanishes mid-drift, breaking the 3-second "death spiral" visual; fire it on terminal `Destroyed` → cleanup is right but the player sees the victory banner 3 seconds after the Keel-kill moment they can feel. Adversarial reviewer caught this at plan time — and would not have been caught by unit tests, since the two bugs cancel at the per-callback level. The plan's first fix was to split into two hooks; execution refined that to one hook + sealed-state branching, which resolves the same tradeoff without the second subscription surface. The backing-list tally still becomes a `.none { is Active }` filter rather than a list-emptiness check — that's the load-bearing change. The number-of-callbacks question is secondary.

**Pattern B.** `converter.convertShipDesign(design).getOrThrow()` inside `startDemoScene` made the spawn-time flightworthiness recheck (Unit 4) dead code: the converter's own Keel-required invariant would throw first, propagate out of the `suspend` scene-transition function, and take down the whole scene. A per-item `Result`-loop keeps the recheck reachable and lets one bad design skip instead of killing the whole scene. The pattern also gives the implementer a single "every design got rejected" post-loop fallback point — no silent "no ships spawned and game hangs" mode.

**Pattern C.** Two alternatives failed. The v1 "phantom entry" cast trick (iterate `placedHulls + Keel`, cast each to `HullAttributes`) would drop the Keel from drag aggregation silently because `KeelAttributes` fails the cast — a zero-log bug where ships just feel wrong. Merging Keel into `HullAttributes` with nullable `lift`/`shipClass`/`maxHp` forces every hull-reader into null-checks *and* loses exactly-one-Keel enforcement at the placement layer (the nullable `placedKeel: PlacedKeel?` singleton becomes a length-constrained list). Parallel loops (drag aggregation in `ShipStatsCore`, drag aggregation again in `ShipDesignConverter`) duplicate the logic and invite drift the next time a drag field is added. The interface cuts all three failure modes.

## When to Apply

- A lifecycle transition's consumers care about different moments (match-tally at state-entry vs. cleanup at a terminal state; UI update vs. persistence; observer vs. finalizer) — surface the moment as a sealed state value, not as separate callbacks.
- A scene-setup, bulk-load, or batch-process entry point iterates N items and any per-item failure should degrade, not crash the whole operation.
- A downstream safety check exists after an operation that currently `.getOrThrow()`s — the check is dead code until the surrounding iteration is a `Result`-loop.
- Two sealed variants share runtime behaviour (drag, armour, rendering, collision) but need different identity, placement rules, or UI category.
- You are tempted to reach for `as?` on a sealed variant inside an aggregation loop.
- A reviewer asks "what happens if this fires at moment X vs. moment Y?" and both answers are "it breaks something" — usually the fix lives in the state type, not in splitting the callback surface.
- A plan proposes "two seams for two roles". Check whether the consuming code already has (or can get) a sealed type that encodes the moment. If yes, a single seam + in-handler `when` is almost always cleaner.

## Examples

Patterns A and C shipped in Slice B Units 1–3 (commits `24b6b3a`, `d0b8c8f`, `4aef1fd`, `e420baf`). Pattern B lands in Unit 4.

- **Pattern A** — `features/game/impl/.../actors/ShipLifecycle.kt` (new sealed type); `features/game/impl/.../actors/Ship.kt` (single `onLifecycleTransition` hook; Ship self-removes from `actorManager` on `Destroyed`); `features/game/impl/.../managers/GameStateManager.kt` (subscriber branches on `newState` via `when`). The two-hook intermediate shape was committed briefly (`4aef1fd`) then refactored to one (`e420baf`) — the diff between those two commits is a useful worked example of the refinement.
- **Pattern B** — `features/game/impl/.../managers/GameStateManager.kt` `startDemoScene` (Unit 4 replaces the `.getOrThrow()` calls with a per-design Result-loop + post-loop fallback). Plan at `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md` Unit 4 Approach.
- **Pattern C** — `components/game-core/api/.../shipdesign/ItemDefinition.kt` (`ExternalPartAttributes` interface across the `ItemAttributes` sealed hierarchy); `components/game-core/api/.../stats/ShipStatsCore.kt` (drag aggregation loop uses the interface via a `buildExteriorParts` helper). Plan Unit 1 Files + Key Technical Decision 1.

The companion physics doc (`physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`) covers the Slice A shipping arc and the `RHO` constant pattern that Pattern C's drag aggregation feeds into.

## Related

- **Slice B plan** (source material): `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md`
- **Sibling — physics/runtime patterns from Slice A**: `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md`
- **Slice A plan**: `docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md`
- **Origin brainstorm**: `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`
