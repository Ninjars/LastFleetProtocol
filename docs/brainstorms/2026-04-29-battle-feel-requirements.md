# Battle-Feel Pass — scale baseline, camera, physics rebase, projectile drag, AI retune (cruiser-class slice)

Created: 2026-04-29
Roadmap: item C (`docs/brainstorms/2026-04-24-core-gameplay-roadmap-requirements.md`)
Status: brainstorm complete (post-review)
Depends on: item B (scenario builder) shipped — `docs/plans/2026-04-26-001-feat-scenario-builder-plan.md`

## Problem

The combat prototype currently runs at an unspecified spatial scale. Every physics constant, projectile speed, AI engagement distance, and camera default has been hand-tuned in a vacuum — none of it is anchored to a real-world reference. Validating combat-balance changes (any future item), terrain scaling (item F), projectile-interaction tuning (item E), and player perception (item G) all need a committed scale baseline plus a camera the player can actually operate at the new scale. Without that, every downstream item is tuning against shifting ground.

This brainstorm settles the scale baseline and the directly-coupled tuning surface (camera, physics, projectiles, AI ranges) **for the cruiser ship class only** — far enough to unblock the first "is this fun?" playtest signal (per CCD-5 in the roadmap) and to give items D/E/F/G a stable substrate to land against. Fighter and battleship class tuning are deferred to a follow-up item once cruiser-vs-cruiser feel is locked.

## Goals

- **Commit to a SceneUnit ↔ metres ratio.** All downstream tuning derives from this.
- **Make the camera operable for multi-ship player teams.** Manual pan + zoom, no auto-follow. Bounds wide enough to support any future engagement range.
- **Rebase cruiser-class physics to physically-plausible accelerations.** Numbers stop being arbitrary; design space gets constraints.
- **Land projectile drag.** Velocity decays over flight; combat effectiveness dies with it.
- **Retune cruiser-class AI to engagement/firing/braking ranges that fit the new scale.** No new AI behaviors.
- **Ship a debug overlay for empirical tuning.** Without measurable distances, the physical-plausibility framing is unfalsifiable in practice.

## Decisions

### D1. Scale baseline: 1 SceneUnit = 1 metre

Locked. The existing ship polygons (e.g., `components/game-core/api/src/commonMain/composeResources/files/default_ships/player_ship.json` — vertices reach ±56 SU, ~112 m hull) already use a scale roughly compatible with this ratio. Zero conversion overhead in authoring tools, debug HUDs, or scenario composition. Resolves CCD-5.

The choice is for *authoring convenience and zero conversion overhead*, not externally-validated physical correctness. Existing JSON values (e.g. `turret_guns.json` `speed = 200`) become *interpretable* as physical quantities (200 m/s is plausible cannon-shell velocity) without claiming the values were originally derived from physics.

**Float precision sanity check.** Battleship engagement at 12 km = 12 000 SU. Float32 holds ~7 significant digits, so a 1 SU vertex offset on a polygon centred at 12 000 SU still resolves at 0.001 SU — well below visible jitter. Kubriko's `SceneOffset` exposes `Float`-backed `x.raw`/`y.raw` (verify in planning whether the type is float32 or float64; the calculation above assumes the worst case).

**Existing-asset rescaling audit (deferred to planning).** A small audit table — current SU dimensions of every bundled ship + projectile speed + thrust constants vs target physical-plausibility ranges — must land in the plan. Implementer should not have to reverse-engineer it. For cruiser-only scope, only the cruiser-class assets (currently absent from `default_ships/` — the existing JSONs are player_ship + 3 enemy classes) need authoring; the scale-audit covers what those baselines should be.

Rejected: 1 SU = 10 m (would force sub-unit polygon vertex precision and silently rescale every existing asset). Rejected: 1 SU = 5 m (inherits both ratios' cognitive overhead at lesser intensity).

### D2. Engagement-range targets — full-scope target, cruiser-only in C

The roadmap's long-term target spans three classes. C ships only the cruiser row.

| Class | Typical engagement range | Notes | In C? |
|---|---|---|---|
| Fighter (player-class) | < 1 km, up to ~2 km | CQB / dogfight feel | No — follow-up |
| **Cruiser** | **3–5 km** | **Mid-range turret salvos** | **Yes** |
| Battleship | 8–12 km | Long-range capital duels | No — follow-up (depends on F for terrain cover) |

A scenario can mix classes long-term; tactical asymmetry is intended (a fighter uses maneuverability + closing speed against a battleship's range). For C, the playtest target is **cruiser-vs-cruiser at 3–5 km separation**. Mixed-class scenarios are deferred until per-class tuning lands for fighter and battleship in subsequent items.

Cruiser-class AI firing/approach distances are derived from the ship's largest weapon's effective range — no global engagement constant.

### D3. Camera: manual pan + manual zoom, no auto-follow, no snap-to-ship

There can be multiple player ships on a team — a single follow-camera can't represent the team. The camera is fully manual:

- **Pan:** drag-with-secondary-gesture on Desktop (right-mouse-drag is the target UX, but verify implementation feasibility per Working Assumption #3 — fallback is left-drag if raw-event interception is too costly), one-finger-drag (touch). Two-finger touch gesture is reserved for future ship-command UI; pan is one-finger only.
- **Zoom:** mouse wheel (Desktop), pinch (touch). Bounds: ~200 m visible (close-up to one ship) to ~15 km visible (wide enough to fit future battleship-range engagements, even though C only ships cruiser tuning). Out-of-bounds wheel/pinch input is **hard-clamped silently** (no rubberband on Desktop; pinch may rubberband on touch if the platform default does so naturally).
- **Initial position on scene start:** centred on the player-team centroid; one line in `GameStateManager.startScene` after the spawn loop completes — `viewportManager.setCameraPosition(centroidOf(playerShips))`. **Initial zoom: ~3 km visible** — a default that frames cruiser engagements without requiring an immediate zoom action.
- **Camera state when player ships are destroyed mid-battle:** camera stays at its current position. No automatic ally-follow.
- **Camera state at game-over (all ships destroyed):** camera stays where it was when the last ship died. The game-over overlay sits on top regardless of camera position.

**Snap-to-ship hotkeys are deferred to G.** The roadmap's player-perspective + status-markers item (G) needs to introduce a "selected ship" concept and a keyboard input bridge anyway (Kubriko has no keyboard channel today — see Working Assumptions). Folding the snap-to-ship hotkeys into G's keyboard-bridge work avoids building input architecture twice. C does not commit to any keyboard input.

Auto-fit-to-engaged-target, preset zoom tiers, and the "follow currently-selected ship with manual override" pattern (the standard fleet-RTS approach) were all considered. Full-manual was chosen because (a) no current "selected ship" concept exists in the codebase, and (b) C's brief is camera + tuning, not selection UI. Follow-selected is a viable G follow-up if playtest signal demands it.

### D4. Physics tuning: rebase cruiser-class to physically-plausible m/s²

Existing MovementConfig values in the ship JSONs become interpretable as m/s² once D1 lands. Rebase **the cruiser class** to plausible numbers:

- **Cruiser:** ~1 m/s² acceleration. Slow accel, moderate top speed (~50–100 m/s after long burn). Inertia is a tactical fact — turning radius matters, headlong charges are committed.

Fighter-class (5–20 m/s²) and battleship-class (~0.1 m/s²) target ranges are documented for follow-up items. The cruiser class is the validation target for the physical-plausibility framing — if it produces good combat at 3–5 km, the framing is vindicated and follow-up items inherit the constraint shape.

Per-class final values are tuned empirically using item B's scenario builder (compose cruiser-vs-cruiser scenarios at varied accelerations, iterate) before C is considered shippable.

**Baseline-feel capture.** Before applying the rebase, record the *current* (pre-rebase) cruiser feel as a reproducible scenario in B (one cruiser engagement scenario at current values). After the rebase + tune, compare side-by-side. The criterion is *not* "feels identical" but "the tuned-physical version is at least as fun as the unconstrained version." Guards against rebase-driven regression.

### D5. Projectile drag and velocity-based expiration

Projectiles get a per-weapon drag coefficient. Each frame the velocity is integrated with a numerically-stable form (does NOT flip sign at high `dt`):

```
velocity *= exp(-dragCoefficient * dt)
currentPenetration = basePenetration * (velocity / muzzleVelocity)   // C's starting formula; see D6
```

The naive `velocity *= (1 - dragCoefficient * dt)` was rejected because it flips sign whenever `dragCoefficient * dt > 1` (slow frame, post-pause resume). The exponential form is always positive and frame-rate-stable. A simpler always-positive approximation is `velocity *= 1 / (1 + dragCoefficient * dt)`; pick whichever the implementer benchmarks more cheaply.

The drag model is **first-order linear in velocity**, not the physically-correct quadratic `F = ½ρv²C_dA`. The simplification is deliberate for a 2D prototype — quadratic drag is non-trivially harder to integrate stably and produces faster-than-intuitive falloff at high velocity that we can revisit if playtests demand it. Documented as a known simplification.

When `velocity < expirationThreshold` (default: 30 % of `muzzleVelocity`), the projectile expires — replacing the *primary* expiration mechanism. The existing `lifetimeMs` field is **kept as a safety cap** (projectile expires on whichever fires first) so existing JSON deserialises without migration; new projectiles can omit `dragCoefficient` (default 0 = no drag, lifespan-only behaviour preserved).

For C's cruiser-only scope, the relevant weapons are cruiser-class turret guns (~600–800 m/s muzzle velocity is a plausible starting band; tune in B). Fighter-class light cannons and battleship-class heavy cannons inherit the drag mechanic but their values are tuned in their respective follow-up items.

`currentPenetration` is computed in C and stored on the projectile; **its interaction with armour at hit-event time is owned by item E** — see D6.

### D6. C/E boundary for the projectile model

| Concern | Owner |
|---|---|
| Per-weapon drag coefficient | C |
| Projectile velocity decay (per-frame integration) | C |
| `currentPenetration` derivation from current velocity (starting formula: linear in `velocity / muzzleVelocity`) | C |
| Velocity-threshold expiration | C |
| `lifetimeMs` retained as safety cap; primary expiration is velocity-threshold | C |
| Penetration ↔ armour resolution at hit time | E |
| Hit-event taxonomy (glancing / penetrating / ricochet / overpenetrate) | E |
| Hit VFX (turret flash, glancing spark, penetration shower) | E |

C provides the *velocity-aware penetration value* on the projectile; E consumes it. **C's starting formula is linear** (`currentPenetration = basePenetration * velocity / muzzleVelocity`); E may revise the formula if its hit-event taxonomy demands a different curve, but the revision is a coordinated C+E change, not E-only. This coupling is acknowledged here so E's brainstorm doesn't assume a free hand.

### D7. AI retune: cruiser-class ranges, no new behaviors

The roadmap is explicit — C tunes; G adds player-perspective behaviour and status markers. C makes existing AI work at the new scale **for cruiser-class ships only**.

- **Engagement range** (the cruiser distance band in D2 is the *target*; AI's actual approach distance is **derived from** the ship's largest weapon's effective range, where "effective range" means the distance at which a projectile fired straight has decayed to D5's `expirationThreshold`). The two definitions describe the same quantity from different angles — D2 is the playtest-validated outcome; D7 is the runtime computation. Verify they converge during empirical tuning.
- **Firing range:** per-weapon, fires when target is within that weapon's effective range. **Multi-weapon ships fire each weapon independently** — verified: each `Turret` actor has its own `update` loop with independent firing decisions in `features/game/impl/.../game/actors/Turret.kt`. C's work is *tuning per-turret range checks*, not adding multi-weapon behavior.
- **Braking distance:** proportional to ship mass / inertia. **Note:** `ShipNavigator.stoppingDistanceUnderDrag` (atmospheric-movement plan, Slice A) already implements this — verify the formula scales correctly under D4's rebased mass values.

Behaviours like target prioritisation, formation flying, retreat decisions, minimum-arc responses for fighter-vs-battleship, and player-aware threat assessment are deferred to G.

### D8. Demo scene rebuilt as cruiser-vs-cruiser

`DemoScenarioPreset.SLOTS` (committed in item B) currently has player_ship + enemy_light/medium/heavy at ±300 SU separation. C rebuilds the demo as a **cruiser-vs-cruiser engagement at ~3–5 km separation** to match the cruiser-class playtest target. New cruiser ship JSONs are authored as part of C; existing fighter / enemy-class designs stay in the asset library but are not part of the demo.

Unit 7's `DemoScenarioPresetParityTest` gets re-baselined against the new cruiser composition. The parity test pattern is reusable across slot reshuffles — re-baselining is a one-line value update, not a test rewrite.

### D9. Debug overlay shipped in C

A debug-only HUD lands in C. Promoted from open question to hard requirement after three reviewers (design-lens, product-lens, scope-guardian) flagged that empirical tuning of D4/D5/D7 cannot validate physical-plausibility claims without measurable distances.

- **Distance rings** centred on the camera-view centre at 1 km, 3 km, and 5 km. Cruiser-engagement-range bracket; tunable list in code.
- **Mouse-cursor world-position readout** in metres (e.g. `(1234, -567) m`). Updates per frame.
- **FPS counter** in a corner.
- **Gating:** uses the same `DevToolsGate` from item B (`DevToolsGate.isAvailable` — true on Desktop with `lfp.repo.root` resolved). Hidden on Android and packaged Desktop builds.

The overlay is empirical-tuning infrastructure; it pays back its cost across C, the fighter/battleship follow-ups, item E (projectile-interaction tuning), and item F (terrain-scale validation).

## Working Assumptions

1. **Kubriko `ViewportManager` zoom + pan.** `multiplyScaleFactor` clamps to constructor-supplied min/max bounds, but `AppComponent` currently constructs the game `ViewportManager` *without* explicit bounds — engine defaults apply. Planning must wire the D3 200 m–15 km bounds via constructor params or a post-construction `setScaleFactor` call.
2. **Kubriko `onPointerDrag` has no mouse-button discriminator.** The D3 "right-mouse-drag pan on Desktop" requirement cannot be implemented through `PointerInputAware` directly — either intercept raw Compose `PointerEvent`s in the game screen Composable to filter by button before they reach the engine layer, or accept left-drag as the Desktop pan gesture. Pick in the plan.
3. **`TurretGun` / `ProjectileStats` JSON schema migration.** `ProjectileStats.lifetimeMs` is non-nullable today. D5's primary expiration is velocity-based, but `lifetimeMs` is *retained as a safety cap* — projectile expires on whichever fires first. New optional fields (`dragCoefficient: Float = 0f`, `expirationVelocityFraction: Float = 0f`) deserialise existing JSON unchanged.
4. **`ShipDesign` / `MovementConfig` JSON schema can absorb rebased per-class values without a schema bump** (values change; structure unchanged).
5. **Item B's scenario builder is sufficient for empirical tuning sessions** — compose cruiser-vs-cruiser scenarios at varied tunings, measure feel, iterate.
6. **`ShipNavigator` Slice-A already shipped.** `forwardDragCoeff`, `stoppingDistanceUnderDrag`, `terminalVelocity` exist in `ShipNavigator.kt` per the atmospheric-movement plan (2026-04-10). C does NOT re-implement the drag braking — D7's "braking distance" is a tuning rebase against Slice A's existing formula, not a new implementation. The planning audit should map which parts of Slice A are already shipped vs still pending.
7. **No keyboard input in C.** Snap-to-ship hotkeys deferred to G; C requires no `KeyboardInputBridge` and no `Modifier.onKeyEvent` integration.

## Out of Scope / Non-Goals

- **Fighter-class and battleship-class tuning** — separate follow-up items. D2 documents target bands; C only validates the cruiser row.
- **Mixed-class scenarios** — fighter-vs-cruiser, cruiser-vs-battleship, etc. Composable in B once each class lands; not a C playtest target.
- **Snap-to-ship hotkeys / keyboard input** — deferred to G alongside the selected-ship concept and command UI.
- **Hit-event taxonomy and VFX** — item E owns these.
- **Penetration ↔ armour math** — item E.
- **Player-perspective AI improvements + status UI markers** — item G.
- **Terrain placement** — item F.
- **Player-facing mission picker** — long-deferred per CCD-3.
- **Air drag / atmospheric model changes beyond projectiles** — `2026-04-10-atmospheric-movement-requirements.md` already governs ship aerodynamics; projectile drag is a parallel mechanic with its own constants, not a unification.
- **Joystick / gamepad camera support** — Desktop mouse + touch only; gamepad is a future polish item.

## Open Questions for Planning

- **OQ1.** Exact drag coefficients per cruiser-class weapon. Pick starting values from real-world ballistic-coefficient analogues, then tune empirically against the 3–5 km engagement band in D2.
- **OQ2.** Exact MovementConfig values for the cruiser class after rebase. Physical-plausibility starting point (~1 m/s² accel), tune via B.
- **OQ3.** When AI's largest weapon is out of range and the target is fleeing, does the ship pursue indefinitely or break off? Recommend pursue until a kill criterion (destroyed) or pursuit-cost criterion. Final shape decided in G; C just gets a sane default for cruiser pursuit.
- **OQ4.** Existing-asset rescaling table: which bundled JSONs need value changes, and to what targets? See D1 audit note.
- **OQ5.** Right-mouse-drag pan implementation path (per Working Assumption #2): commit in planning to either raw `PointerEvent` interception or left-drag-pan fallback.
- **OQ6.** Cruiser ship JSON authoring: is there an existing player-built cruiser candidate in the ship library, or does C author one fresh in the ship-builder?

## Success Criteria

- **Operational:** demo scenario at the new scale loads and plays without manual recalibration. Camera pan + zoom works on Desktop and Android. Debug overlay renders distance rings + mouse readout when `DevToolsGate.isAvailable`.
- **Tuning fidelity:** all cruiser-class physics, projectile, and AI constants are interpretable as physical quantities; no remaining "magic numbers" without a documented physical referent in the rebased class.
- **Self-playtest commitment:** before C is considered shippable, the dev plays at minimum 3 cruiser-vs-cruiser sessions at varied tunings and writes 1–2 paragraphs of subjective feedback into a new file `docs/playtest-notes/2026-XX-cruiser-feel.md` (date-stamped at the time of writing). The note captures (a) what felt right, (b) what felt off, (c) which constants needed iterating most. This produces a written artifact that D, E, F, and G brainstorms can reference as the cruiser-feel baseline.
- **Substrate readiness:** a fighter-class follow-up item can author its own physics + projectile values using C's tuning conventions and the debug overlay without re-deriving the physical-plausibility framing.
