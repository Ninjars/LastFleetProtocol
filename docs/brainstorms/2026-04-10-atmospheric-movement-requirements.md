---
date: 2026-04-10
updated: 2026-04-11
topic: atmospheric-movement
---

# Atmospheric Movement Model — Directional Drag and Keel-Based Lift

## Problem Frame

The current movement model is pure frictionless Newtonian physics (`features/game/impl/src/commonMain/kotlin/jez/lastfleetprotocol/prototype/components/game/physics/ShipPhysics.kt`): forces accumulate per frame, there is no passive drag, terminal velocity is unbounded, and `ShipNavigator.computeBrakingStrategy` bakes a `v² / 2a` stopping-distance formula into AI braking decisions across several code paths. `docs/ship.md` explicitly codifies unbounded-under-thrust as an intentional design choice, so this brainstorm is an explicit reversal of that.

Three related problems motivate the change:

1. **Ships feel the same regardless of shape.** The builder lets players draw custom hull polygons, but those polygons only affect collision — they have no influence on how a ship moves. Every design converges on similar combat behaviour because movement is driven entirely by scalar thrust values in `MovementConfig`. This is the only problem currently observable in the code; the other two are currently untested theory.
2. **The game's fiction is ambiguous.** `docs/game_design.md` does not commit to a setting. "Last Fleet Protocol" evokes space, but the current physics don't feel like space or anything else in particular — they're a placeholder.
3. **Combat lacks weight.** Ships can pivot-and-dash freely, which produces a top-down-shooter feel. The target is closer to naval combat (committed trajectories, momentum costs) while retaining more freedom of movement than water-bound vessels.

This brainstorm reframes movement as an atmospheric model with fictional compact levitation tech, directional drag derived from hull shape, and a new Keel hull-part type that introduces lift as a build-time constraint and a second death condition beyond HP destruction.

The work is explicitly sliced so that the directional drag reframe (Slice A) can ship and be validated independently of the Keel + lift mechanic (Slice B). This lets the drag-driven "shape matters" mechanic land and be playtested before committing to the larger Keel system.

## Slice Structure

| Slice | Delivers | Requirements |
|---|---|---|
| **Slice A** | Directional drag, fiction reframe, naval-feel movement, stats-panel rework | R1–R7, R22, R23, R27–R29 |
| **Slice B** | Keels, build-time lift gate, lift-failure death condition, flightworthiness UX | R8–R11, R14, R17, R19, R20, R21, R24 |

Slice A is playable and complete on its own. Slice B builds on it. The slicing reflects that the stated goals (shape matters, fiction, naval feel) are all delivered by Slice A alone; Slice B adds a fourth goal — make Keel choice a meaningful build axis — which is valuable but should not gate the drag reframe.

## Requirements

### Slice A — Directional Drag and Physics Reframe

**Physics Model**

- R1. Replace frictionless Newtonian physics with an atmospheric drag model. Full replacement — no coexistence flag, no "space mode". No back-out gate: if feel regresses, recovery is via git revert. The user accepts the irreversibility cost.
- R2. Drag is quadratic in velocity (`F_drag = ½·ρ·v²·Cd·A`). Linear drag is rejected as insufficiently naval in feel. Braking math under quadratic drag is log-based, not constant-deceleration — this invalidates `ShipNavigator.computeBrakingStrategy`'s `v² / 2a` assumption in multiple places and must be reworked as part of this slice (see Dependencies).
- R3. Per-axis drag coefficients (forward, lateral, reverse) are computed **once at ship-load time** from the combined hull geometry. See R29 for the aggregation rule. The Key Decision "linear per axis" refers to the decomposition structure, not the analytic form of braking math.
- R4. At runtime, drag force is applied via smooth angular interpolation between the pre-computed axis coefficients based on the angle between the ship's velocity vector and its local forward axis. This avoids the step-function discontinuity that would result from naïvely reclassifying velocity as "forward" or "lateral" the instant a ship rotates. The visible consequence is that a rotating ship's drag coefficient changes continuously as heading sweeps past velocity, producing smooth skid under turn.
- R5. Drag produces a **per-axis terminal velocity** under constant thrust. A ship's top speed in each local direction is a computable closed-form value (`v_t = √(2·thrust / (ρ·Cd·A))` under quadratic drag). Terminal velocity is an upper bound achievable only under isolated-axis thrust; in curved trajectories the effective cap is lower because of cross-axis drag coupling.
- R6. Angular rotation is damped so ships do not spin freely. For Slice A, the current `ShipPhysics` convention of treating `mass` as a stand-in for moment of inertia is **preserved**. Angular dynamics are dimensionally incorrect but gameplay-adequate, and introducing a polygon-derived moment of inertia is out of scope. Documented here so planning does not silently invent one.
- R7. The physics interface accommodates a future wind force applied uniformly. Wind is not implemented in this slice.

**Thrust Reinterpretation**

- R22. Existing `MovementConfig` thrust values (`forwardThrust`, `lateralThrust`, `reverseThrust`, `angularThrust`) are preserved structurally but semantically reinterpreted — they now fight drag rather than producing unbounded acceleration. All existing values require retuning. Retuning is content work (per-ship, per-Keel-class) and is not a code task.
- R23. The ship builder's stats panel replaces thrust-based "acceleration" readouts with per-axis terminal velocity displays (forward, lateral, reverse) and exposes a single headline "movement" indicator. The specific stat set is planning-scope; at minimum forward terminal velocity is shown. A polar drag plot visualising the per-axis coefficients against hull shape is recommended as a diagnostic widget — it directly surfaces the R3/R29 computation and helps the builder user reason about shape-driven differentiation. Terminal velocity is labelled as an upper bound, not a guaranteed achievable speed.

**Drag Derivation from Hull Geometry**

- R27. In Slice A, all ships may use multi-hull designs (the ship builder's existing allowance is preserved). Drag derivation operates on the **union** of placed hull pieces.
- R28. Each hull piece may carry **per-axis drag modifiers** (forward, lateral, reverse) authored in the ship builder. These modifiers reflect aerodynamic intent — a streamlined nose piece has a low forward modifier; a broad engine mount has a high lateral modifier. Modifiers are stored on the hull piece's `ItemDefinition`.
- R29. The ship's aggregate per-axis drag coefficient is computed at load time as follows:
  1. Compute the ship's axis-aligned bounding box over the union of all placed hull pieces (after position, rotation, and mirror transforms).
  2. For each axis (forward, lateral, reverse), compute the hull-piece drag modifiers weighted by the **proportion of each piece's perimeter visible to the exterior of the ship along that axis** — i.e., pieces that are buried inside other pieces contribute less, pieces with exposed faces contribute more.
  3. The combined axis coefficient is the bounding-box extent along that axis times the visibility-weighted modifier average.

  This resolves the "bounding box is shape-blind" critique: within-box detail matters because visible-to-exterior geometry determines which modifiers dominate. The perimeter-visibility computation is a Slice A deepening target — the initial implementation may approximate with a simpler heuristic (e.g., modifier average weighted by piece area), with perimeter-projection replacing it in a follow-up.

**Migration (Slice A)**

- R25. Existing `DemoScenarioConfig` ships are replaced wholesale by builder-authored equivalents as part of rollout. No mechanical retuning of hand-coded ships. Dev-mode must allow authoring arbitrary test ships regardless of any future progression system.
- R26. The atmospheric model ships alongside or immediately after ship-builder Phase 3 (game integration). Phase 3 is an unplanned prerequisite: it must be scoped and at least partially planned before Slice A planning begins, since Slice A cannot be validated end-to-end without a working builder→simulation pipeline. This dependency is explicit, not assumed.

### Slice B — Keels and Lift-Kill Death Condition

Slice B ships after Slice A is playable. It adds the Keel as a new hull-part type, lift as a build-time constraint, and a second death condition. It is explicitly simpler than the original brainstorm: Lift Modules have been cut, the soft effective-mass dial (R15) has been cut, and the salvageable-wreck marker (R18) has been cut. See "Cut From Scope" for rationale.

**Keel as a Hull-Part Type**

- R8. A new hull-part item type, **Keel**, is introduced. A Keel is functionally a hull piece with additional properties — it participates in hull geometry and drag derivation like any other placed hull piece — but it lives in its own parts-panel category and carries a lift value.
- R9. Every ship has **exactly one** Keel. Exactly-one is enforced by construction: when starting a new design, the builder presents a **mandatory Keel-picker first step**. The chosen Keel becomes the first placed hull piece, centred on the grid, before the canvas is interactable. This eliminates the "what happens if I add a second Keel" conflict flow — there is no way to add a second Keel because the first step commits to one and the parts-panel Keel category is consequently disabled for that design.
- R10. The Keel defines the ship's class (fighter, frigate, cruiser, etc.) and provides the ship's **total lift value** in Slice B. There are no Lift Modules in Slice B.
- R11. Keels are distinct `ItemDefinition` instances in a new Keel category. The data structure is trivially compatible with a future Keel progression/unlock system (they're already distinct, gatable units) — nothing further is committed here. No `tier` field, no `prerequisites` field, no speculative progression scaffolding.

**Build-Time Lift Gate**

- R14. Total ship mass must be less than or equal to total Keel lift. Lift consumption is identical to mass — there is one budget, not two. The builder flags ships failing this check as **unflightworthy** and prevents them from being loaded into combat.
- R20. The ship builder surfaces **lift capacity vs mass** as a live stat in the stats panel, updating as parts are added or removed.
- R21. The ship builder displays a **Flightworthiness Indicator** in the **header of the stats panel**, prominently visible throughout design. States: `Flightworthy` (green), `No Keel` (red, pre-first-step only), `Mass exceeds lift` (red, with the current mass/lift values).
- R24. Flightworthiness is enforced at **two gates**: (a) the ship builder prevents saving a design as "ready for combat" when unflightworthy, though designs may be saved as works-in-progress; (b) the combat-load path re-checks flightworthiness at spawn time and refuses to load an unflightworthy ship. Both gates exist because the builder can be bypassed by a future save/share/import path, and the combat-side check is the authoritative safety net.

**Combat Consequences**

- R17. The Keel is **damageable as a hull part** but its HP is tracked via a new `KEEL` entry in the `InternalSystemType` enum (currently `REACTOR`, `MAIN_ENGINE`, `BRIDGE`). This is a minimal extension of the existing damage model — one enum value and corresponding routing — not a per-instance refactor. Damage routed to the Keel subsystem is applied when damage intersects the Keel's hull polygon.
- R19. When the Keel's HP reaches zero, the ship enters a **LIFT_FAILED lifecycle state**: controls disengage, the ship continues its current momentum under drag, and it is non-participating in combat from the moment of failure. After a short window (tuned during implementation), the ship despawns. For combat outcome purposes, a LIFT_FAILED ship is counted as destroyed at the moment of Keel failure, not at despawn. A combat log event distinguishes `destroyed (lift failure)` from `destroyed (hull)` for R19 legibility.

## Success Criteria

- **Slice A:** Two ships with identical modules but different hull polygon shapes have measurably different movement characteristics — notably different terminal velocities per axis and different turn behaviour. Hull shape is no longer cosmetic.
- **Slice A:** A ship drawn with aerodynamic intent (streamlined nose, clean sides) reaches a higher forward terminal velocity than a visually bulky ship with the same bounding box. Per-hull-piece drag modifiers + perimeter visibility produce the differentiation.
- **Slice A:** Combat engagements exhibit naval-feeling dynamics: committed passes, momentum costs on turning, and visible smooth skid when a moving ship rotates (not a step discontinuity).
- **Slice A:** Per-axis terminal velocity is a predictable upper-bound value displayed in the builder — users can reason about ship performance without running the sim.
- **Slice A validation:** The developer can sit down and fight a match between two builder-authored ships and notice a feel difference from the previous frictionless version within 60 seconds of gameplay. This is the end-to-end sanity check the earlier version lacked.
- **Slice B:** A ship built around a "bomber" Keel feels meaningfully different to pilot and fight than one built around a "fighter" Keel, even before combat AI differentiation.
- **Slice B:** Lift-failure destruction is legibly distinct from HP destruction in the combat log, and Keel-destroyed ships visibly drift before despawning.

## Scope Boundaries

- **Wind is deferred.** The physics interface accommodates wind as a future force input, but no wind mechanics (static, dynamic, zones) are implemented in this pass.
- **Power and heat are deferred.** No Lift Modules in Slice B, so there are no power-consuming or heat-generating subsystems to design here. A later brainstorm may reintroduce Lift Modules with power/heat costs as part of designing those subsystems.
- **Lift Modules are deferred.** All lift comes from the Keel in Slice B. Dedicated lift-providing modules become a future feature once power/heat exist.
- **Effective mass / soft lift dial is deferred.** R14 is a hard gate only — mass must be ≤ lift to fly. "Excess lift reduces effective mass" is cut. A future brainstorm may reintroduce it if playtesting shows the lift budget is otherwise too binary.
- **Salvageable-wreck tracking is deferred.** Lift-killed ships are just destroyed (with a distinct cause tag). No persistent salvage-state flag. The post-combat salvage loop is a separate future brainstorm that will decide what state it needs.
- **Full AI rewrite is a separate downstream brainstorm.** `BasicAI` and `ShipNavigator.computeBrakingStrategy` need interim patches as part of Slice A so combat runs without crashing or thrashing under quadratic drag. High-quality behaviour (skid tactics, momentum-aware intercepts, per-class archetypes) is out of scope here. See R30.
- **Keel unlock / progression system is deferred.** R11 commits only to Keels being distinct `ItemDefinition` units. Any progression scaffolding is future work.
- **Post-combat salvage/recovery loop is deferred.**
- **Keel-or-class-specific drag modifiers are out of scope.** Drag is derived from the union of hull pieces and per-piece modifiers (R29). The Keel affects lift, class identity, and progression — not drag (other than via its own contribution as a hull piece).
- **No "space mode".** Frictionless physics is deleted, not preserved behind a flag.
- **Perimeter-projection refinement of R29 is a deepening target, not a blocker.** An initial implementation may use a simpler modifier-aggregation heuristic; perimeter-based visibility replaces it in a follow-up if needed.
- **Bounded moment of inertia is out of scope.** R6 uses mass as a stand-in. A polygon-derived MoI is a future refinement.

## Cut From Scope (Changes from First-Pass Review)

These items were in the first-pass brainstorm and were cut during document review:

- **Lift Modules as a distinct category.** Removed: all lift comes from the Keel. Resolves ~5 findings including the per-instance damage-model refactor prerequisite and the "target lift modules first" dominant strategy concern.
- **R15: Excess lift reduces effective mass.** Cut: created a lift-for-speed loophole under quadratic drag, had an undefined formula, and directly undermined the "hull shape matters" goal by making lift margin the dominant movement dial.
- **R18: Salvageable-after-combat marker.** Cut: orphan requirement with no in-scope consumer. Combat log distinguishes death cause via R19 instead.

## Key Decisions

- **Directional drag coefficients computed at load time, not per-frame polygon projection.** Keeps per-frame physics cheap. Runtime applies them via smooth angular interpolation (R4) so rotation doesn't produce a step discontinuity.
- **Drag is quadratic**, not linear. Chosen for naval feel at the cost of log-based braking math in AI.
- **Exactly one Keel per ship, enforced by construction via a mandatory first-step picker.** No conflict flow needed because no second-Keel path exists.
- **Full replacement of the frictionless model with no back-out gate.** Recovery from feel regression is via git revert. Accepted cost.
- **Hull shape matters via per-piece drag modifiers weighted by exterior visibility** (R28/R29), not via per-frame projection. Rewards thoughtful polygon composition; within-box shape details matter because exposed geometry dominates the modifier aggregation.
- **Lift comes entirely from the Keel in Slice B.** Simpler damage model, cleaner death condition, no dominant-strategy target list.
- **Lift failure is a real, distinct death condition** via a new KEEL entry in `InternalSystemType` and a LIFT_FAILED lifecycle state that drifts then despawns.
- **Slicing A/B is firm.** Slice A is playable and complete without Slice B. Slice B landing depends on Slice A having shipped and been validated.
- **Angular dynamics use mass as a moment-of-inertia stand-in**, matching current `ShipPhysics` behaviour. Polygon-derived MoI is deferred.
- **Builder stats panel gets a headline Flightworthiness Indicator in its header.** Always visible; never a surprise at combat load time.

## Dependencies / Assumptions

- **Ship-builder Phase 3 (game integration) is an unplanned prerequisite for Slice A.** Slice A cannot be validated end-to-end without a working builder→simulation pipeline. Phase 3 must be scoped and at least partially planned before Slice A planning begins. Recommended sequencing: plan Phase 3 → plan Slice A → plan Slice B → plan deferred items.
- **`ShipNavigator.computeBrakingStrategy` needs replacement as part of Slice A, not as a deferred item.** The `v² / 2a` assumption appears in at least three places (desired-speed-at-distance, stop-distance, rotate-then-brake scoring) and the rotation-time estimate also assumes constant deceleration. An interim closed-form drag-based approximation — "good enough to not thrash" — must be derived and acceptance-tested before Slice A ships. This is promoted to R30:
- R30. **AI Braking Patch (interim).** `ShipNavigator.computeBrakingStrategy` and its supporting formulas are replaced with a quadratic-drag-based closed-form approximation. Acceptance criterion: AI ships approaching a target do not oscillate, successfully come to approximate stop within 2× the analytically computed braking distance, and do not thrash between rotation and thrust decisions. No other AI behaviour changes. Full drag-aware AI rewrite is deferred.
- **`ShipStatsCalculator.kt` in the builder needs significant rework** to compute terminal velocities from drag coefficients, lift totals, flightworthiness flags, and to display the per-axis terminal velocities and lift/mass relationships. Likely the single largest file change in the builder for this work.
- **A perimeter-visibility polygon utility** (for R29) does not exist today. `PolygonArea.calculatePolygonArea` (shoelace) is not sufficient. Planning must decide whether to build the full visibility computation up front or start with a simpler area-weighted approximation.
- **`InternalSystemType` enum is extended with a `KEEL` value** as part of Slice B. This is a minimal change — add the enum case, route arc damage and kinetic damage to it when hitting the Keel polygon, and wire the destruction hook to the new LIFT_FAILED lifecycle state. It is not a per-instance damage-model refactor; the existing enum-keyed structure is preserved.
- **A new lifecycle state (`LIFT_FAILED`)** is added to `Ship.kt` alongside the existing `isDestroyed` binary, with a short "drift + despawn" window driven by the existing physics loop plus a countdown. This is net-new infrastructure but is bounded: a state enum + a timer + a controls-disable flag, not a generalised ship-lifecycle system.
- **`ShipDesign.formatVersion` must bump** (currently 2 → 3) for the Keel item-type and per-piece drag modifiers. No migration — existing saved designs are invalidated. Consistent with R25's "replace wholesale" posture.
- **Future power/heat extensibility:** Any `ItemDefinition` changes to support the Keel should avoid baking in assumptions that would prevent a future `powerDraw` / `heatGeneration` field on any item. This is a soft forward-compatibility note, not a requirement.
- **Kubriko hot-path budget:** The drag integration adds per-ship-per-frame vector math. Current combat is small-scale so this is unlikely to matter, but planning should avoid unnecessary `SceneOffset` allocations in the drag force path (see `docs/ai_kubriko_constraints.md`).

## Outstanding Questions

### Deferred to Planning

- [Affects R2, R30][Technical] Derive the closed-form stopping distance under quadratic drag (`x(v)` where `dv/dt = (thrust - k·v²) / m`) and use it in the interim `computeBrakingStrategy` replacement. This is a solvable math problem but the derivation and the acceptance criterion both need writing.
- [Affects R4][Technical] Angular interpolation function for directional drag application — simplest is `|cos(θ)|` for forward/reverse axis blending and `|sin(θ)|` for lateral, but these may need shaping to get the skid feel right. Tune during implementation.
- [Affects R6][Technical] Angular drag model: linear `k·ω` damping or quadratic `k·ω²`. Feel-driven; pick during implementation.
- [Affects R17][User decision] Tuning of the "drift window" before a LIFT_FAILED ship despawns. Start with ~3 seconds and adjust once visible in combat.
- [Affects R28, R29][Technical] Exact formula for hull-piece drag modifiers. Planning should define (a) the modifier's numeric range (e.g., 0.5–2.0), (b) the per-piece authoring UX in the ship builder (sliders per axis, presets, derived-from-shape defaults?), and (c) the visibility-weighting approximation used for the initial implementation of R29. Perimeter projection is the deepening target; the initial shipping version may use a cruder weighting.
- [Affects R9][User decision] Is the mandatory-Keel-first-step a full-screen modal, an inline panel that replaces the parts panel, or a canvas prompt? UX decision; pick during implementation.
- [Affects R23][Needs research] What stats are actually useful in the stats panel besides per-axis terminal velocity and flightworthiness? Candidates: time-to-terminal, braking distance, turn radius at terminal speed, total mass. Add a polar drag plot as the headline visualisation widget.
- [Affects R27][Technical] Multi-hull Keel placement: the Keel is one hull piece in a multi-hull ship. Does the builder enforce the Keel being placed "inside" other hull pieces, "at the centre", or anywhere the user chooses? Probably anywhere, but call it out.
- [Affects R20, R21][Needs research] What does the physical "Mass" display look like alongside the new "Lift" and "Flightworthy?" indicators? Inventory of current stats-panel contents and a before/after map should be a planning deliverable.
- [Affects R1, R22][User decision / Needs tuning] MovementConfig retuning pass: which ships / Keel classes need to exist for Slice A demo scenarios, and what are their target thrust/drag tuning targets? Content work that planning should surface.

### Follow-up Brainstorms (Not Blocking This Work)

- **Atmospheric AI** — new behaviours exploiting drag: skid tactics, momentum-aware intercepts, per-Keel-class archetypes. Runs after Slice A is playable and the interim braking patch has revealed which AI issues remain.
- **Power and heat** — ship-wide power budget, heat generation/dissipation, module power draw. Reintroduces Lift Modules with costs.
- **Wind** — static global, dynamic, and/or zone-based wind as environmental mechanics.
- **Keel progression and unlock** — how players acquire new Keels, progression curves, mission-tier relationships.
- **Post-combat salvage loop** — recovering lift-failed and hull-destroyed ships, salvage yields, integration with player inventory/economy. Needs to decide its own state tracking; nothing in Slice B forward-declares it.
- **Effective mass / soft lift dial** — only if playtest reveals the hard-gate approach to R14 is too binary.
- **Polygon-derived moment of inertia** — refinement to replace the current mass-as-MoI stand-in.
- **Perimeter-projection visibility weighting for R29** — replaces the initial-implementation approximation once the simpler version has been validated.

## Next Steps

→ **Before planning this work:** scope ship-builder Phase 3 (game integration). It is an unplanned prerequisite for Slice A validation.

→ Then `/ce:plan` against Slice A first, Slice B second.
