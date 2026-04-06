---
date: 2026-04-03
topic: combat-vertical-slice
---

# Combat Vertical Slice

## Problem Frame

The game's design docs describe a rich combat simulation with many interdependent systems (ships, armour, internal modules, weapons, heat, formations, orders). No playable combat loop exists yet — the current prototype has ships rendering with turrets that fire non-interacting bullets. Before building out the full system, we need a narrow vertical slice that proves the core combat pipeline end-to-end: ships move physically, weapons fire kinetic projectiles, projectiles interact with armour and damage internal systems, and ships can be destroyed.

## Requirements

**Ship Spatial Model**

- R1. Ships are modelled as a single convex polygon hull with armour segments mapped to hull edges. The polygon defines the ship's collision boundary and visual silhouette for hit detection.
- R2. Internal systems are tracked as a flat list per ship (no spatial positions within the hull). Damage routing uses arc-based targeting instead of line-tracing (see R14).
- R3. Ships have a uniform armour stat block (hardness, density) applied to all hull segments. Per-segment overrides are deferred until a consuming feature requires them (e.g., Radiator deployment).

**Physics-Based Movement**

- R4. Ship movement uses physics simulation: mass derived from hull, armour, and internal systems; momentum persists between frames.
- R5. Ships accelerate primarily in their forward-facing direction via Main Engine thrust. Lateral and reverse acceleration is significantly reduced but available (Main Engine covers aux engine functionality for this slice).
- R6. Ship rotation uses angular force. Positioning and facing matter — faster ships can meaningfully outmanoeuvre slower ones.
- R7. Ships navigate toward a target position, calculating whether rotating to a better facing would be faster than accelerating from current heading. Ships come to rest at the target position.

**Camera**

- R8. The camera supports free pan (drag) and zoom (pinch/scroll) across the battlefield. No auto-follow behavior.
- R9. Camera controls work on both desktop (mouse drag + scroll wheel) and Android (touch drag + pinch).

**Player Orders**

- R10. The player can tap a location on the battlefield to issue a move order to their ship(s). Selected ship(s) navigate to the tapped position using the physics movement system.
- R11. Ship selection mechanism: the player can tap a ship to select it, then tap a destination. Scope is limited to tap-to-move; no combat stance orders (advance, stay at range, disengage) in this slice.

**Weapons — Kinetic Only**

- R12. The existing turret and gun system is extended with kinetic projectile impact resolution. The chain is: hit check (random + to-hit vs evasion) -> ricochet check (angle of incidence vs armour hardness/slope) -> penetration check (projectile AP vs armour hardness) -> penetrating damage to internal systems.
- R13. Bullets have a limited lifespan and are removed when they leave the battlefield or after a time threshold.

**Damage Model**

- R14. Penetrating hits use arc-based system targeting: determine the impact point's position relative to the ship's center and facing. Forward 90-degree arc targets Bridge, rear 90-degree arc targets Main Engine, side arcs target Reactor.
- R15. Over-penetrating hits (remaining damage exceeds terminating armour hardness threshold) target the remaining two systems in random order.
- R16. Each internal system has hit points and a density value that determines how much penetrating damage it absorbs.

**Internal Systems (Simplified)**

- R17. Ships have three internal systems: Reactor, Main Engine, Bridge.
- R18. Reactor: if destroyed, the ship explodes (instant kill). If disabled, the ship loses power — all other systems stop functioning.
- R19. Main Engine: provides thrust in all directions (forward-biased). If disabled, the ship cannot accelerate or rotate.
- R20. Bridge: provides fire control (target selection, aiming accuracy) and command (receives player orders). If disabled, turrets lose target tracking and the ship ignores new orders.
- R21. Systems can be disabled when damaged to 2/3 of max HP. Disabled systems restart after a delay once damage is repaired below threshold (no Damage Control system in this slice — natural HP recovery or no recovery, deferred to planning).
- R22. Ammunition is unlimited. Weapons reload from an infinite pool; no Magazine system in this slice.
- R23. Heat management is excluded from this slice. No heat-related stubs or interfaces are required; heat will be added as a distinct follow-up feature.

**Enemy AI**

- R24. Enemy ships use the same physics movement system as player ships. They choose a target movement position relative to their nearest target (a position at weapon range from the target ship).
- R25. Enemy ships auto-select the nearest player ship as their target for both movement and weapons.
- R26. Different enemy ship configurations (speed, mass, weapon loadout) can be used to exercise the movement system and demonstrate varied combat behavior.

**Win/Lose Conditions**

- R27. The player wins when all enemy ships are destroyed. The player loses when all player ships are destroyed.
- R28. On win or loss, display a simple result screen with the option to restart the combat scenario.

**Ship Destruction**

- R29. A ship is destroyed when its Reactor is destroyed (explosion). Reactor destruction is the sole destruction trigger in this slice — a ship with a disabled Main Engine and Bridge but intact Reactor remains alive (drifting and unresponsive). Destroyed ships are removed from the scene.

**Data-Driven Stats**

- R30. All ship, weapon, armour, and system stat values are provided via parameter data classes, not hardcoded in actor implementations. This follows the game engine principle of having a single collection of stat definitions.

## Success Criteria

- A player can pan/zoom the camera, tap to move ships, and watch them navigate using physics-based movement with visible momentum and rotation.
- Turrets track and fire kinetic projectiles at enemy ships; projectiles interact with armour (ricochet, deflect, or penetrate) and damage internal systems.
- Destroying a ship's reactor causes it to explode; destroying all enemies or losing all player ships triggers a result screen.
- Enemy ships autonomously move into engagement range and fire back, using the same movement physics as player ships.
- The same scenario runs on both Desktop (JVM) and Android.

## Scope Boundaries

- No heat management system (deferred; design for future addition).
- No formation system — ships are individually ordered.
- No combat stance orders (advance, stay at range, disengage) — tap-to-move only.
- No concussive, beam, or thermal weapons — kinetic only.
- No Magazine, Damage Control, Engineering, Signals, or other internal systems beyond Reactor, Main Engine, Bridge.
- No special abilities.
- No ship configuration/loadout screen — scenario is pre-defined.
- No visual hull polygon rendering — ships use sprite assets. The convex polygon is for collision/hit detection only.
- Unlimited ammunition.

## Key Decisions

- **Hybrid spatial model**: Convex polygon hull with armour segments on edges, but internal systems as a flat list with arc-based damage routing. Balances tactical positioning feel against implementation scope, and can be upgraded to full spatial internals later.
- **Kinetic only**: Proves the core projectile-to-armour pipeline without needing beam, thermal, or concussive damage models. Each additional weapon type is additive once kinetic works.
- **Three internal systems (Reactor, Main Engine, Bridge)**: Minimum set that demonstrates power dependency, movement, and command/targeting. Bridge consolidates Fire Control and Command roles. Main Engine consolidates aux engine thrust. Keeps the system interaction graph small while proving the disable/destroy mechanics.
- **Arc-based damage routing**: Approximates positional damage without requiring spatial system layout. Impact arc determines primary target (Bridge forward, Engine rear, Reactor sides), with over-penetration hitting remaining systems randomly. Preserves the tactical value of attack angle.
- **Full physics movement**: Core to the game's identity — positioning and facing matter, fast ships outmanoeuvre heavy ones. Worth the implementation cost in the first slice because simplified movement would need to be replaced entirely.
- **Simple enemy AI using same movement system**: Validates movement physics from both sides and creates visually interesting engagements without complex AI. Different enemy configurations exercise varied movement capabilities.
- **Unlimited ammo, no heat**: Removes two resource management systems that add complexity without proving the core combat pipeline. Heat will be a distinct follow-up feature.

## Dependencies / Assumptions

- Kubriko's `PolygonCollisionMask` supports convex polygon collision (confirmed in Kubriko 0.0.7 source). Circle-to-polygon collision is also supported, so bullets can use `CircleCollisionMask` against polygon ship hulls.
- The existing Parent/Child actor relationship for turrets continues to work with the new physics movement model.
- Sprite assets exist or can be created for the ship types needed in the demo scenario.

## Outstanding Questions

### Deferred to Planning

- [Affects R4-R7][Technical] Physics approach: use Kubriko's `PhysicsManager`+`RigidBody` (with zero gravity, no collision response) or implement custom movement in `Dynamic.update()`. These are mutually exclusive — `PhysicsManager` updates position in its own loop, so a `RigidBody` that also moves in `Dynamic.update()` would double-apply. If using `PhysicsManager`, the existing `Child` actor for turrets reads `BoxBody.position` which may diverge from `PhysicsBody.position`.
- [Affects R8-R11][Technical] Input disambiguation: how to distinguish tap (move order / ship select) from drag (camera pan). Current `PlayerShip` treats every press as a move order. Needs gesture detection (press-and-release within time/distance threshold for tap vs. drag for pan).
- [Affects R12][Technical] Collision contact point retrieval: `onCollisionDetected()` only receives a list of collidables, not `CollisionResult` (which contains contact point, normal, penetration). The `collisionMask.collisionResultWith()` extension is public and can be called directly to re-compute contact data. Armour segment (edge) identification from contact point requires custom polygon-edge matching.
- [Affects R15][Technical] Over-penetration trigger: clarify whether over-penetration fires when the primary target system is fully destroyed and damage remains, or whenever remaining damage after absorption exceeds a threshold. Define "terminating armour hardness threshold" explicitly.
- [Affects R7][Technical] What's the best approach for the "should I rotate first?" movement calculation — analytical solution or heuristic with tuning parameters?
- [Affects R13][Technical] Bullet cleanup mechanism: time-to-live counter, distance check, or viewport-bounds check. Without cleanup, missed bullets accumulate as actors and degrade collision performance.
- [Affects R20, R25][Technical] Does Bridge disabling affect enemy auto-target selection (R25), or only player re-targeting? Clarify whether auto-select is a Bridge function or a fallback behavior.
- [Affects R21][Technical] Should disabled systems recover HP naturally over time in this slice, or should they stay disabled until a future Damage Control system is added?
- [Affects R11][Technical] How should multi-ship selection work for tap-to-move? Tap each ship individually, or a "select all" shortcut?
- [Affects R26][Technical] What specific ship configurations (number of ships, stat spreads, weapon loadouts) should the demo scenario use?
- [Affects R30][Technical] Should stat definitions live in Kotlin data class constants, or in external data files (JSON/TOML) loaded at runtime? (Kotlin constants are recommended for a pre-defined scenario.)

## Next Steps

-> `/ce:plan` for structured implementation planning
