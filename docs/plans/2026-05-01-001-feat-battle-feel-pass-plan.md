---
title: "feat: Battle-feel pass — cruiser-class slice"
type: feat
status: active
date: 2026-05-01
origin: docs/brainstorms/2026-04-29-battle-feel-requirements.md
---

# Battle-feel pass — cruiser-class slice

## Overview

Land roadmap item C as a single cruiser-class slice. Commits the SceneUnit → metres ratio (1 SU = 1 m), rebases cruiser-class physics + projectile stats to physically-plausible numbers, introduces projectile drag with velocity-aware penetration (and threads `currentPenetration` through to the existing `KineticImpactResolver` so the drag-aware value actually reaches the hit path during C, not just E), adds a per-weapon effective-range check to AI firing (a small behaviour addition the brainstorm absorbed at D7), wires camera zoom bounds + initial centroid + initial zoom, and ships a debug overlay (distance rings + mouse readout + FPS, gated on `DevToolsGate`) for empirical tuning. The demo scene gets rebuilt as a 2v2 cruiser engagement at ~3 km separation.

Fighter and battleship class tuning are explicitly deferred to follow-up items per the brainstorm's narrowed scope. The Compose-side pan + zoom input already works through `InputController` — the input layer is *not* rewritten in C; only the camera bounds + initial state are added. Snap-to-ship hotkeys are deferred to G alongside the keyboard-input bridge.

**Integration notes.** Modifies `ProjectileStats` (game-core/api) by adding two optional fields with defaults — backward-compatible JSON deserialisation. Mutates `Bullet.velocity` from `val` to `var` for drag integration; adds `var currentPenetration: Float`; threads it into the existing `KineticImpactResolver.resolve` call. Rebases values in `enemy_heavy.json` and `turret_guns.json`. Rebases cruiser-relevant `ShipNavigator` constants (Slice A formula unchanged). Adds zoom-bound parameters to `ViewportManager.newInstance` in `AppComponent`. Adds a `DebugOverlay` Compose composable in `GameScreen`. Rewrites `DemoScenarioPreset.SLOTS` and re-baselines `DemoScenarioPresetParityTest`. No new modules; no DI graph changes.

## Problem Frame

See origin: `docs/brainstorms/2026-04-29-battle-feel-requirements.md`. The combat prototype runs at an unspecified spatial scale; every physics constant, projectile speed, AI engagement distance, and camera default has been hand-tuned in a vacuum. Items D/E/F/G all need a stable scale + cruiser-class substrate to land against. C delivers that substrate plus a debug overlay so future tuning sessions are empirical instead of guesswork.

## Requirements Trace

- **R1.** Scale baseline 1 SU = 1 m, with float-precision sanity check + asset-rescaling audit table — see origin D1. Lands in Units 1 (audit table prose), Unit 5 (physics rebase pass), Unit 3 (turret rebase).
- **R2.** Cruiser-class engagement at 3–5 km — see origin D2. Validated empirically via Unit 9 (demo) + the self-playtest exit criterion.
- **R3.** Manual camera with 200 m–15 km zoom bounds + initial centroid + initial zoom ~3 km visible — see origin D3. **Lands in Unit 7.**
- **R4.** Cruiser-class physics rebase to ~1 m/s² with baseline-feel capture — see origin D4. Lands in Unit 5.
- **R5.** Projectile drag (numerically-stable `exp(-k·dt)` integrator) + velocity-threshold expiration + linear penetration formula + `lifetimeMs` retained as safety cap — see origin D5. Lands in Units 2 + 3.
- **R6.** C/E boundary respected: C ships drag + linear `currentPenetration` and *threads it through to the resolver*; E owns the penetration↔armour math (glancing/penetrating taxonomy, hit-event VFX) — see origin D6.
- **R7.** Cruiser-class AI range-aware firing + per-weapon range check + `ShipNavigator` braking tuning — see origin D7. Lands in Units 4 + 6.
- **R8.** Demo scene rebuilt as 2v2 cruiser at 3 km, parity test re-baselined — see origin D8. Lands in Unit 9.
- **R9.** Debug overlay (distance rings + mouse readout + FPS, gated on `DevToolsGate.isAvailable`) — see origin D9. **Lands in Unit 8.**

## Scope Boundaries

- **Fighter and battleship class tuning** — separate follow-up items.
- **Hull vertex rescaling** — deferred. Current `enemy_heavy.json` keel is ~104 m × 100 m, an unrealistically square footprint for a cruiser (real cruisers are ~150 m × 20 m), but the silhouette doesn't block physics or AI tuning at the gameplay level. Vertex rescaling lands as a cosmetic polish item later.
- **Snap-to-ship hotkeys / keyboard input** — deferred to G alongside selected-ship UI.
- **Right-mouse-drag pan distinction** — deferred to G; existing `InputController` already pans on drag, which is fine until G introduces ship-select on left-drag.
- **Hit-event taxonomy + VFX** — item E.
- **Penetration ↔ armour resolution math** (glancing/penetrating/ricochet decision logic on top of `currentPenetration`) — item E. C ships only the linear `currentPenetration = armourPiercing * (v/v0)` value at hit time.
- **Player-perspective AI improvements + status UI markers** — item G.
- **Terrain placement** — item F.
- **Quadratic projectile drag** (`F = ½ρv²C_dA`) — linear-velocity exponential decay is the prototype simplification per origin D5.
- **Per-turret behaviour-shape changes beyond range-gating** — adversarial concerns about minimum-engagement-arc responses or CIWS-class behaviours are deferred to G.

**Note on the "tuning + camera work only" roadmap brief.** Unit 4 (per-turret range gate) is a small behaviour addition the origin brainstorm explicitly absorbed at D7; it supersedes the roadmap's earlier "tuning only" framing. Unit 2's `currentPenetration` threading through the resolver is similarly a *plumbing addition* to make the value reach the hit path — not an armour-resolution change (which stays in E).

## Context & Research

### Relevant Code and Patterns

- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ProjectileStats.kt` — `@Serializable` data class to extend with drag fields. Existing fields: `damage`, `armourPiercing`, `toHitModifier`, `speed`, `lifetimeMs`. The field `armourPiercing` plays the role of the brainstorm's `basePenetration` — no rename in the codebase; the names refer to the same quantity.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt` lines 38, 45, 70–80, 103–113 — projectile actor; `velocity` is `val` and needs to become `var`; the existing collision path at line 107 calls `KineticImpactResolver.resolve(velocity = velocity, projectileStats = projectileStats, ...)`. Threading `currentPenetration` happens here.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/KineticImpactResolver.kt` — currently reads `projectileStats.armourPiercing` directly. Unit 2 changes this to accept `currentPenetration: Float` as a parameter and use that instead. No taxonomy logic changes (E owns that).
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Turret.kt` — fires regardless of distance today. New range check goes in `update()` before delegating to `Gun.update()`.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/data/Gun.kt` — `GunData`. Cached `effectiveRangeM` derived from `ProjectileStats.effectiveRangeM()` at `Turret` construction.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt` — Slice A drag braking already shipped (`forwardDragCoeff`, `stoppingDistanceUnderDrag`, `terminalVelocity`). Cruiser-class tuning rebases the closed-form's input constants, not the formula.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/input/InputController.kt` — already pans on `onPointerDrag` (`addToCameraPosition`) and zooms on `onPointerZoom` (`multiplyScaleFactor`). **No new input code in C.**
- `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` line 209 — `ViewportManager.newInstance` is called with logging params only; no zoom bounds. Bounds must be added.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` — `startScene` already iterates spawn slots in a Result-loop pattern (Item B Unit 2a). The post-spawn-loop initial-centroid call lands at the bottom of `startScene`, before `lastLaunched = slots`.
- `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameScreen.kt` — `Box{ KubrikoViewport(...); overlays }` pattern. Debug overlay inserts as a sibling composable inside the same `Box`.
- `components/shared/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/utils/export/DevToolsGate.kt` — gating flag (already shipped item B). Inject into `GameVM` to seed `state.canShowDebugOverlay`.
- `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json` — the cruiser-class ship; rebase target. **Verify Keel `lift` value before rebasing system masses** to avoid making the ship unflightworthy (`mass <= lift` is enforced by `evaluateSpawnGate`).
- `components/game-core/api/src/commonMain/composeResources/files/turret_guns.json` — three weapon types (`turret_standard`, `turret_light`, `turret_heavy`), all 150–250 SU/s muzzle, 3–5 s lifetime. Rebase target.
- `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPreset.kt` — `SLOTS` constant; rewrite for 2v2 cruiser at 3 km.
- `components/game-core/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPresetParityTest.kt` — re-baseline against new positions + designs. The test has three assertion blocks (count + team distribution, designNames, positions); all three need updates.
- `components/game-core/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/shipdesign/BundledAssetVersionTest.kt` — verified to assert only `formatVersion == ShipDesign.CURRENT_VERSION` and `name != null` for ship JSONs. **Does not pin projectile stats**, so `turret_guns.json` rebase is test-safe; ship JSON value changes are also test-safe (only schema changes would break it).

### Institutional Learnings

- `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md` — Slice A's lesson is **"one dimensionless boundary constant absorbs the unit mismatch."** Apply the same shape to projectile drag — keep `dragK` as the single named constant per weapon at the formula boundary; don't let drag tuning bleed into the integration loop.
- `docs/ai_kubriko_constraints.md` — **don't trigger runtime side effects from unstable composition recomposition paths.** Camera-state-from-input must live in a stable holder; the existing `InputController` path satisfies this. The debug overlay (Unit 8) reads camera state via `collectAsStateWithLifecycle` on `ViewportManager.cameraPosition: StateFlow<SceneOffset>` — read-only, recomposition-safe.

### External References

None. Codebase patterns + Slice A precedent cover the work.

## Key Technical Decisions

- **`dragK` is a per-projectile constant in `ProjectileStats`**, not a per-weapon override or a global. Each `turret_guns.json` entry sets its own `dragK` to produce the desired effective range. Default `0f` means no drag (legacy behaviour preserved).
- **Numerically-stable drag integration: `velocity *= exp(-dragK * dt)`** (with `dt` in seconds). The naive linear form (`velocity *= (1 - dragK * dt)`) was rejected — flips sign at slow frames or post-pause resume.
- **Effective range — single closed-form formula used everywhere:**
  ```
  effectiveRangeM = (muzzleSpeed / dragK) * (1 - expirationVelocityFraction)
  ```
  This is the integrated distance from `v(t) = v0·exp(-k·t)` between `t = 0` and `t = -ln(expFrac)/k`:
  ```
  ∫₀^T v0·exp(-k·t) dt = (v0/k) · (1 − exp(-k·T))
                       = (v0/k) · (1 − expFrac)
  ```
  The naive `T · v0` (= `-ln(expFrac) * v0 / k`, the distance at constant muzzle speed for the same flight time) is wrong because it ignores velocity decay. **Use the integrated form.** Unit 1, Unit 3's tuning derivation, and Unit 4's cached value all use this single formula.
- **Inverse — picking `dragK` for a target range:** rearrange to `dragK = muzzleSpeed * (1 - expirationVelocityFraction) / desiredRange`. For `muzzleSpeed = 600`, `expFrac = 0.3`, `desiredRange = 3500`: `dragK = 600 * 0.7 / 3500 ≈ 0.120`.
- **Velocity-based expiration is primary; `lifetimeMs` is secondary.** Projectile expires when current speed drops below `expirationVelocityFraction * muzzleSpeed` (default 30 %) OR `lifetimeMs` runs out. Both checks fire each frame. `lifetimeMs` defaults to a sensibly large value (e.g. 30 s) for new drag-aware projectiles; existing `lifetimeMs` values stay valid for legacy projectiles with `dragK = 0f`.
- **`currentPenetration = armourPiercing * (currentSpeed / muzzleSpeed)`**. Linear in velocity ratio. Stored on `Bullet` as `var currentPenetration: Float`. **In C this value is threaded through the existing `KineticImpactResolver.resolve` call** (replaces the resolver's direct read of `projectileStats.armourPiercing`) so drag-aware penetration actually applies during C. *The brainstorm uses the term `basePenetration` for what the codebase calls `armourPiercing` — these refer to the same quantity; there is no rename.* E owns any future formula change (glancing curves, step-functions for penetrating-vs-glancing); the C/E coupling is acknowledged.
- **Per-turret effective-range check is added in `Turret.update()`**, not `Gun.update()`. The check uses `currentTarget.distanceTo(turret.body.position) <= effectiveRangeM` to decide whether to fire. `effectiveRangeM` is computed once at `Turret` construction from `ProjectileStats.effectiveRangeM()` — projectile stats don't mutate, so the cache is stable.
- **Cruiser-class rebase is data-only** — `enemy_heavy.json` movementConfig + system masses change; structure unchanged. **Verify the rebased `totalMass` still satisfies `totalMass <= totalLift`** before merge — `evaluateSpawnGate`'s flightworthiness check would otherwise silently drop the ship from the demo.
- **Camera bounds wire through `ViewportManager.newInstance(minimumScaleFactor = ..., maximumScaleFactor = ...)` in `AppComponent`** with values **derived from the actual game-window pixel width at construction time**, not hard-coded against a 1920-wide reference. The Compose-Desktop window can resize at runtime, so bounds are recomputed via a Compose `LocalWindowInfo` / `LocalDensity` adapter accessible at `AppComponent` injection time. Worst-case fallback: `minimumScaleFactor = 0.05f, maximumScaleFactor = 20.0f` — wide enough to span 200 m–15 km on any reasonable resolution; the actual visible-range *intent* is documented but not enforced past these guard rails. **The debug overlay's distance ring radii use the same `LocalWindowInfo` source so they stay correct under window resize.**
- **Initial camera state set by `GameStateManager.startScene` after the spawn loop completes.** Computes `centroidOf(playerShips)`; calls `viewportManager.setCameraPosition(centroid)` and `viewportManager.setScaleFactor(...)` for ~3 km visible at the current window width.
- **Debug overlay is Compose-side**, not Kubriko-actor-side. Lives in `GameScreen.kt`'s `Box`, layered over `KubrikoViewport`. Reads `viewportManager.cameraPosition` and `viewportManager.scaleFactor` via `collectAsStateWithLifecycle()` on the Kubriko-exposed `StateFlow`s — recomposition-safe, no writes from recompose. Mouse position captured via `Modifier.pointerInput { awaitPointerEventScope { ... } }` **without calling `event.changes.forEach { it.consume() }`** so `KubrikoViewport`'s pointer handling still receives pan/zoom events unchanged.
- **Demo composition: 2 cruisers per team, ±1500 SU on X, ±200 SU offset on Y per pair** = 3 km between teams, 400 m intra-team. `enemy_heavy` for both teams; team assignment via `teamId` only.
- **Self-playtest commitment is captured under "Exit Criteria"**, not as a numbered implementation unit. It's a process gate, not a code deliverable, and a checkbox unit would muddy completion tracking.

## Open Questions

### Resolved During Planning

- **OQ5 (right-mouse-drag pan):** *Resolved.* `InputController.onPointerDrag` already pans on any drag; no left/right discrimination needed in C. When G adds ship-select, raw `PointerEvent` filtering can land alongside the keyboard bridge.
- **OQ6 (cruiser ship JSON authoring):** *Resolved.* `enemy_heavy.json` is already `shipClass = "cruiser"`. C rebases its values rather than authoring a new ship.
- **Per-weapon `effectiveRangeM`:** *Resolved.* Computed at `GunData` / `Turret` construction from `ProjectileStats.effectiveRangeM()` using the integrated-distance formula above.
- **`lifetimeMs` default for new drag-aware projectiles:** *Resolved.* Stays per-weapon in `turret_guns.json`. Cruiser turret-standard/heavy/light get 15–30 s safety caps after rebase; well above expected drag-driven expiration time.
- **Camera bounds derivation:** *Resolved.* Use actual window width via `LocalWindowInfo` rather than hard-coded reference. Wide guard-rail bounds (`0.05f – 20.0f`) prevent pathological values on edge cases.
- **Baseline-feel capture mechanism:** *Resolved.* Saving a Scenario in B doesn't capture pre-rebase ship stats — the scenario stores slot positions and `designName: "enemy_heavy"`, not the JSON values; reloading after rebase pulls the new values. The actual capture is **in-session subjective comparison**: dev plays the demo at current cruiser values, applies the rebase, recompiles, plays again. The "baseline" is a written 1–2-paragraph note in `docs/playtest-notes/` capturing the dev's memory of the pre-rebase feel.
- **`currentPenetration` threading in C:** *Resolved.* `KineticImpactResolver.resolve` accepts `currentPenetration` as a parameter (replacing its direct read of `projectileStats.armourPiercing`). C ships drag-aware penetration end-to-end; E layers hit-event taxonomy on top.

### Deferred to Implementation

- **Exact `dragK` per weapon.** Pick starting values from `dragK = muzzleSpeed * (1 - expFrac) / desiredRange`, then tune empirically.
- **Exact rebased cruiser MovementConfig values.** Physical-plausibility starting point (~1 m/s² accel from ~50 t total mass implies ~50,000 N total thrust); tune via B.
- **AI orbit-radius tuning** — the brainstorm OQ3 (pursue vs break-off) recommendation is "pursue until killed or fuel-exhausted." Cruiser-class default: pursue indefinitely. Tune via empirical sessions.
- **Debug overlay's mouse-readout polling rate.** `awaitPointerEventScope` fires per frame on cursor move; no extra rate-limiting needed for ~60 fps.
- **Scale-audit table values for ships outside cruiser scope** (player_ship, enemy_light, enemy_medium). Surface dimensions for documentation only — those classes are tuned in their own follow-up items, not C.

## Implementation Units

- [x] **Unit 1: `ProjectileStats` schema migration**

**Goal.** Add the optional drag fields to `ProjectileStats` with serialization defaults so existing JSON deserialises unchanged. Add a derived helper for muzzle-speed-derived effective range using the *correct* integrated formula. Pure data-model change; no behavioural impact on its own.

**Requirements.** R5 (drag fields), R6 (C/E penetration formula stays in C).

**Dependencies.** None.

**Files:**
- Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ProjectileStats.kt`
- Test: `components/game-core/api/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/data/ProjectileStatsSerializationTest.kt`

**Approach:**
- Add two new optional fields with defaults: `val dragK: Float = 0f`, `val expirationVelocityFraction: Float = 0f`. Both default to 0f, which means "no drag, no velocity-based expiration" — preserving existing behaviour for any caller that omits them.
- Document the formula in a class-level KDoc: `velocity(t) = muzzleSpeed * exp(-dragK * t)`; expiration when `currentSpeed < expirationVelocityFraction * muzzleSpeed`.
- Add a derived helper:
  ```
  fun ProjectileStats.effectiveRangeM(): Float =
      if (dragK > 0f && expirationVelocityFraction > 0f)
          (speed / dragK) * (1f - expirationVelocityFraction)   // integrated distance
      else
          speed * lifetimeMs / 1000f                             // legacy fallback
  ```
  *(directional sketch — implementer chooses the exact Kotlin idiom)*

**Patterns to follow.**
- `ShipDesign.kt` — `@Serializable` data class with companion-object version constant pattern.
- Item B's `Scenario.kt` — adding optional fields with serialization defaults for backward-compat.

**Test scenarios:**
- *Happy path (legacy JSON deserialises unchanged):* a JSON blob with only the original 5 fields round-trips through `Json.decodeFromString` to a `ProjectileStats` with `dragK = 0f`, `expirationVelocityFraction = 0f`.
- *Happy path (new fields round-trip):* a JSON blob with all 7 fields round-trips identity-preserving.
- *Edge case (effectiveRangeM with drag):* `ProjectileStats(speed = 600f, lifetimeMs = 30000, dragK = 0.6f, expirationVelocityFraction = 0.3f).effectiveRangeM()` returns `(600 / 0.6) * (1 - 0.3) = 700f`. Tolerance `1e-3f`.
- *Edge case (effectiveRangeM legacy fallback):* `ProjectileStats(speed = 200f, lifetimeMs = 4000, dragK = 0f, expirationVelocityFraction = 0f).effectiveRangeM()` returns `800f`.
- *Edge case (effectiveRangeM with dragK > 0 but expFrac = 0f):* falls through to legacy branch (defensive — both fields must be set to enable drag-aware range).

**Verification.**
- `./gradlew :components:game-core:api:allTests` passes.
- `./gradlew :components:game-core:api:compileDebugKotlinAndroid :components:game-core:api:compileKotlinJvm` succeeds.

---

- [x] **Unit 2: `Bullet` drag integration + velocity-based expiration + `currentPenetration` threading**

**Goal.** Bullet projectile decays its velocity each frame, exposes a `currentPenetration` value derived from current speed, expires on velocity-threshold (primary) or lifetime (secondary), AND **threads `currentPenetration` through the existing `KineticImpactResolver.resolve` call** so drag-aware penetration actually reaches the hit path during C. Backward-compatible: bullets with `dragK = 0f` behave exactly as today.

**Execution note.** Test-first. The drag integration is pure math, trivially testable in isolation; write the assertions before changing `Bullet`.

**Requirements.** R5, R6.

**Dependencies.** Unit 1.

**Files:**
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Bullet.kt`
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/combat/KineticImpactResolver.kt` — change `resolve(...)` to accept `currentPenetration: Float` (replacing the internal read of `projectileStats.armourPiercing`).
- Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/BulletDragTest.kt`

**Approach:**
- Change `private val velocity: SceneOffset` (constructor parameter) to `private var velocity: SceneOffset`. Cache `private val muzzleVelocity: SceneOffset = velocity` and `private val muzzleSpeed: Float = muzzleVelocity.length()` at construction.
- In `update(deltaTimeInMilliseconds)`, before the existing position integration:
  - If `projectileStats.dragK > 0f`, multiply `velocity` by `exp(-projectileStats.dragK * deltaTimeInSeconds)`.
  - Compute `currentSpeed = velocity.length()`.
  - If `projectileStats.expirationVelocityFraction > 0f && currentSpeed < projectileStats.expirationVelocityFraction * muzzleSpeed`: `actorManager.remove(this); return` — primary expiration.
  - Existing `remainingLifetimeMs` countdown stays as the secondary safety cap.
  - Update `currentPenetration = projectileStats.armourPiercing * (currentSpeed / muzzleSpeed)`.
- Existing `body.position += velocity * 0.001f * deltaTimeInMilliseconds` line stays — uses the decayed `velocity` automatically.
- In `onCollisionDetected` (line 107-ish), update the `KineticImpactResolver.resolve(...)` call to pass `currentPenetration = currentPenetration` and remove any `armourPiercing` arg the resolver currently reads from `projectileStats`. The `KineticImpactResolver.resolve` signature changes accordingly.

**Technical design** *(directional, not implementation specification)*:

```
Bullet.update(dt_ms):
    dt_s = dt_ms / 1000
    if dragK > 0:
        velocity *= exp(-dragK * dt_s)
    currentSpeed = velocity.length()
    currentPenetration = armourPiercing * (currentSpeed / muzzleSpeed)
    if expirationFraction > 0 and currentSpeed < expirationFraction * muzzleSpeed:
        remove(); return
    remainingLifetimeMs -= dt_ms
    if remainingLifetimeMs <= 0:
        remove(); return
    position += velocity * dt_s

Bullet.onCollisionDetected(collidables):
    ...
    KineticImpactResolver.resolve(
        velocity = velocity,             // decayed value reaches the resolver
        currentPenetration = currentPenetration,  // NEW — replaces resolver's internal read
        ... other params unchanged
    )
```

**Patterns to follow.**
- `ShipNavigator.stoppingDistanceUnderDrag` — closed-form-with-zero-drag-fallback (Slice A precedent).
- Item A's `RepoExporterImpl.export` — backward-compatible-with-defaults pattern.

**Test scenarios:**
- *Happy path (zero drag, legacy behaviour):* `Bullet` with `dragK = 0f`. Drive `update(16)` for 100 frames. Velocity unchanged frame-to-frame; position moves linearly; lifetime drives expiration.
- *Happy path (drag decay):* `muzzleSpeed = 600f`, `dragK = 0.6f`. After 1 second of `update` calls, velocity magnitude is `600 * exp(-0.6) ≈ 329 m/s`. Tolerance `1e-1f`.
- *Edge case (velocity-threshold expiration):* `dragK = 0.6f`, `expFrac = 0.3f`, `muzzleSpeed = 600f`, `lifetimeMs = 30_000`. After ~2.0 s (when speed crosses 180 m/s), `actorManager.remove(this)` fires. Lifetime hadn't expired.
- *Edge case (effective range matches Unit 1 formula):* drive bullet to expiration; final `body.position.length()` ≈ `(600/0.6) * (1 - 0.3) = 700m`. Tolerance 5%. **Pins the integrated formula matches behaviour.**
- *Edge case (lifetime safety cap):* `lifetimeMs = 1000`. Lifetime fires before drag would trigger; bullet expires at 1 s.
- *Edge case (currentPenetration tracking):* `armourPiercing = 100f`, `muzzleSpeed = 600f`. At construction, `currentPenetration == 100f`. After velocity decays to 50%, `currentPenetration ≈ 50f`.
- *Edge case (high `dt` numerical stability):* one big `update(2000)` — velocity remains positive; drag correctly applied.
- *Integration (KineticImpactResolver receives decayed value):* spawn a Bullet, decay its velocity to 50%, simulate a collision via a fake target. Assert the `KineticImpactResolver.resolve` call site received `currentPenetration ≈ 50f` (not 100f). This pins the threading.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- Manual run: existing combat scenario (post-Unit 9 demo) shows projectiles slowing visibly mid-flight; surviving projectiles deal less damage at long range than at close range.

---

- [x] **Unit 3: Cruiser turret drag tuning + projectile rebase**

**Goal.** `turret_guns.json` projectile values are rescaled from current ~150–250 SU/s × ~3–5 s lifetime (effective range ~750 m) to muzzle ~500–700 m/s with drag-driven effective range ~3–4 km. `dragK` and `expirationVelocityFraction` populated for cruiser-relevant turret types.

**Requirements.** R1 (rescale), R5, R7 (per-weapon range derivation).

**Dependencies.** Units 1, 2.

**Files:**
- Modify: `components/game-core/api/src/commonMain/composeResources/files/turret_guns.json`
- Verify: `BundledAssetVersionTest` does not pin projectile values (confirmed in Context).

**Approach:**
- Pre-rebase audit (current values): `turret_light` (250/3000ms), `turret_standard` (200/4000ms), `turret_heavy` (150/5000ms).
- Pick muzzle speeds: light ~700 m/s, standard ~600 m/s, heavy ~500 m/s. (Heavier projectiles slower, longer reach via lower drag.)
- Pick `dragK` per type using the **correct** inverse formula: `dragK = muzzleSpeed * (1 - expFrac) / desiredRange`. With `expFrac = 0.3`:
  - `turret_light` (desiredRange = 2500m, v0 = 700): `dragK = 700 * 0.7 / 2500 ≈ 0.196`.
  - `turret_standard` (desiredRange = 3500m, v0 = 600): `dragK = 600 * 0.7 / 3500 ≈ 0.120`.
  - `turret_heavy` (desiredRange = 4500m, v0 = 500): `dragK = 500 * 0.7 / 4500 ≈ 0.078`.
- `lifetimeMs` raised to 30 000 (30 s safety cap; well above drag-driven expiration time).
- `damage` and `armourPiercing` values unchanged for now (E will tune armour interactions; C just makes projectiles reach the right range and deliver `currentPenetration` correctly).
- `expirationVelocityFraction = 0.3f` for all three weapons (consistent design choice; tune per weapon if playtests reveal a need).

**Patterns to follow.**
- `ShipNavigator.stoppingDistanceUnderDrag`'s "single dimensionless boundary constant" approach — `dragK` is the per-weapon tuning handle.

**Test scenarios:**
- *Happy path (projectile reaches engagement range):* `BulletDragTest` constructs a Bullet with the new `turret_standard` values. Simulate update calls until expiration. Final position is approximately 3500 m from origin (within 5%). Confirms tuning math produces engagement-band projectiles.
- *Edge case (heavy reaches further than light):* `turret_heavy.effectiveRangeM() > turret_light.effectiveRangeM()`. (Heavy's lower `dragK` overrides its lower muzzle speed.)

**Verification.**
- `./gradlew :components:game-core:api:allTests` passes.
- `./gradlew :features:game:impl:allTests` passes.
- Manual run: cruiser-vs-cruiser at 3 km produces projectile arcs that reach but slow visibly approaching the target.

---

- [x] **Unit 4: Per-turret effective-range check (small AI behaviour addition)**

**Goal.** Turrets only fire when target is within their projectile's drag-aware effective range. Small *behaviour addition* — turrets currently fire at any distance regardless. Unit lands the range check using `ProjectileStats.effectiveRangeM()`.

**Requirements.** R7 (per-weapon range check; multi-weapon "fire each independently" already works because each Turret has its own update loop).

**Dependencies.** Units 1, 2, 3.

**Files:**
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/Turret.kt`
- Test: `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/actors/TurretRangeTest.kt`

**Approach:**
- In `Turret.update()`, before delegating to `Gun.update()` for fire decision, compute `targetDistance = currentTarget.body.position.distanceTo(turret.body.position).raw`. If `targetDistance > effectiveRangeM`, skip the fire delegation; turret can still rotate-toward-target.
- `effectiveRangeM` cached at Turret construction from `gun.gunData.projectileStats.effectiveRangeM()`. Stable for the projectile's lifetime (`ProjectileStats` is `@Serializable val` — immutable).

**Patterns to follow.**
- `Turret.firingEnabled` early-return pattern (drift-blocked → no fire). Mirror for the range check.

**Test scenarios:**
- *Happy path (in-range fire):* Turret with `effectiveRangeM = 3500`, target at 2000 m. `Gun.update` is called for fire decision.
- *Edge case (out-of-range no-fire):* Same turret, target at 5000 m. `Gun.update` is NOT called for fire. Turret still rotates.
- *Edge case (boundary):* target at exactly `effectiveRangeM`. Use `<=` (fires) for inclusive boundary; document.
- *Edge case (no target):* `currentTarget == null`. Existing no-target path stays.
- *Integration (no wasted fire at extreme range):* fire a Bullet that drag-expires at exactly the turret's effective range; verify the Bullet doesn't reach the target.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- Manual run: cruisers fire visibly less aggressively at far distances; close-quarter engagements produce more shots per second.

---

- [x] **Unit 5: `enemy_heavy.json` cruiser physics rebase**

**Goal.** Rebase cruiser-class movement constants (`forwardThrust`, `lateralThrust`, `reverseThrust`, `angularThrust`, system masses) to produce ~1 m/s² acceleration when fully loaded. Pure data tuning. **The pre-rebase values get captured first as a written baseline-feel reference (in-session subjective comparison; saved scenarios cannot serve as the baseline because they don't store ship stats).**

**Execution note.** Characterization-first via in-session subjective memory. Before changing values: dev plays the demo at current cruiser values, writes 1–2 paragraphs in `docs/playtest-notes/2026-05-XX-cruiser-baseline.md` capturing the pre-rebase feel. Then applies rebase, recompiles, plays again, writes the post-rebase paragraphs. Both notes go in the same file (date-stamped at writing time).

**Requirements.** R4 (cruiser rebase + baseline-feel capture).

**Dependencies.** None.

**Files:**
- Modify: `components/game-core/api/src/commonMain/composeResources/files/default_ships/enemy_heavy.json`
- Create: `docs/playtest-notes/2026-05-XX-cruiser-baseline.md` (date-stamped)

**Approach:**
- Document current values as a doc comment in this plan unit before editing the JSON.
- Total cruiser mass = sum of system masses + hull mass. For `enemy_heavy`, sum to ~50–100 t (50,000–100,000 kg). Target `forwardThrust = total_mass * targetAccel ≈ 50,000 N` for ~1 m/s².
- Lateral / reverse / angular thrusts proportional. Lateral typically 0.3–0.5 of forward; reverse ~0.5; angular tuned for sensible turn rate.
- **Verify the new `MovementConfig` values still pass `evaluateSpawnGate` (`totalMass <= totalLift`) before committing** — if total mass exceeds Keel lift, the cruiser silently drops from the demo. Read `enemy_heavy.json`'s Keel `lift` value first; pick system masses that respect it.

**Patterns to follow.**
- Slice A's "single constant at the formula boundary" — keep the rebase as a multiplier on existing values where possible, not a from-scratch rewrite.

**Test scenarios:**
- *Happy path (post-rebase cruiser flies):* simulate `Ship.update` for 5 s with full forward thrust; resulting velocity is approximately `targetAccel * 5 ≈ 5 m/s` (subject to drag from Slice A). Tolerance ~10%.
- *Edge case (spawn gate passes):* `evaluateSpawnGate(rebasedShipDesign, turretGuns)` returns `Ready`; rebased values don't break flightworthiness.
- *Manual baseline-feel comparison:* dev plays both pre-rebase and post-rebase cruiser scenarios; subjective notes captured in `docs/playtest-notes/2026-05-XX-cruiser-baseline.md`.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- `./gradlew build` succeeds.
- Manual run: cruiser ship feels heavy but maneuverable; doesn't accelerate like a fighter.
- The baseline-feel note exists in `docs/playtest-notes/` with both pre- and post-rebase paragraphs.

---

- [x] **Unit 6: `ShipNavigator` cruiser-class AI tuning**

**Goal.** Tune cruiser-class navigation constants — `ARRIVAL_THRESHOLD`, `BRAKING_MARGIN`, `PROXIMITY_RADIUS_FACTOR`, etc. — so that a cruiser approaches a target at scale, brakes at a sensible distance proportional to its (rebased) mass and the new effective range, and orbits at a tactical engagement distance. Slice A's `stoppingDistanceUnderDrag` formula stays unchanged — only the input values change.

**Requirements.** R7 (cruiser AI tuning).

**Dependencies.** Units 1, 2, 3, 4, 5 (so the rebased cruiser physics from Unit 5 are the inputs to the braking math).

**Files:**
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigator.kt`
- Test: extend `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/navigation/ShipNavigatorTest.kt` (create if absent).

**Approach:**
- Audit current `ARRIVAL_THRESHOLD`, `BRAKING_MARGIN`, `SLOW_SPEED_FACTOR`, `PROXIMITY_RADIUS_FACTOR`. Document current values in a comment.
- Tune constants for cruiser-class ships at engagement range 3–5 km. The "orbit engagement" target is *just inside* `effectiveRangeM` — compute as `0.8 * effectiveRangeM`, so cruisers orbit comfortably inside their own firing range.
- **Invariant check** (verified in tests): `orbitRadius < effectiveRangeM`. If `orbitRadius` exceeds `effectiveRangeM`, the AI orbits beyond firing range — perpetual approach with no fire. The 0.8 multiplier protects this; tests assert it explicitly.
- `ARRIVAL_THRESHOLD`: rescale to a fraction of cruiser engagement-band (e.g. 50–100 m).
- `BRAKING_MARGIN`: tighter (e.g. 1.1) — cruisers benefit from earlier braking due to mass.

**Patterns to follow.**
- `ShipNavigator.stoppingDistanceUnderDrag` — inputs change, formula unchanged.

**Test scenarios:**
- *Happy path (cruiser approaches and orbits in-range):* spawn a cruiser at 5 km from a target. After several seconds of `ShipNavigator.update` calls, ship is within `0.8 * effectiveRangeM` (e.g. 2.5–3.5 km).
- *Invariant (orbit < effective range):* assert `0.8 * effectiveRangeM < effectiveRangeM` — trivially true but documents the design rule.
- *Edge case (close-engagement, no overshoot):* cruiser starts at 100 m from target. ARRIVAL_THRESHOLD logic prevents bouncing or jittering.
- *Edge case (target out of reach):* AI still pursues — recommendation from origin OQ3 (now plan deferred-to-implementation).

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- Manual run: cruisers reach engagement distance and hold orbit; no overshoot or perpetual approach.

---

- [x] **Unit 7: Camera zoom bounds + initial centroid + initial zoom**

**Goal.** Wire `ViewportManager` constructor with explicit min/max zoom bounds (200 m–15 km visible *intent*; 0.05–20.0 absolute guard rails). Add post-spawn-loop call to centre the camera on the player-team centroid at ~3 km initial zoom. Bounds derived from actual window width via `LocalWindowInfo`.

**Requirements.** R3 (camera bounds + initial centroid + initial zoom).

**Dependencies.** None (independent of physics/AI/projectile work).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/di/AppComponent.kt` (`ViewportManager()` provider)
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManager.kt` (post-spawn-loop initial-centroid)
- Test: extend `features/game/impl/src/commonTest/kotlin/jez/lastfleetprotocol/prototype/components/game/managers/GameStateManagerTest.kt`

**Approach:**
- In `AppComponent.ViewportManager()`, pass `minimumScaleFactor = 0.05f` and `maximumScaleFactor = 20.0f` to `ViewportManager.newInstance(...)`. These are wide guard rails; the *intent* is 200 m–15 km visible at typical 1080p+ resolutions.
- Initial zoom set after construction in `GameStateManager.startScene` via `viewportManager.setScaleFactor(targetScale)` where `targetScale = windowWidthPx / 3000f` — a "3 km visible" framing. The window-width fetch happens via Compose `LocalWindowInfo` accessible at the screen layer; `GameStateManager` exposes a `setInitialZoom(viewportPxWidth: Int)` method called by `GameScreen` on first composition. *(Implementer chooses: pass via `GameVM` state, side-effect channel, or direct call.)*
- Add `private fun List<Ship>.centroid(): SceneOffset` helper inside `GameStateManager`. Computes `SceneOffset(meanX, meanY)` from `body.position`.
- After the spawn loop in `startScene` (before `lastLaunched = slots`), call `viewportManager.setCameraPosition(playerShips.centroid())`. If `playerShips.isEmpty()`, fall back to `SceneOffset.Zero`.

**Patterns to follow.**
- `GameStateManager.startScene` post-loop fallback (existing `if (startupResult != null)` block).
- Compose `LocalWindowInfo` for window-size queries (Compose Multiplatform 1.6+).

**Test scenarios:**
- *Happy path (camera centroid set):* spawn 2 player ships at (-1500, 0) and (1500, 0) via `startScene(slots)`. Post-spawn, `viewportManager.cameraPosition.x.raw == 0f` and `y == 0f`.
- *Edge case (single player ship):* 1 player ship at (-1000, 0). Centroid is (-1000, 0); camera matches.
- *Edge case (no player ships):* `playerShips.isEmpty()` → camera at origin; no NPE.
- *Test (zoom bounds clamp — exact values):* construct `ViewportManager` with `maximumScaleFactor = 9.6f`. Call `multiplyScaleFactor(1000f)`. Assert `scaleFactor.value == 9.6f` (exact, not just `<= max`). Mirror for under-zoom against `minimumScaleFactor`.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- `./gradlew build` succeeds.
- Manual run: scene starts with battlefield framed at ~3 km wide; mouse wheel zooms in/out within bounds; over-zoom is clamped silently.

---

- [ ] **Unit 8: Debug overlay (distance rings, mouse readout, FPS)**

**Goal.** Compose-side overlay above `KubrikoViewport` in `GameScreen`. Renders concentric distance rings at 1 km, 3 km, 5 km centred on the camera-view centre; mouse-cursor world-position readout in metres; FPS counter (frame-rate visibility for downstream perf-touching items). Gated on `DevToolsGate.isAvailable`.

**Requirements.** R9.

**Dependencies.** Unit 7 (camera state available to read).

**Files:**
- Create: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/DebugOverlay.kt`
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameScreen.kt`
- Modify: `features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/ui/GameVM.kt` (inject `DevToolsGate`, expose `canShowDebugOverlay` in `GameState`)

**Approach:**
- `DebugOverlay` is a `@Composable` that subscribes to `viewportManager.cameraPosition` and `viewportManager.scaleFactor` via `collectAsStateWithLifecycle()` — read-only, recomposition-safe, no writes from recompose.
- Distance rings: a `Canvas` overlay drawing three circles at 1 km, 3 km, 5 km radii centred on the camera-view centre. Convert metres → screen px via current scale factor + `LocalWindowInfo` width. Stroke style; semi-transparent.
- Mouse readout: `Modifier.pointerInput { awaitPointerEventScope { val event = awaitPointerEvent(); /* update mouse-screen-position state */ } }`. **Do NOT call `event.changes.forEach { it.consume() }`** — events propagate to children, so `KubrikoViewport`'s pointer handling still receives pan/zoom unchanged. Convert screen → world via inverse of viewport transform (camera position + scale factor); render as Compose `Text` in a corner.
- FPS counter: a `LaunchedEffect` averaging the last N frame deltas via `withFrameMillis`. Render in another corner.
- All wrapped in `if (state.canShowDebugOverlay) { DebugOverlay(...) }` in `GameScreen`'s `Box`.
- `GameState` gains `val canShowDebugOverlay: Boolean = false`. `GameVM.init` sets it from injected `DevToolsGate.isAvailable`.

**Patterns to follow.**
- Item B's `LandingVM` snapshot-once gate read into state.
- `GameScreen`'s existing `Box{ KubrikoViewport(...); pauseOverlay; resultOverlay }` stacking — debug overlay slots in as another sibling.
- Item A's `RepoExporter.isAvailable` → `state.canExport` flow.

**Test scenarios:**
- *Test (Boolean propagation):* `GameVMDebugOverlayTest` asserts `state.canShowDebugOverlay == devToolsGate.isAvailable` at construction.
- *Manual:* on Desktop with `lfp.repo.root` set, overlay visible; on Android (or Desktop without the gate), hidden.
- *Manual (pointer pass-through):* pan + zoom still work via mouse drag and wheel while the overlay is active.

**Verification.**
- `./gradlew :features:game:impl:allTests` passes.
- Manual run: on `./gradlew :composeApp:run`, overlay shows distance rings + mouse readout + FPS counter; pan/zoom still works; on Android the overlay is absent.

---

- [ ] **Unit 9: Demo scene rebuild — 2v2 cruiser at 3 km**

**Goal.** Rewrite `DemoScenarioPreset.SLOTS` to a 2v2 `enemy_heavy` engagement with player team at x ≈ -1500 SU and enemy team at x ≈ +1500 SU. Re-baseline `DemoScenarioPresetParityTest` against the new positions + designs.

**Requirements.** R8 (demo rebuild).

**Dependencies.** Unit 5 (cruiser values rebased — demo plays the rebased ship).

**Files:**
- Modify: `components/game-core/api/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPreset.kt`
- Modify: `components/game-core/api/src/jvmTest/kotlin/jez/lastfleetprotocol/prototype/components/gamecore/scenarios/DemoScenarioPresetParityTest.kt`

**Approach:**
- `SLOTS` becomes 4 entries: 2 player at (-1500, ±200) and 2 enemy at (+1500, ±200). All `designName = "enemy_heavy"`. `teamId` per side; `withAI = true` for enemy, `false` for player; `drawOrder` per existing constants.
- Update **all three** parity-test assertion blocks atomically: count + team distribution, designNames, positions. The test's index-based assertions (`SLOTS[0..3]`) all need updating in the same commit; CI fails between half-updates.

**Patterns to follow.**
- Item B's `DemoScenarioPresetParityTest` (Unit 7 from item B) — same shape, new values.

**Test scenarios:**
- *Happy path:* `DemoScenarioPreset.SLOTS.size == 4`, `count(player) == 2`, `count(enemy) == 2`, `all { it.designName == "enemy_heavy" }`, positions match the new 2v2 layout.
- *Drift detection:* manual edit of one position fails the test.

**Verification.**
- `./gradlew :components:game-core:api:allTests` passes.
- Manual run: clicking Play loads the new demo. Two cruisers per team are visible; cruisers approach and engage; debug overlay shows the 3 km ring framing the engagement.

---

## Exit Criteria

Beyond the implementation-unit checkboxes, C is not considered shippable until the following process commitments are met:

- **Self-playtest signal captured.** The dev plays at minimum 3 cruiser-vs-cruiser sessions at varied tunings (one demo, two custom scenarios via item B). Each session ~5–10 minutes. Subjective feedback captured in `docs/playtest-notes/2026-05-XX-cruiser-feel.md` (date-stamped at writing time): (a) what felt right, (b) what felt off, (c) which constants needed iterating most.
- **Baseline-feel comparison documented** (pre-rebase vs post-rebase cruiser feel) in `docs/playtest-notes/2026-05-XX-cruiser-baseline.md`. See Unit 5.
- **Production demo unchanged** in observable terms beyond what the brainstorm explicitly authorises (composition + scale change). The Result-loop spawn pattern + `lastLaunched` cache + `PendingScenario` consume-on-read all behave identically to item B.

These exit criteria are documentation/process artefacts; they have no compile-time or test-time enforcement. The discipline is the dev's commitment, not a gate the build can fail. The artefacts are what D, E, F, G's brainstorms reference as the cruiser-feel baseline going forward.

## System-Wide Impact

- **Interaction graph:** `ProjectileStats` schema gains 2 fields with defaults. `Bullet` mutates `velocity`; threads `currentPenetration` through `KineticImpactResolver`. `KineticImpactResolver.resolve` signature changes (accepts `currentPenetration: Float` instead of reading `armourPiercing` internally). `Turret` adds a range check before firing. `ShipNavigator` constants change values (formula unchanged). `ViewportManager` is constructed with new bounds; `GameStateManager.startScene` calls `setCameraPosition` once after spawn loop. `GameScreen` adds a sibling overlay composable. `GameVM` injects `DevToolsGate`. `DemoScenarioPreset.SLOTS` is rewritten; parity test re-baselined.
- **Error propagation:** `ProjectileStats.effectiveRangeM()` falls back to legacy lifetime-based calculation when drag fields are 0 — defensive default. `Turret`'s out-of-range path is a silent no-fire (target still tracked). `ViewportManager.setCameraPosition(SceneOffset.Zero)` is the empty-team fallback — no NPE.
- **State lifecycle risks:** `Bullet.velocity` mutation is per-frame; no cross-frame lifecycle. `currentPenetration` is a derived value. `DemoScenarioPreset.SLOTS` is `val`. The debug overlay reads camera state per recomposition via `collectAsStateWithLifecycle` (recomposition-safe); MUST NOT trigger camera-state writes from inside the overlay's recomposition. The mouse-readout `pointerInput` does not call `consume()`, so events still reach `KubrikoViewport`.
- **API surface parity:** `ProjectileStats` is `@Serializable`; consumed by `Bullet` + JSON loader; both accept new optional fields. `KineticImpactResolver.resolve` signature changes — search for all call sites; only `Bullet.onCollisionDetected` is the known consumer in this repo.
- **Integration coverage:** Unit 2 covers `Bullet` drag + threading in isolation. Unit 4 covers `Turret` range gate. Unit 6 covers `ShipNavigator` orbit invariant. Unit 9 covers `DemoScenarioPreset` shape. End-to-end (cruiser-vs-cruiser plays at 3 km, projectiles drag-decay, AI fires only in range, overlay shows correct rings) is verified via manual run + the Exit-Criteria self-playtest.
- **Unchanged invariants:** `Turret.firingEnabled` (drift-blocked path) stays. `ShipNavigator.stoppingDistanceUnderDrag` formula stays. `GameStateManager.startScene` spawn-gate loop stays. `RepoExporter` and item B's `DevToolsGate` behaviour are untouched. Existing `ShipBuilder` flows are untouched. `Scenario` / `SpawnSlotConfig` schema is untouched.

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Drag formula tuning produces an "uncanny valley" — projectiles fly too far or expire too quickly. | Closed-form math (`dragK = muzzleSpeed * (1 - expFrac) / desiredRange`) lands sensible starting values. Empirical tuning via B + the debug overlay produces convergence. Self-playtest exit criterion makes iteration explicit. |
| Cruiser physics rebase regresses feel below current hand-tuned baseline. | Baseline-feel capture in Unit 5 — pre-rebase note in `docs/playtest-notes/`; post-rebase compared in subjective writing. If rebased version is worse, escalate the dimensionless thrust-multiplier rather than reverting wholesale. |
| Per-turret range check (Unit 4) is a behaviour addition; existing AI tests might assume "always fires." | New `TurretRangeTest` covers behaviour explicitly. Existing `GameStateManagerTest` doesn't drive turret update loop (Item B Unit 2a precedent), so no regression there. |
| `KineticImpactResolver.resolve` signature change breaks call sites. | One known caller (`Bullet.onCollisionDetected`); search confirms. Update atomically in Unit 2. |
| `ViewportManager.newInstance` constructor signature differs from what the plan assumes. | Verified in research: signature includes `minimumScaleFactor`/`maximumScaleFactor` parameters. If API changes, fall back to `multiplyScaleFactor` calls post-construction. |
| Debug overlay's `pointerInput` consumes events and breaks `KubrikoViewport` pan/zoom. | Approach explicitly does not call `event.changes.forEach { it.consume() }`. Verify via manual smoke test (pan + zoom still work with overlay active). Worst case: mouse-readout becomes Desktop-only via `Modifier.onPointerEvent` (Desktop-only API). |
| Recomposition perf — overlay subscribed to camera `StateFlow` recomposes per frame during pan. | `collectAsStateWithLifecycle` + Compose smart-recomposition bounds the cost; only `Text` composables re-render and `Canvas` re-draws. On Android, profile if jank is observed; otherwise acceptable for dev-only overlay. |
| `enemy_heavy.json` rebase makes ship unflightworthy (`totalMass > totalLift`). | Unit 5's spawn-gate test asserts `evaluateSpawnGate(rebased) == Ready`. Implementer reads Keel `lift` value before picking new system masses. |
| `BundledAssetVersionTest` breaks when `enemy_heavy.json` values change. | Verified: the test asserts only `formatVersion` and `name != null`; value changes don't break it. |
| The exact window-width-derived camera bounds vary across resolutions. | Wide absolute guard rails (0.05f–20.0f) prevent pathological clamping. The "3 km visible" intent is documented; multi-resolution support remains a future polish item. |
| Empirical tuning takes longer than expected; C ships with sub-optimal cruiser feel. | Self-playtest exit criterion is the explicit gate. If 3 sessions don't produce a "feels right" judgement, C is not done. Better to slip than to ship a bad baseline. |

## Documentation / Operational Notes

- After C ships, update `docs/weapons.md` (or create `docs/projectiles.md`) with the drag mechanic and `currentPenetration` formula.
- Reference the cruiser-feel playtest note from the C ship commit message.
- `docs/ai_kubriko_constraints.md` may benefit from a "manual camera + pan/zoom + recomposition-safe debug overlay" entry once Unit 7+8 land.

## Sources & References

- **Origin document:** [`docs/brainstorms/2026-04-29-battle-feel-requirements.md`](../brainstorms/2026-04-29-battle-feel-requirements.md)
- **Roadmap:** [`docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md`](../brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md) (item C)
- **Prior atmospheric movement plan (Slice A — partially shipped):** referenced via `docs/brainstorms/2026-04-10-atmospheric-movement-requirements.md`. `ShipNavigator.stoppingDistanceUnderDrag` is the established drag-formula pattern.
- **Prior items shipped:**
  - Item A (asset export) — `docs/plans/2026-04-25-001-feat-asset-export-plan.md` provides `RepoExporter` / `DevToolsGate`.
  - Item B (scenario builder) — `docs/plans/2026-04-26-001-feat-scenario-builder-plan.md` provides empirical-tuning tooling + `DemoScenarioPreset` parity-test pattern.
- **Institutional learnings:**
  - `docs/solutions/best-practices/physics-tuning-patterns-kubriko-ship-movement-2026-04-17.md` — single dimensionless boundary constant for drag.
  - `docs/ai_kubriko_constraints.md` — recomposition + actor draw constraints.
- Related code: `features/game/impl/src/commonMain/kotlin/.../game/actors/Bullet.kt`, `Turret.kt`, `Gun.kt`, `combat/KineticImpactResolver.kt`, `navigation/ShipNavigator.kt`; `composeApp/src/commonMain/kotlin/.../di/AppComponent.kt`; `features/game/impl/src/commonMain/kotlin/.../game/ui/GameScreen.kt`, `GameVM.kt`; `components/game-core/api/src/commonMain/kotlin/.../gamecore/data/ProjectileStats.kt`.
