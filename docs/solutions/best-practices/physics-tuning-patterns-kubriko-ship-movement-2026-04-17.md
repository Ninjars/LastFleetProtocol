---
title: Physics tuning patterns from the atmospheric movement reframe (Slice A)
date: 2026-04-17
category: docs/solutions/best-practices/
module: components/game-core + features/game/impl (physics + navigation)
problem_type: best_practice
component: service_object
severity: medium
applies_when:
  - tuning a control loop that oscillates around its setpoint
  - a physics formula derived from raw engine units feels off by an order of magnitude
  - angular dynamics need to feel responsive without full rigid-body fidelity
  - integrating gameplay physics on top of Kubriko actor/sprite managers
  - trading dimensional correctness for tractable gameplay math
tags: [physics, kubriko, ship-movement, control-loops, tuning-constants, angular-motion, game-feel, kmp]
---

# Physics tuning patterns from the atmospheric movement reframe (Slice A)

> **Note on `component:`** — the schema's `component` enum is Rails-shaped. The navigator + integrator sits between the ViewModel layer and Kubriko actor state — a stateful internal logic module. `service_object` is the closest conceptual analogue ("encapsulated domain behaviour invoked by a controller-equivalent"). If a game/physics component enum is added later, update the frontmatter.

## Context

Slice A of the atmospheric movement reframe rebuilt Last Fleet Protocol's ship movement from frictionless Newtonian physics into a quadratic-drag atmospheric model on the Kubriko engine. The shipping arc spanned two commits: `3cdeef9` introduced quadratic drag, turn-rate rotation, and rewrote `ShipNavigator` around the new model; `e95a5af` then fixed a navigator oscillation and scaled the drag constant after playtesting. Along the way, three distinct patterns crystallised that recur whenever gameplay physics, tactical AI, and raw-engine-unit tuning collide. This doc captures them as reusable guidance for the next physics-adjacent slice.

See Slice A plan `docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md` for the full historical record; this doc is the distilled forward-looking reference.

## Guidance

**Unifying theme:** in a real-time game loop where the physics already encodes the constraints you care about, *stop fighting the physics with the controller*. Let the simulation do the work; let the AI read the equilibrium; let a single tuning constant absorb the unit mismatch.

### 1. Delete oscillating control loops — don't tune them

When a controller flip-flops around an equilibrium the physics already enforces, removing the controller is cheaper and more stable than adding dampening.

```kotlin
// features/game/impl/.../navigation/ShipNavigator.kt (post-e95a5af)
// Cruising: apply thrust proportional to alignment with target direction.
// Drag caps speed at terminal velocity — no velocity-correction loop.
if (forwardComponent > THRUST_ALIGNMENT_THRESHOLD) {
    physics.applyThrust(
        SceneOffset(1f.sceneUnit, 0f.sceneUnit),
        movementConfig.forwardThrust * forwardComponent,
        facing,
    )
}
```

The predecessor computed a desired velocity each frame and applied full thrust to chase it — guaranteed flip-flop near terminal velocity. Drag is the natural cap; exposing thrust to *alignment* instead of *target speed* makes the ship accelerate toward equilibrium and then sit there.

### 2. Absorb raw-engine-unit mismatch with a single boundary constant

```kotlin
// components/game-core/api/.../stats/ShipStatsCore.kt
forwardDragCoeff = forwardExtent * avgForwardMod * RHO
// ...
/** Air density — dimensionless tuning parameter that scales all drag
 *  coefficients uniformly. */
const val RHO = 0.005f
```

Bounding-box extents in scene units come out around 74. `RHO = 1.0` gave `dragCoeff ≈ 74`, terminal velocity `sqrt(1200/74) ≈ 4` — unplayable. `RHO = 0.005` drops `dragCoeff ≈ 0.37`, terminal velocity ≈ 57. One named constant at the formula boundary, documented as the tuning dial, beats rewriting the derivation.

### 3. Trade dimensional correctness for tractable AI math when the approximation is invisible

```kotlin
// features/game/impl/.../navigation/ShipNavigator.kt
private fun computeTurnRate(movementConfig: MovementConfig, mass: Float): Float {
    if (mass <= 0f) return 0f
    return movementConfig.angularThrust / mass * TURN_RATE_SCALE
}

private fun rotateToward(body: BoxBody, targetAngle: AngleRadians, turnRate: Float, dt: Float) {
    val turnRateRad = turnRate * (PI.toFloat() / 180f)
    val maxRotation = turnRateRad * dt
    val rawDelta = targetAngle - body.rotation
    val delta = normalizeAngle(rawDelta).normalized.let {
        if (it > PI.toFloat()) it - (2.0 * PI).toFloat() else it
    }
    body.rotation += delta.coerceIn(-maxRotation, maxRotation).rad
}
```

`mass` stands in for moment of inertia — dimensionally wrong, gameplay-indistinguishable. The navigator now estimates rotation time as `angleDelta / turnRate`: a single division. The dimensionally-correct predecessor required a closed-form integral through variable angular drag, which no tactical AI can carry in a per-frame budget.

## Why This Matters

**Oscillation.** Keeping the velocity-chasing loop and adding damping/hysteresis would have layered tuning parameters on top of a structurally wrong controller. Every future ship class or engine upgrade would re-trigger the instability. Deleting the loop makes the controller structurally correct and means new ship tuning only touches thrust/drag numbers in `ShipStatsCore`. Rejected alternative: a PID with heuristic gains — would have needed per-ship retuning and still oscillated when drag coefficients shifted in the content pass.

**RHO.** The alternatives were (a) redefining `MovementConfig` drag fields to be pre-scaled (forcing every piece of content to know about scene-unit magnitudes) or (b) normalising bounding-box extents at computation time (hiding the unit gap inside `calculateShipStats`, fragile under future geometry changes). A single dimensionless constant at the formula boundary is visible, one-line to retune, and survives content iteration without touching call sites.

**Turn-rate vs angular momentum.** Keeping angular physics would have made the braking-distance estimate (`stoppingDistanceUnderDrag`) need an angular counterpart — a closed-form solution through `angularVelocity` decay that doesn't exist in elementary form. The rejected alternative was a lookup table or simulated rollout per frame per ship; both blow the per-frame allocation budget on Android. Turn-rate rotation also removed an entire state variable (`angularVelocity`) and two methods (`applyAngularForce`, `decelerateAngular`) from `ShipPhysics`, shrinking the test surface.

## When to Apply

- A controller full-thrusts both directions near an equilibrium the simulation already enforces (terminal velocity, drag cap, clamp).
- A formula mixes engine-native units (scene units, pixels, tiles) with tuning-dimension quantities (modifiers, area) and feels off by an order of magnitude.
- Tactical AI needs a closed-form prediction (arrival time, braking distance, rotation time) through a physics quantity whose integral is intractable.
- Introducing angular or rotational state that only the AI will query, not the player.
- Removing a PID or desired-state chaser whose gains are being hand-tuned per ship class.
- Any new slice where per-frame allocation and deterministic behaviour on both Android and Desktop are firm constraints.

## Examples

Navigator before/after — the oscillating velocity-chase vs the alignment-proportional thrust — is visible in the diff of `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt` between `3cdeef9` and `e95a5af` (146 lines removed, 48 added; net -100 LOC).

RHO scaling, single-line:

```kotlin
// components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/stats/ShipStatsCore.kt
const val RHO = 0.005f
```

Drag-aware stopping distance in the navigator — the tractable closed form that replaced a per-ship simulated rollout:

```kotlin
// features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt
private fun stoppingDistanceUnderDrag(speed: Float, thrust: Float, dragCoeff: Float, mass: Float): Float {
    if (speed < CORRECTION_EPSILON) return 0f
    if (thrust <= 0f) return Float.MAX_VALUE
    if (dragCoeff < DRAG_EPSILON) {
        val decel = thrust / mass
        return if (decel > 0f) (speed * speed) / (2f * decel) else Float.MAX_VALUE
    }
    val kv2 = dragCoeff * speed * speed
    return (mass / (2f * dragCoeff)) * ln((thrust + kv2) / thrust)
}
```

The zero-drag fallback matters: it lets the same formula serve ships with atmospheric drag and (hypothetical) vacuum-regime ships without branching at the call site — the same boundary-constant hygiene as RHO, one level up.

## Related

- **Slice A plan** (historical record, implemented all three patterns): `docs/plans/2026-04-14-001-feat-atmospheric-movement-slice-a-plan.md`
- **Slice B plan** (builds on these patterns, notably the "delete the loop" lesson at line 92): `docs/plans/2026-04-16-001-feat-atmospheric-movement-slice-b-plan.md`
- **Origin brainstorm** (R6 mass-as-MoI stand-in; R30 AI braking oscillation acceptance criterion): `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`
- **Ship design overview**: `docs/ship.md`
- **Kubriko runtime constraints**: `docs/ai_kubriko_constraints.md`
