---
title: "feat: Combat vertical slice — physics movement, kinetic weapons, damage pipeline"
type: feat
status: completed
date: 2026-04-03
origin: docs/brainstorms/2026-04-03-combat-vertical-slice-requirements.md
---

# feat: Combat vertical slice — physics movement, kinetic weapons, damage pipeline

## Overview

Implement a playable combat vertical slice proving the core pipeline end-to-end: ships move
with physics-based momentum, turrets fire kinetic projectiles that interact with hull armour,
penetrating hits damage internal systems via arc-based routing, and ships are destroyed when
their reactor is destroyed. Includes camera pan/zoom, tap-to-move orders, simple enemy AI,
and win/lose conditions.

## Problem Frame

The prototype currently renders ships with turrets that fire non-interacting bullets. No combat
loop exists. This plan delivers the minimum playable slice that validates: physics movement with
directional thrust, kinetic projectile-to-armour resolution, internal system damage/disable/destroy
mechanics, and a complete game round with win/lose. (see origin:
`docs/brainstorms/2026-04-03-combat-vertical-slice-requirements.md`)

## Requirements Trace

- R1. Convex polygon hull with armour segments on edges
- R2. Internal systems as flat list, arc-based damage routing
- R3. Uniform armour stat block per ship
- R4-R7. Physics-based movement: mass, momentum, directional thrust, rotation, navigate-to-target
- R8-R9. Camera pan/zoom on desktop and Android
- R10-R11. Tap-to-select ship, tap-to-move
- R12. Kinetic impact chain: hit -> ricochet -> penetration -> damage
- R13. Bullet TTL and cleanup
- R14-R15. Arc-based damage routing with over-penetration
- R16-R23. Internal systems (Reactor, Main Engine, Bridge) with disable/destroy
- R24-R26. Enemy AI using same movement system
- R27-R28. Win/lose conditions with result screen
- R29. Ship destruction on reactor destroy
- R30. Data-driven stats via parameter data classes

## Scope Boundaries

- No heat management, formations, combat stances, or special abilities
- No concussive, beam, or thermal weapons — kinetic only
- No Magazine, Damage Control, Engineering, Signals systems
- No ship loadout screen — pre-defined scenario
- No visual hull polygon rendering — sprites only; polygon is for collision/hit detection
- Unlimited ammunition
- No natural HP recovery — disabled systems stay disabled in this slice

## Context & Research

### Relevant Code and Patterns

- **Actor hierarchy**: `Ship` (abstract) -> `PlayerShip`/`EnemyShip`, with `Turret` as `Child` actor, `Gun` state machine, `Bullet` actor. All in `features/game/impl/.../actors/`
- **Data classes**: `ShipSpec`, `GunData`, `BulletData` in `.../data/`. Follow the pattern of parameter data classes per `docs/game_engine_principles.md`
- **DI root**: `AppComponent.kt` creates all Kubriko managers and two Kubriko instances (`KUBRIKO_BACKGROUND`, `KUBRIKO_GAME`)
- **Scene setup**: `GameStateManager.startDemoScene()` creates ships with inline stats
- **MVI pattern**: `ViewModelContract<Intent, State, SideEffect>` in `:components:design`
- **Child positioning**: `Child.update()` reads `parent.body.position/rotation/scale` via `BoxBody.getRelativePoint()`
- **Utilities**: `BoxBodyUtils.getRelativePoint()`, `SceneOffsetUtils.rotate()`

### Critical Gap: CollisionManager Not Wired

The current `AppComponent.gameKubriko` does not include a `CollisionManager`. Must be
instantiated and added before any collision detection runs. **Manager ordering matters**:
`CollisionManager` must come AFTER `ActorManager` in the `Kubriko.newInstance()` parameter list
so collisions are checked against positions updated in the current frame.

### Critical: `isAlwaysActive` Override Required

Kubriko's `ActorManagerImpl` stops calling `Dynamic.update()` on actors outside the viewport
(unless `isAlwaysActive = true`). For a combat game with camera pan/zoom, ships and bullets
will routinely be offscreen. **All `Ship`, `Bullet`, and `InputController` actors must override
`isAlwaysActive = true`** to prevent:
- Bullets freezing offscreen instead of expiring via TTL
- Enemy ships going inert when off-camera
- Ships stopping mid-navigation when destination is off-viewport

### R22 Unlimited Ammunition

The existing `Gun` state machine tracks magazine capacity and reload cycles. To enforce unlimited
ammo (R22), set `GunData.magazineCapacity` to `Int.MAX_VALUE` in the demo scenario config. This
avoids modifying the Gun state machine while effectively removing ammo as a constraint.

### Kubriko Engine APIs (from source at `/Users/jez/dev/multiplatform/kubriko-main`)

| API | Key Methods |
|-----|-------------|
| `PolygonCollisionMask` | Constructor takes `vertices: List<SceneOffset>`, auto-generates convex hull, supports rotation |
| `CollisionResult` | `contact: SceneOffset`, `contactNormal: SceneOffset`, `penetration: SceneUnit` |
| `collisionMask.collisionResultWith(other)` | Public extension, returns `CollisionResult?` — must be called manually in `onCollisionDetected` |
| `ViewportManager` | `addToCameraPosition(Offset)`, `multiplyScaleFactor(Float)`, `setCameraPosition(SceneOffset)` |
| `PointerInputAware` | `onPointerPressed`, `onPointerReleased`, `onPointerDrag`, `onPointerZoom` callbacks |

## Key Technical Decisions

- **Custom physics in `Dynamic.update()`, not Kubriko's `PhysicsManager`**: PhysicsManager has
  gravity (default 9.81), automatic collision response (bounce/friction), and manages position on
  `PhysicsBody` separately from `BoxBody`. For a top-down zero-gravity space game where projectiles
  trigger game logic (not physics rebound), custom movement in `Dynamic.update()` is cleaner. This
  preserves the `Child` actor pattern (reads `parent.body.position`) without sync issues.
  (see origin: deferred question affecting R4-R7)

- **Dedicated `InputController` actor for gesture disambiguation**: An actor implementing
  `PointerInputAware` (added via `actorManager.add()`, not a Manager) that detects tap vs drag
  gestures and dispatches camera pan/zoom and ship selection/move orders. Replaces `PlayerShip`
  directly reading `pressedPointerPositions`. Must be an actor (not a Manager) because
  `PointerInputManagerImpl` dispatches events only to actors in the actor list.
  Note: `onPointerDrag(screenOffset)` receives a delta offset (despite the parameter name), which
  maps directly to `ViewportManager.addToCameraPosition()`.
  (see origin: deferred question affecting R8-R11)

- **Call `collisionResultWith()` in `Bullet.onCollisionDetected()`**: Since the callback only
  receives `List<Collidable>`, the bullet re-computes `CollisionResult` to get contact point and
  normal for armour resolution. Double-compute is acceptable at this scale.
  (see origin: deferred question affecting R12)

- **Over-penetration triggers when remaining damage > 0 after primary system absorption**: The
  projectile's remaining damage after the primary system absorbs its share (based on system density
  vs projectile armourPiercing) continues to the next systems in random order. Each subsequent
  system absorbs damage using the same density-vs-AP calculation. "Terminating armour hardness
  threshold" for exit over-penetration is the armour hardness of the hull edge nearest to the
  projectile's exit trajectory. (see origin: deferred question affecting R15)

- **`armourPiercing` is the single penetration stat**: Used both for the penetration check (AP vs
  armour hardness) and for internal damage absorption (system density vs AP ratio). No separate
  "penetration" stat — `armourPiercing` serves both roles.

- **Bridge disabling affects ALL targeting including enemy auto-select**: Consistent with Bridge
  owning fire control. A ship with disabled Bridge loses all target tracking — turrets go idle,
  no new targets acquired. (see origin: deferred question affecting R20, R25)

- **Bullet cleanup via TTL counter**: Simplest and most predictable. Each bullet has a
  `remainingLifetimeMs` decremented each frame; removed when <= 0.
  (see origin: deferred question affecting R13)

- **No natural HP recovery**: Disabled systems stay disabled. Damage Control system will add
  recovery in a future slice. (see origin: deferred question affecting R21)

- **Stat definitions as Kotlin data class constants**: Scenario is pre-defined, no runtime loading
  needed. External data files deferred to when a loadout/configuration screen is added.
  (see origin: deferred question affecting R30)

- **Tap individual ships for selection**: No "select all" shortcut in this slice. Player taps a
  ship to select, taps destination to move. (see origin: deferred question affecting R11)

- **Movement heuristic for "should I rotate?"**: Simple comparison — estimate time to rotate to
  forward-thrust alignment vs time to reach target from current heading with reduced lateral thrust.
  Pick the faster option. Tunable via parameters. (see origin: deferred question affecting R7)

## Open Questions

### Resolved During Planning

- **Physics approach**: Custom `Dynamic.update()` — see Key Technical Decisions
- **Input disambiguation**: Dedicated InputController manager — see Key Technical Decisions
- **Collision contact retrieval**: Manual `collisionResultWith()` call — see Key Technical Decisions
- **Over-penetration trigger**: Remaining damage > 0 after absorption — see Key Technical Decisions
- **Bridge + auto-targeting**: Bridge disabling stops all targeting — see Key Technical Decisions
- **Bullet cleanup**: TTL counter — see Key Technical Decisions
- **System recovery**: No recovery in this slice — see Key Technical Decisions
- **Multi-ship selection**: Tap individual ships — see Key Technical Decisions
- **Stat format**: Kotlin data class constants — see Key Technical Decisions
- **Rotation heuristic**: Time comparison heuristic — see Key Technical Decisions

### Deferred to Implementation

- **Exact polygon vertices for ship hulls**: Depends on sprite dimensions; define during
  implementation when sprites are loaded
- **Specific stat values for demo ships**: Tuning numbers (mass, thrust, armour hardness, weapon
  damage, system HP) to be set during implementation and iterated based on play feel

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not
> implementation specification. The implementing agent should treat it as context, not code to
> reproduce.*

```
Ship Model (per ship instance):
  HullPolygon ──── PolygonCollisionMask (vertices define ship shape, convex only)
  ArmourStats ──── { hardness, density } uniform across all edges
  CombatStats ──── { evasionModifier } used in hit checks
  InternalSystems ─ flat list of [Reactor, MainEngine, Bridge]
    each: { type, maxHp, currentHp, density, isDisabled, isDestroyed }
  PhysicsState ──── { mass (derived), velocity, angularVelocity }
  MovementConfig ── { forwardThrust, lateralThrust, reverseThrust, angularThrust }

Combat Pipeline (per bullet collision):
  Bullet hits Ship polygon
    ├─ Retrieve CollisionResult (contact point, normal)
    ├─ Hit Check: random + toHit vs evasion → miss? bullet continues
    ├─ Ricochet Check: angle of incidence vs armour hardness → ricochet? discard
    ├─ Penetration Check: projectile AP vs armour hardness → deflect? discard
    └─ Penetrating Hit:
         ├─ Determine arc (forward/rear/side) from impact point relative to ship center+facing
         ├─ Primary system: Bridge (forward) / MainEngine (rear) / Reactor (side)
         ├─ Apply damage: system absorbs based on density vs penetration
         └─ Over-penetration: remaining damage → random order through other 2 systems

Input Flow:
  PointerInputAware callbacks
    ├─ Short tap (press+release, <threshold distance/time)
    │     ├─ On ship → select that ship
    │     └─ On empty space → move selected ship to position
    ├─ Drag → camera pan via ViewportManager.addToCameraPosition
    └─ Pinch/scroll → camera zoom via ViewportManager.multiplyScaleFactor
```

## Implementation Units

### Phase 1: Data Model & Ship Systems Foundation

- [x] **Unit 1: Stat data classes and ship configuration**

  **Goal:** Define all parameter data classes for ship stats, armour, internal systems, and
  weapon/projectile properties. Create demo scenario ship configurations.

  **Requirements:** R3, R16, R17, R30

  **Dependencies:** None

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ShipConfig.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ArmourStats.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/InternalSystemSpec.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ProjectileStats.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/DemoScenarioConfig.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/Gun.kt` (add projectile stats to `GunData`)
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/data/ShipConfigTest.kt`

  **Approach:**
  - `ShipConfig` bundles hull vertices, armour stats, combat stats (including `evasionModifier`),
    movement config, internal system specs, and weapon slot definitions for a complete ship template
  - `ShipSpec` is expanded to include mass derivation from hull/armour/systems instead of just
    acceleration/deceleration/maxSpeed
  - `MovementConfig` replaces simple acceleration with directional thrust values:
    `forwardThrust`, `lateralThrust`, `reverseThrust`, `angularThrust`
  - `InternalSystemSpec` has: `type` enum, `maxHp`, `density`, `disableThreshold` (2/3 of maxHp).
    No `restartDelayMs` in this slice — disabled systems stay disabled
  - `ProjectileStats` has: `damage`, `armourPiercing`, `toHitModifier`, `speed`
  - `DemoScenarioConfig` defines the full scenario: 2 player ships (medium config), 3 enemy ships
    (1 fast/light, 1 slow/heavy, 1 medium) with varied weapon loadouts
  - All stat values are Kotlin constants in a single file, not scattered across actor constructors

  **Patterns to follow:**
  - Existing `ShipSpec` and `GunData` data class patterns
  - `docs/game_engine_principles.md`: no hardcoded game-affecting values in actors

  **Test scenarios:**
  - Happy path: mass derivation from ShipConfig components sums hull mass + armour mass + system
    masses correctly
  - Happy path: DemoScenarioConfig produces valid ship configs (non-zero mass, non-zero HP on all
    systems, non-zero thrust values)
  - Edge case: a ShipConfig with zero-mass armour still produces positive total mass from hull and
    systems

  **Verification:**
  - All game-affecting stat values originate from data classes, not inline constants in actors
  - Demo scenario config defines distinct ship archetypes with meaningfully different stat profiles

- [x] **Unit 2: Internal system model**

  **Goal:** Implement the internal system runtime state and damage/disable/destroy logic for
  Reactor, Main Engine, and Bridge.

  **Requirements:** R2, R16, R17, R18, R19, R20, R21

  **Dependencies:** Unit 1

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/InternalSystem.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/ShipSystems.kt`
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/systems/ShipSystemsTest.kt`

  **Approach:**
  - `InternalSystem` tracks runtime state: `currentHp`, `isDisabled`, `isDestroyed`.
    Exposes `takeDamage(amount): DamageAbsorbed`. No restart timer in this slice — disabled
    systems stay disabled
  - `ShipSystems` holds the flat list of three systems and provides:
    - `applyDamage(systemType, damage, penetration)` — applies damage, checks disable threshold
    - `isReactorDestroyed()` — the ship destruction trigger
    - `isPowered()` — false if reactor disabled/destroyed
    - `canMove()` — false if engine disabled/destroyed or not powered
    - `hasFireControl()` — false if bridge disabled/destroyed or not powered
    - `canReceiveOrders()` — false if bridge disabled/destroyed or not powered
  - Disabled state: triggered at 2/3 max HP damage. No natural recovery in this slice.
  - Power cascade: reactor disabled → all systems stop functioning (but retain their HP state)

  **Patterns to follow:**
  - `docs/game_engine_principles.md` presenter pattern: expose public state, limited mutation
    functions
  - System does not know about the ship or other systems — `ShipSystems` orchestrates interactions

  **Test scenarios:**
  - Happy path: system with 100 HP takes 30 damage, has 70 HP remaining, not disabled
  - Happy path: system with 100 HP takes 67 damage, becomes disabled (threshold = 2/3 = 66.7)
  - Happy path: system with 100 HP takes 100 damage, becomes destroyed
  - Happy path: reactor destroyed → `isReactorDestroyed()` returns true
  - Integration: reactor disabled → `isPowered()` false → `canMove()` false, `hasFireControl()` false
  - Integration: reactor intact but engine disabled → `canMove()` false, `hasFireControl()` true
  - Integration: bridge disabled → `canReceiveOrders()` false, `hasFireControl()` false, `canMove()` true
  - Edge case: damage applied to already-destroyed system is ignored
  - Happy path: `applyDamage` returns the amount of damage absorbed (based on system density)

  **Verification:**
  - All three system types can be damaged, disabled, and destroyed independently
  - Power cascade correctly propagates reactor state to other system queries

### Phase 2: Ship Hull, Collision, and Movement

- [x] **Unit 3: Ship hull polygon and armour**

  **Goal:** Replace `CircleCollisionMask` on ships with `PolygonCollisionMask`. Attach uniform
  armour stats. Wire `CollisionManager` into `AppComponent`.

  **Requirements:** R1, R3

  **Dependencies:** Unit 1

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` (polygon mask, armour stats)
  - Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (add CollisionManager)
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/HullDefinition.kt`
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/data/HullDefinitionTest.kt`

  **Approach:**
  - `HullDefinition` wraps hull polygon vertices and uniform `ArmourStats`
  - `Ship` constructor takes `HullDefinition` instead of just a `DrawableResource`
  - Replace `CircleCollisionMask` with `PolygonCollisionMask(vertices)` in `Ship`
  - Update `collisionMask.position` and `collisionMask.rotation` in `Ship.update()`
  - Add `CollisionManager.newInstance()` to `AppComponent` and include it in `gameKubriko`
    **after `actorManager`** in the parameter list (manager update order = parameter order)
  - Override `isAlwaysActive = true` on `Ship` to keep updating when off-viewport
  - Hull vertices are defined relative to ship center; sized to approximate the sprite silhouette.
    `PolygonCollisionMask` auto-generates a convex hull — concave shapes are not supported
  - Armour edge identification: given a contact point from `CollisionResult`, find nearest polygon
    edge by iterating vertices and computing point-to-line-segment distance

  **Patterns to follow:**
  - Existing `PolygonCollisionMask` usage in Kubriko's demo-physics examples
  - Existing `AppComponent` manager instantiation pattern

  **Test scenarios:**
  - Happy path: PolygonCollisionMask created from ship hull vertices produces a valid convex hull
  - Happy path: given a contact point on a known edge, edge identification returns the correct
    edge index
  - Edge case: contact point equidistant between two edges selects either consistently

  **Verification:**
  - Ships render and collide using polygon masks
  - CollisionManager is active in the game Kubriko instance

- [x] **Unit 4: Physics-based movement**

  **Goal:** Replace the current simple velocity movement with physics simulation featuring mass,
  directional thrust, momentum, and angular force.

  **Requirements:** R4, R5, R6, R7

  **Dependencies:** Unit 1, Unit 3

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt` (rewrite update/movement)
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/ShipSpec.kt`
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysicsTest.kt`

  **Approach:**
  - `ShipPhysics` encapsulates the physics state and integration logic, separated from Ship actor:
    - Tracks: `velocity: SceneOffset`, `angularVelocity: Float`, `mass: Float` (derived)
    - `applyThrust(direction, magnitude, deltaMs)` — applies force relative to ship facing
    - `applyAngularForce(torque, deltaMs)` — applies rotational force
    - `integrate(deltaMs)` — semi-implicit Euler: velocity += force/mass * dt, position += velocity * dt
  - `Ship.update()` delegates to `ShipPhysics` and a navigation controller:
    1. Determine desired movement given destination and current state
    2. Decide whether to rotate first or thrust from current heading (time comparison heuristic)
    3. Apply appropriate thrust (forward-biased: forwardThrust >> lateralThrust)
    4. Apply angular force to rotate toward desired heading
    5. Integrate physics state
    6. Update `body.position`, `body.rotation`, `collisionMask.position/rotation`
  - Navigation deceleration: calculate stopping distance from current velocity and available
    deceleration thrust; begin decelerating when within stopping distance
  - Mass is computed once from `ShipConfig` at construction (hull + armour + systems)
  - Movement gated on `ShipSystems.canMove()` — disabled engine means no thrust/rotation
  - Remove the existing `updateFacing()` and `updateMovement()` methods; replace entirely

  **Patterns to follow:**
  - Kubriko's `PhysicsBody` semi-implicit integration pattern (velocity += force/mass * dt,
    position += velocity * dt) but without the full PhysicsManager overhead
  - Existing `Dynamic.update(deltaTimeInMilliseconds)` contract

  **Test scenarios:**
  - Happy path: ship with zero destination and zero velocity remains stationary
  - Happy path: ship facing target applies forward thrust, accelerates, and decelerates to stop at
    target
  - Happy path: ship facing away from target rotates to face target before applying forward thrust
    (when rotation is faster than lateral movement)
  - Happy path: directional thrust asymmetry — forward acceleration is significantly faster than
    lateral
  - Happy path: ship with higher mass accelerates slower than lighter ship given same thrust
  - Edge case: ship at destination with residual velocity decelerates to rest
  - Edge case: ship with disabled engine applies no thrust and drifts on current velocity
  - Integration: mass correctly derived from hull + armour + systems config values

  **Verification:**
  - Ships visibly accelerate, coast with momentum, and decelerate to rest at target positions
  - Heavier ships feel sluggish; lighter ships feel nimble
  - Ships rotate to face movement direction before thrusting forward
  - Both desktop and Android show consistent movement behavior

### Phase 3: Input, Camera, and Orders

- [x] **Unit 5: Input controller with gesture disambiguation**

  **Goal:** Create a centralized input controller that distinguishes tap from drag, dispatches
  camera pan/zoom, and exposes tap events for ship selection and movement orders.

  **Requirements:** R8, R9, R10, R11

  **Dependencies:** Unit 3 (CollisionManager wired)

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/input/InputController.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/PlayerShip.kt` (remove direct pointer reading)
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` (register InputController)
  - Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (provide InputController)
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/input/InputControllerTest.kt`

  **Approach:**
  - `InputController` is a plain actor (not a Manager) implementing `PointerInputAware`,
    added via `actorManager.add()`. Must override `isAlwaysActive = true`. Registered as an actor
    because `PointerInputManagerImpl` dispatches events only to actors in the actor list
  - Gesture detection:
    - Track pointer press position and time
    - `onPointerReleased`: if distance from press < threshold AND time < threshold → emit tap event
    - `onPointerDrag`: delegate to `ViewportManager.addToCameraPosition()`
    - `onPointerZoom`: delegate to `ViewportManager.multiplyScaleFactor()`
  - Tap handling:
    - Convert tap screen position to scene coordinates via `Offset.toSceneOffset(viewportManager)`
    - Check if tap hits a ship (point-in-polygon test via `PolygonCollisionMask.isSceneOffsetInside`)
    - If tap hits a player ship → select it (store reference in InputController)
    - If tap hits empty space and a ship is selected → issue move order to selected ship
  - `PlayerShip` no longer reads `pressedPointerPositions` directly; movement orders come through
    a public `moveTo()` method called by InputController
  - Selection state: `InputController` holds `selectedShip: PlayerShip?` and exposes it as a
    `StateFlow` for UI indicators

  **Patterns to follow:**
  - Kubriko's `PointerInputAware` callbacks
  - Existing `Offset.toSceneOffset(viewportManager)` usage in `PlayerShip`

  **Test scenarios:**
  - Happy path: quick press-and-release within thresholds registers as tap
  - Happy path: press-and-drag beyond distance threshold does NOT register as tap
  - Happy path: tap on player ship selects it; subsequent tap on empty space issues move order
  - Happy path: tap on different player ship changes selection
  - Happy path: drag gesture pans camera position
  - Happy path: pinch/scroll gesture changes zoom level
  - Edge case: tap on enemy ship does not select it
  - Edge case: tap on empty space with no ship selected does nothing

  **Verification:**
  - Camera pan and zoom work smoothly on both desktop (mouse) and Android (touch)
  - Tapping a ship selects it; tapping a destination moves it
  - No false move orders during camera pan gestures

### Phase 4: Kinetic Damage Pipeline

- [x] **Unit 6: Kinetic projectile impact resolution**

  **Goal:** Implement the full kinetic hit chain: hit check -> ricochet -> penetration ->
  arc-based damage routing to internal systems.

  **Requirements:** R12, R14, R15, R16

  **Dependencies:** Unit 2, Unit 3

  **Files:**
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/KineticImpactResolver.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouter.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt` (collision handling)
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/Gun.kt` (pass ProjectileStats to bullets)
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/KineticImpactResolverTest.kt`
  - Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/ArcDamageRouterTest.kt`

  **Approach:**
  - `KineticImpactResolver` is a stateless utility that takes projectile stats, collision result,
    and target ship state, and returns an `ImpactOutcome` (miss / ricochet / deflect / penetrating
    hit with damage breakdown)
  - Impact chain:
    1. **Hit check**: `random(0..1) + projectile.toHitModifier` vs `target.evasionModifier` —
       miss if below threshold
    2. **Ricochet check**: calculate angle of incidence from `CollisionResult.contactNormal` and
       bullet velocity vector. Compare against armour hardness — shallow angle + high hardness =
       ricochet
    3. **Penetration check**: `projectile.armourPiercing` vs `armour.hardness` with small random
       variance — fails to penetrate if AP is insufficient
    4. **Penetrating damage**: pass to `ArcDamageRouter`
  - `ArcDamageRouter`:
    1. Calculate impact point relative to ship center and facing
    2. Determine arc: angle from ship forward vector to impact direction
       - Forward 90 degrees (±45 from forward) → Bridge
       - Rear 90 degrees (±45 from rear) → Main Engine
       - Side arcs → Reactor
    3. Apply damage to primary system; system absorbs based on `density / projectile.penetration`
       ratio
    4. If remaining damage > 0 → distribute to remaining two systems in random order
  - `Bullet.onCollisionDetected()`: first check target's destroyed flag (prevents processing
    impacts against ships destroyed by other bullets in the same collision pass), then call
    `collisionMask.collisionResultWith(target.collisionMask)` to get contact data, then delegate
    to `KineticImpactResolver`. Note: `CollisionResult.contact` is in world space — subtract
    `ship.body.position` before determining the impact arc
  - `Bullet.collidableTypes` set to `listOf(EnemyShip::class)` for player bullets and
    `listOf(PlayerShip::class)` for enemy bullets

  **Patterns to follow:**
  - `docs/game_engine_principles.md` — impact system is a separate concern from ship/modules;
    ships present values, impact system determines outcomes
  - Existing `collisionMask.collisionResultWith()` extension in Kubriko collision plugin

  **Test scenarios:**
  - Happy path: projectile with high toHit vs low evasion → hit registered
  - Happy path: shallow angle of incidence + high armour hardness → ricochet
  - Happy path: projectile with low AP vs high hardness → deflection (no penetration)
  - Happy path: projectile penetrates → damage routed to correct arc-based system (forward hit →
    Bridge damage)
  - Happy path: rear hit → Main Engine damage
  - Happy path: side hit → Reactor damage
  - Happy path: over-penetration — primary system absorbs partial damage, remaining goes to other
    two systems
  - Edge case: projectile with toHit just below evasion → miss
  - Edge case: impact exactly on arc boundary (45 degrees) → deterministic assignment to one arc
  - Edge case: primary target system already destroyed → damage goes to remaining systems

  **Verification:**
  - Projectiles that hit ships produce visible damage to internal systems
  - Different impact angles route damage to different systems
  - The full chain (hit → ricochet → penetration → damage) runs without errors

- [x] **Unit 7: Bullet lifecycle management**

  **Goal:** Add TTL to bullets, remove them when expired or when they score a hit.

  **Requirements:** R13

  **Dependencies:** Unit 6

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/Gun.kt` (add TTL to BulletData/GunData)

  **Approach:**
  - Add `remainingLifetimeMs: Int` to Bullet, initialized from a TTL value in projectile config
  - Override `isAlwaysActive = true` on `Bullet` so offscreen bullets still expire via TTL
  - Decrement in `Bullet.update()`. When <= 0, call `actorManager.remove(this)`
  - On successful collision (non-miss in impact resolution), also remove the bullet
  - On miss, bullet continues (may hit other ships)
  - On ricochet or deflection, bullet is destroyed (disintegrates)

  **Test expectation:** None — pure lifecycle wiring with no complex logic. Verified by
  observation in the running game.

  **Verification:**
  - Bullets disappear after a fixed time if they don't hit anything
  - Bullets disappear on ricochet, deflection, or penetrating hit
  - No actor accumulation in prolonged combat

### Phase 5: Enemy AI and Ship Destruction

- [x] **Unit 8: Enemy AI — target selection and movement**

  **Goal:** Enemy ships autonomously select the nearest player ship as target, move to weapon
  range, and fire.

  **Requirements:** R24, R25, R26

  **Dependencies:** Unit 4, Unit 6

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/EnemyShip.kt`
  - Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ai/EnemyAI.kt`

  **Approach:**
  - `EnemyAI` encapsulates target selection and movement decision logic, separate from the actor
  - Target selection: find nearest `PlayerShip` in the actor list that `isValidTarget()`. Gated
    on `ShipSystems.hasFireControl()` — if Bridge disabled, no re-targeting (keeps last target
    until Bridge comes back, but loses accuracy/tracking)
  - Movement: calculate a position at weapon range from the target, offset to the side to avoid
    head-on collision. Set this as the movement destination
  - Re-evaluate target periodically (not every frame — every ~500ms to avoid thrashing)
  - Enemy turrets auto-fire using existing Gun state machine when target is set
  - Each enemy ship configuration has different stats (from DemoScenarioConfig) producing varied
    behavior: fast ships close quickly, heavy ships lumber in slowly

  **Patterns to follow:**
  - Existing `Ship.setTarget()` and `Ship.moveTo()` methods
  - Same physics movement system as player ships (R24)

  **Test scenarios:**
  - Happy path: enemy with multiple player targets selects the nearest one
  - Happy path: enemy moves to a position at weapon range from target, not on top of target
  - Happy path: enemy re-targets when current target is destroyed
  - Edge case: enemy with disabled Bridge retains last target but does not acquire new ones
  - Edge case: all player ships destroyed → enemy has no valid target, stops moving

  **Verification:**
  - Enemy ships autonomously navigate toward player ships and engage
  - Different enemy configurations produce visibly different combat behavior
  - Enemy ships use the same physics movement as player ships

- [x] **Unit 9: Ship destruction and removal**

  **Goal:** Ships whose reactor is destroyed explode and are removed from the scene.

  **Requirements:** R18, R29

  **Dependencies:** Unit 2, Unit 6

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Ship.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`

  **Approach:**
  - After damage is applied to internal systems, `Ship` checks `shipSystems.isReactorDestroyed()`
  - If reactor destroyed: mark ship as destroyed, notify `GameStateManager`, call
    `actorManager.remove(this)` to remove ship and its child actors (turrets)
  - `GameStateManager` tracks active player and enemy ships for win/lose checking
  - `Ship.isValidTarget()` returns false when destroyed (prevents targeting during removal frame)
  - A ship with disabled engine and bridge but intact reactor continues to exist: drifting on
    current velocity, turrets idle, unresponsive to orders

  **Patterns to follow:**
  - Existing `actorManager.remove()` / `actorManager.add()` patterns in `GameStateManager`

  **Test scenarios:**
  - Happy path: reactor destroyed → ship removed from scene
  - Happy path: ship with destroyed reactor is no longer targetable
  - Happy path: ship with disabled engine+bridge but intact reactor remains alive (drifts)
  - Integration: destroying a ship removes its child turret actors too

  **Verification:**
  - Ships explode and disappear when reactor is destroyed
  - Drifting disabled ships remain in the scene as obstacles

### Phase 6: Game Loop Completion

- [x] **Unit 10: Win/lose conditions and result screen**

  **Goal:** Track fleet state, detect victory or defeat, and display a result screen with restart
  option.

  **Requirements:** R27, R28

  **Dependencies:** Unit 9

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameVM.kt`
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameScreen.kt`

  **Approach:**
  - `GameStateManager` maintains lists of active player/enemy ships. When a ship is destroyed,
    it's removed from the relevant list. After each destruction, check:
    - Enemy list empty → emit `GameResult.Victory`
    - Player list empty → emit `GameResult.Defeat`
  - `GameStateManager` exposes `gameResult: StateFlow<GameResult?>` collected by `GameVM`
  - `GameVM` updates `GameState` with result; `GameScreen` shows a simple overlay with result text
    and a "Restart" button
  - Restart: `GameStateManager` clears all actors, resets state, and calls `startDemoScene()` again
  - Pause the simulation (`StateManager.updateIsRunning(false)`) when result is shown

  **Patterns to follow:**
  - Existing `GameState` / `GameIntent` / `GameSideEffect` MVI pattern in `GameVM`
  - Existing `GameStateManager.setPaused()` and `startDemoScene()`

  **Test scenarios:**
  - Happy path: all enemy ships destroyed → Victory result emitted
  - Happy path: all player ships destroyed → Defeat result emitted
  - Happy path: restart resets scene to initial state and resumes simulation
  - Edge case: simultaneous last-ship destruction (both fleets' last ship dies same frame) →
    deterministic result (Defeat takes priority — player's ship destruction is checked first)

  **Verification:**
  - Victory screen appears when all enemies are destroyed
  - Defeat screen appears when all player ships are destroyed
  - Restart works cleanly without stale state

- [x] **Unit 11: Demo scenario wiring and integration**

  **Goal:** Wire everything together: create the demo scenario from `DemoScenarioConfig`, update
  `GameStateManager.startDemoScene()`, and validate end-to-end on desktop and Android.

  **Requirements:** R26, all (integration)

  **Dependencies:** All previous units

  **Files:**
  - Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt`
  - Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (ensure all new managers/dependencies wired)

  **Approach:**
  - Rewrite `startDemoScene()` to use `DemoScenarioConfig`:
    - Create 2 player ships with medium configuration
    - Create 3 enemy ships: 1 fast/light (high speed, low armour, small weapons), 1 slow/heavy
      (low speed, high armour, large weapons), 1 medium (balanced)
    - Position fleets on opposite sides of the battlefield
    - Set up enemy AI for each enemy ship
  - Ensure `CollisionManager`, `InputController`, and all new dependencies are properly injected
    through AppComponent
  - Validate: launch on desktop, launch on Android, verify all systems work together

  **Test expectation:** None — integration wiring and manual validation.

  **Verification:**
  - Game launches cleanly on both Desktop and Android
  - Player can pan/zoom camera, select ships, and issue move orders
  - Ships move with physics-based momentum; rotation before thrust is visible
  - Turrets track and fire at enemies; impacts produce hit/ricochet/penetrate outcomes
  - Enemy ships autonomously engage
  - Destroying all enemies shows victory; losing all player ships shows defeat
  - Restart works

## System-Wide Impact

- **Interaction graph:** InputController dispatches to ViewportManager (camera) and Ship actors
  (orders). Bullet collision triggers KineticImpactResolver which calls ArcDamageRouter which
  modifies ShipSystems. ShipSystems state changes affect Ship movement (canMove), Turret targeting
  (hasFireControl), and GameStateManager (reactor destruction → ship removal → win/lose check).
- **Error propagation:** Bullet collision failures (no CollisionResult) should be treated as misses,
  not crashes. Ship destruction during collision resolution must not cause concurrent modification
  of actor lists — defer removal to end of frame.
- **State lifecycle risks:** Ship removal during collision iteration. Mitigate by marking ships as
  destroyed immediately but deferring `actorManager.remove()` to after collision processing
  completes. GameStateManager checks win/lose after removal, not during.
- **API surface parity:** Both player and enemy ships use identical movement physics, collision
  handling, and damage pipelines. No separate code paths.
- **Unchanged invariants:** Background Kubriko instance, splash screen, landing screen, navigation,
  preferences, and audio systems are unaffected. The game feature's api module contracts
  (`GameScreenEntry`, `GameSessionState`, `GameLoadingStatus`) remain unchanged.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| PolygonCollisionMask performance with many bullets | Bullet count is bounded by weapon fire rate and TTL. At demo scale (<10 ships, ~50 bullets max) this is not a concern. Monitor if scaling up. |
| Double-compute of CollisionResult | Acceptable at demo scale. If performance-critical later, extend CollisionManager to pass results through. |
| Movement tuning feel | Stats are data-driven (Unit 1); iterate values without code changes. Start with exaggerated differences between ship types for clear feedback. |
| Child actor (Turret) position sync after physics rewrite | Custom physics writes to `body.position` directly (same as before), so Child continues to read parent.body correctly. No sync issue. |
| Collision detection not working | Currently no CollisionManager — must be wired in Unit 3. Easy to miss; flagged explicitly. |

## Documentation / Operational Notes

Per `docs/ai_workflows.md` Workflow 3 step 6 and `docs/game_engine_principles.md`, document new
game systems during implementation. This is not a separate unit — documentation happens as part of
each unit that introduces new systems:
- Document the kinetic impact resolution chain and arc-based damage routing
- Document assumptions baked into implementation in `docs/tech-debt.md` under `Assumptions`

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-03-combat-vertical-slice-requirements.md](docs/brainstorms/2026-04-03-combat-vertical-slice-requirements.md)
- Related code: `features/game/impl/src/commonMain/kotlin/.../actors/Ship.kt`, `Bullet.kt`, `Turret.kt`, `Gun.kt`
- Related code: `composeApp/src/commonMain/kotlin/.../di/AppComponent.kt`
- Design docs: `docs/ship.md`, `docs/weapons.md`, `docs/game_design.md`, `docs/game_engine_principles.md`
- Kubriko source: `/Users/jez/dev/multiplatform/kubriko-main` (collision, physics, pointer-input, viewport plugins)
