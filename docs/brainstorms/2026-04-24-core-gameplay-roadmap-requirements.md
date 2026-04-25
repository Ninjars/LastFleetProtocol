---
date: 2026-04-24
updated: 2026-04-25
topic: core-gameplay-roadmap
---

# Core Gameplay Roadmap — Toward a Game-Feel Focus

## Problem Frame

The prototype has shipped the combat vertical slice, ship builder (Phases 0–3), and atmospheric movement (Slice A + B). What's in the repo today is playable — two teams spawn, shoot, drift, and resolve — but it's still an early slugging match: uniform battlefields, shallow projectile interaction, ad-hoc ship authoring, and no dev harness for iterating on scenarios.

Before the project can responsibly shift focus to game-feel polish (particles, audio, camera work, animation curves, tuning), a set of core functionality needs to land. This roadmap sequences that work, locks the cross-cutting product decisions that affect more than one item, and establishes each item as ready for its own `/ce:brainstorm` when it becomes next-up.

This is a **roadmap document**, not a single-feature brainstorm. Per-item depth is intentionally deferred to each item's own brainstorm.

**Context:** solo-dev prototype. "Compound-engineering leverage" arguments are weighed against the cheaper alternative of hand-rolled content + tight cycles. Where tool-building is chosen, the reason is stated explicitly.

## Goals

1. **Unblock a game-feel phase.** Once this roadmap is complete, the codebase should have no known structural rewrite blocking a sustained period of polish work.
2. **Validate the core combat fantasy early.** The sequence deliberately surfaces "is fighting at kilometre-scale actually fun?" before the largest structural pieces land.
3. **Resolve the module-authoring direction now** so downstream items (E combat depth, A asset library) can be planned without re-litigating product shape.
4. **Keep each item independently plannable.** Every item below should be runnable as its own `/ce:brainstorm` → `/ce:plan` → `/ce:work` cycle without needing this roadmap to be re-read.

## Non-Goals (for this roadmap)

This roadmap does not attempt to scope or sequence the following, even though they appear in `docs/ship.md` / `docs/weapons.md`:

- Heat management system — deferred; may be revisited after E.
- Ammunition / magazine mechanics — deferred; may land with E if scope allows, otherwise separate.
- Thermal and Beam weapon types — deferred; E starts with Kinetic and Concussive.
- Damage Control system behaviour — deferred; E's brainstorm decides whether to include it or stub it.
- Captains, crew XP, morale, special abilities — deferred indefinitely.
- Formation-level commands and fleet orders (`docs/game_design.md`) — deferred; G handles per-ship control only.
- Boarding, ramming, and special-ability weapons — deferred.
- Networked/multiplayer scenarios — deferred indefinitely.
- **Player-facing assembly screen** (edge-snap, mirror, parts-list browsing, slot-to-module assignment) — **moved out of the roadmap**, re-evaluated after G in light of playtest findings. The committed slot model still lands (via item D), so the data shape is ready if this is greenlit later.
- Audio, music, persistent player profile — deferred to the game-feel phase.

If any deferred item becomes blocking for one of the items below, that item's own brainstorm surfaces it. A running list of "deferred-but-tracked" items belongs in this doc under Re-evaluation Triggers.

## Sequencing

Locked order (7 items):

| # | Roadmap Item | User-described Topic | Role |
|---|--------------|----------------------|------|
| 1 | **A** | #6 Export hull parts + ships to project assets | Dev pipeline prerequisite |
| 2 | **B** | #7 Scenario builder (dev tool) | Force multiplier for subsequent testing |
| 3 | **C** | #4 Battle-feel pass — scale/zoom/projectile/AI retune | Validates the kilometre-scale fantasy early |
| 4 | **D** | #2 Slot system + dev ship-builder refactor + migration | Prereq for E; the player-facing assembly screen is descoped from the roadmap |
| 5 | **E** | #3 Combat depth (ricochet, penetration, line trace) + hit-event VFX | Depends on D's slot geometry; owns the glancing-vs-penetrating VFX |
| 6 | **F** | #1 Terrain + structures | New actor category; structures reuse D's slot model |
| 7 | **G** | #5 Player AI + status UI markers | Final piece; needs E's per-system state to render markers |

**Rationale for ordering:**
- **A before B** — B has no content to compose without A.
- **B before C** — C is validated via B-composed scenarios for fast A/B comparison. If the JSON-hand-edit alternative is chosen for A and B (see Considered Alternatives), C can run on that scaffolding.
- **C before D** — C is pure tuning on the existing combat model (scale, zoom, speeds, AI retune). Doing it early gives the first real "is this fun?" playtest signal before committing to D+E. VFX moves to E, where the hit-event taxonomy is defined.
- **D before E** — E's line trace needs deterministic slot geometry.
- **F after E** — terrain/projectile interaction inherits the full interaction model.
- **G last** — G needs per-system damage state from E and benefits from C's scale + F's tactical terrain.

## Cross-Cutting Decisions (Resolved Here)

These decisions apply across multiple items and are fixed by this roadmap. Each item's own brainstorm inherits them.

### CCD-1. Module authoring is slot-based; polygon tool becomes dev-only.

- Hulls are authored by devs using the polygon tool with the current property-customisation options.
- Each hull part declares slot positions and slot categories.
- Module bounds are standard per slot (the slot defines the bounds, not the module).
- Reactor slot is tied to the Keel hull part (implicitly limiting reactor-class by hull-class).
- The current ship-builder screen will be refactored into a dev-tool authoring screen (D).
- A player-facing assembly screen is **descoped from this roadmap** (see Non-Goals); re-evaluated after G.

**Considered alternative.** Polygon-region line-tracing would preserve the "draw your own ships" USP shipped in Phases 0–3 and arguably match the expressiveness the ship-builder already rewards. It was not chosen because (a) slot-based authoring gives D a cleaner data model for E's line trace and F's structures, (b) player-facing polygon authoring is less important in the prototype phase than gameplay depth, and (c) slot geometry composes better with the edge-snap + mirror tools a future player assembly screen would want. **If D runs into trouble** — e.g. migration of existing `default_ships/*.json` proves painful, or slot sizes feel too uniform — revisiting CCD-1 in favour of polygon-region tracing is a valid re-evaluation trigger.

### CCD-2. Export pipeline is Desktop-dev-only; target path is the repo working copy.

The composeResources/ path (`components/game-core/api/src/commonMain/composeResources/files/`) is a **build-time source** — it is baked into APK assets / the JAR classpath and is not writable at runtime. Android apps have no access to the repo filesystem. Desktop apps may be running from a JAR rather than the source tree.

Concrete mechanism:
- **Android:** no export. If needed, Android's role is read-only consumer.
- **Desktop (dev-only):** three mechanism options for A's brainstorm to choose among:
  1. **Env-var-path** — export writes directly to a repo working-copy path, discovered via `LFP_REPO_ROOT` (plausibly injected by `./gradlew run` as `-DLFP_REPO_ROOT=$rootDir`).
  2. **Clipboard** — export puts JSON on the clipboard for manual commit. Works from any distributable.
  3. **Gradle task** — a dedicated dev task (e.g. `./gradlew :composeApp:exportShipDesign --from=...`) that copies from `getUserDataDir()` into the composeResources path. Naturally Desktop-dev-only because Android can't run Gradle, and sidesteps in-app path-discovery entirely.
- **Existing write path** (`FileStorage.{jvm,android}.kt`, writing to `appDirs.getUserDataDir()`) stays as the app-data save target. Export is a separate promotion step from that save location to the repo.
- A's brainstorm picks one mechanism (or a primary + fallback pair).

This is an architectural constraint, not an open question.

### CCD-3. Scenario builder is dev-facing; composition core stays reusable.

- Invoked from a dev/debug menu entry, not the main landing screen.
- Composition: pick ships per team from the asset library (A), position them, optionally place terrain (after F lands).
- Launches into a combat scene via a dev-scenario launch path parallel to the production `startDemoScene` — production demo stays, dev scenarios get their own entry point.
- Persistence: may save local scenario configs to app data for dev convenience. Committing scenarios to the repo is out of scope unless it becomes useful later.
- **Build it so the composition core (scenario model, launch plumbing) is separable from the dev-UI layer.** If playtesting later reveals that scenarios are the primary gameplay shape, a player-facing mission picker can reuse the core without re-implementing it.

### CCD-4. `docs/ship.md` and `docs/weapons.md` are the starting point for E, not a frozen contract.

- These docs describe the target projectile-interaction model (ricochet / penetration / line trace / overpenetration / per-segment armour).
- E's brainstorm inherits the model but may override any piece where the spec turns out to be aspirational, contradictory, or costly without a matching gameplay benefit.
- **Rule:** when E's brainstorm diverges from the docs, the brainstorm is authoritative and the docs are updated to match in the same change. The docs never become out of date.
- Known gaps the reviewer surfaced (per-segment armour vs. uniform in v1, RNG model, Damage Control behaviour, Magazine collateral, Beam/Thermal interactions) are E-brainstorm decisions, not roadmap-level locks.

### CCD-5. Scale baseline is decided in item C's brainstorm — early in that discussion.

- The "tens to hundreds of metres long, exchange fire over kilometres" framing implies a concrete SceneUnit → metres mapping.
- This mapping propagates into slot sizes (D), terrain scale (F), line-trace distances (E), and AI engagement ranges (C and G).
- Resolve it in the first third of C's brainstorm so downstream items inherit a stable number.

### CCD-6. Dev tooling and screens follow existing orientation policy.

- `docs/game_design.md` requires portrait on mobile, landscape on desktop, with UI that handles portrait.
- **New screens introduced by this roadmap** (refactored dev ship-builder, scenario builder) **are Desktop-only dev tools** and are not required to support portrait. The main game screen, landing screen, and any future player-facing builder remain subject to the existing policy.
- **Enforcement mechanism** — runtime gate (debug menu / build-flavour check) rather than compile-time Android exclusion. Reason: kotlin-inject wires DI at compile time, so dev-tool feature modules will be on the Android classpath regardless. D (for the dev ship-builder gate) and B (for the scenario builder gate) each own the concrete gating mechanism for their screen.
- This is an explicit carve-out, not a departure — it keeps dev UI lean without weakening the player-facing policy.

### CCD-7. Status UI markers (G) are world-space for ship-local indicators, screen-space for the selected-ship panel.

- **World-space** (Kubriko actor children on each Ship): hull HP bar, per-system damaged/disabled/destroyed icons — they attach to the ship, inherit its transform, and scale with the camera's zoom.
- **Screen-space** (Compose overlay): the "selected ship" detail panel that surfaces full status and command actions.
- This decision affects E: per-system state is already reachable via `Ship.shipSystems.getSystem(...)`, but not observable. E adds an observable surface (Flow / per-frame snapshot) so world-space markers can bind without per-frame polling. E also adds an aggregate hull-HP field on Ship if G's marker set needs it — no such field exists today.
- **Marker visual language** (text vs. icons, color semantics) is a G-brainstorm decision, not locked here.

## Navigation and Architecture

After all 7 items land, the app's top-level destinations are:

- **Landing** (unchanged) — entry point for players.
- **Ship Builder** (refactored to dev-authoring in D) — behind a dev gate (exact gating — build flag vs. debug menu — is a D decision).
- **Game** — production demo via `startDemoScene` (unchanged path).
- **Dev Menu** (new) — parent for dev-only tools. Contains:
  - **Scenario Builder** (B) — compose + launch custom battles.

**Return destinations:**
- Scenario Builder launches into Game; after Game resolves or player exits, returns to **Scenario Builder with the last config preserved** so the dev can tweak and re-run. (Does not return to Landing.)
- Production demo Game continues to return to Landing on exit.

**Feature module layout:**
- Existing `features/ship-builder/{api,impl}` holds the refactored dev-authoring screen (D). If the descoped player-facing assembly screen is later greenlit, it will be evaluated as a separate feature module at that time; the roadmap does not commit module shape for unshipped work.
- Scenario builder lives in its own feature module (`features/scenario-builder/{api,impl}`). Kotlin-inject wires the module into `AppComponent` at compile time regardless of build target, so isolation is a nav-graph / dev-gate concern (per CCD-6), not a classpath-exclusion one.
- G's status UI (markers + selected-ship panel) lives inside `features/game/impl` since it binds to live Ship actors and GameVM state.

## Considered Alternatives (Roadmap-Level)

Alternatives explicitly weighed, not implemented. Each could be reopened if the chosen direction runs into trouble.

### Polygon-region line-tracing instead of slot-based authoring
See CCD-1. Preserves Phases 0–3's polygon-authoring USP. Rejected for D-reasons listed in CCD-1; revisit if slot-based migration proves painful.

### JSON hand-edit instead of A + B as in-app tooling
The cheapest alternative to the scenario builder is: the dev edits a JSON file (containing ship picks, positions, teams) and the app reads it on launch. This delivers ~80% of B's leverage for C at roughly one day of work. Rejected because:
- A is needed anyway (to commit authored ships into the repo).
- B's UI cost is bounded (CCD-3 specifies dev-facing only — YAGNI applies above the UX-floor set by the "compose-a-battle-under-a-minute" success criterion).
- Once B exists, it keeps paying off for D, E, F, G.
If A or B's implementation cost inflates beyond expectations, the JSON fallback is a valid checkpoint pivot.

### Keep dev and player ship-builders on one screen with a mode toggle
Rejected for D's scope: dev authoring needs the polygon tool and property-customisation; a hypothetical player-facing assembly would need the parts-list + edge-snap + mirror. The UI modes are different enough that a single toggled screen doubles the interaction-state space. Since the player-facing screen is descoped from the roadmap anyway, the "one screen, two modes" question doesn't need an answer during D.

## Re-evaluation Triggers (Checkpoints)

The roadmap is not a linear fire-and-forget commitment. Pause and re-evaluate if:

1. **After C ships** — playtest the scale + VFX + retuned AI for 1–2 days. If the kilometre-scale combat loop doesn't feel fun **before** D/E land, the problem is not combat depth and the roadmap direction needs rethinking, not more items.
2. **During D** — if migration of existing polygon-authored `default_ships/*.json` into the slot model proves costly, or slot-sized internals feel too uniform, reopen CCD-1 and consider polygon-region line-tracing.
3. **During E** — if the full ricochet + penetration + line-trace + overpenetration model slips past budget, scope down and update `docs/ship.md` to match the shipped model. The specific v1 fallback shape (uniform vs. per-segment armour, single vs. per-system penetration checks, etc.) is an E-brainstorm decision.
4. **After G** — re-evaluate whether the descoped player-facing assembly screen is worth building now, or continues to sit post-roadmap.

The checkpoints are not commitments to stop — they're commitments to ask "is the premise still right?" before doing the next large piece.

## Per-Item Summaries

Each item's scope, key open decisions, dependencies, and rough complexity. These are seeds for each item's own `/ce:brainstorm`, not full requirements.

---

### Item A — Export hull parts and ships to project assets  (roadmap #1 / user #6)

**Goal.** Take parts/ships saved in app data and promote them to committed project assets, so we can build up a curated library across dev sessions and machines.

**Scope seeds.**
- Export action surfaced from the dev ship-builder (once refactored in D; until then, from the current ship-builder).
- Target directory: `components/game-core/api/src/commonMain/composeResources/files/` (see CCD-2). Desktop-dev-only, gated on repo-root discovery.
- Probably new subdirectories for hull parts and complete ships (default ships already have one).
- Schema already carries a `formatVersion` field (currently 3, see `ShipDesign.kt`). A's scope is to lock the bump-on-breaking-change discipline so D can distinguish v3 (polygon-authored) from any future v4 (slot-authored) content without inventing a new mechanism.

**Key open decisions (resolve in A's brainstorm).**
- Export mechanism: env-var-path, clipboard, or both (per CCD-2).
- Scope of export: hull parts only, or also complete ships and turret guns.
- Naming conflict / overwrite behaviour.
- Migration-ownership: D owns migration of any A-exported content when the slot-based format lands — A's brainstorm just has to pick a `formatVersion` discipline so D can tell old from new.

**Depends on.** Nothing.

**Unblocks.** B.

**Complexity estimate.** Small.

---

### Item B — Scenario builder (dev tool)  (roadmap #2 / user #7)

**Goal.** Screen where devs pick player + enemy ships from the committed library (A), position them, and launch into combat. Force-multiplier for all subsequent testing.

**Scope seeds.**
- Dev-menu entry (see Navigation and Architecture).
- Team composition + click-to-place positioning with optional numeric fine-tune.
- Launch path parallel to `startDemoScene`; return to scenario builder with config preserved (see Navigation and Architecture).
- Composition core separable from UI (per CCD-3), so a future player-facing mission picker could reuse it.
- Post-F, also picks terrain and places it.

**Key open decisions (resolve in B's brainstorm).**
- Persistence model (session-only, app-data saved).
- UI fidelity floor: the "compose a non-default battle in under a minute" success criterion sets the floor; YAGNI applies **above** the floor, not to it.
- Empty-state (no library assets committed yet) and stale-reference state (scenario references a deleted/migrated ship) behaviour.

**Depends on.** A.

**Unblocks.** Faster iteration on C, D, E, F, G.

**Complexity estimate.** Medium.

---

### Item C — Battle-feel pass: scale, zoom, projectile tuning, AI retune  (roadmap #3 / user #4)

**Goal.** Make the battlefield feel like large warships trading fire over kilometres. Scoped to tuning + camera work only. **Hit-event VFX (glancing/penetrating indicators, turret flashes) are moved to E**, where the event taxonomy is defined.

**Scope seeds.**
- Establish SceneUnit-to-metres mapping early (CCD-5).
- Increase projectile speeds and lifespans for the new scale.
- Rework zoom extents (close-in combat ↔ fleet overview).
- Rework ship starting distances in the demo scene and via B.
- Retune enemy AI engagement/braking/firing ranges where they depend on the old scale.

**Key open decisions (resolve in C's brainstorm).**
- Exact SceneUnit → metres ratio.
- Whether existing physics tuning needs rescaling or stays scale-free.
- Camera zoom floor/ceiling.
- How aggressively to retune AI vs. let G do a second pass with player agency in mind.

**Depends on.** B helps; not strictly required.

**Unblocks.** First "is this fun?" playtest signal — see Re-evaluation Triggers #1.

**Complexity estimate.** Small–Medium.

---

### Item D — Slot system + dev ship-builder refactor + migration  (roadmap #4 / user #2, partial)

**Goal.** Shift from "polygons anywhere" to slot-based ship composition per CCD-1. Refactor the current ship-builder into a dev authoring tool. Migrate existing default ships to the new format. **The player-facing assembly screen is descoped from the roadmap** (see Non-Goals) — only the data model and dev tooling land here.

**Scope seeds — dev authoring screen.**
- Keep polygon tool and current property-customisation for hull-part authoring.
- Add: declare slot positions + slot categories on each hull part.
- Declare per-part base stats (mass, lift contribution, etc.) alongside geometry.
- Output flows through A to become library content.

**Scope seeds — slot model + migration.**
- Slot geometry: polygon (for accurate E line-trace) vs. centre+size — D's brainstorm decides.
- Slot categories are **pruned to MVP-needed in D's brainstorm**, not enumerated here. Categories for deferred systems (Heat Sink, Magazine, Damage Control, Ablative) land only when those systems ship, to avoid dead UI and schema churn.
- Migrate existing `default_ships/*.json` + any A-committed polygon-authored content into the slot-based schema. Choice of auto-migrator vs. re-author-by-hand is a D decision.

**Key open decisions (resolve in D's brainstorm).**
- Slot geometry shape (polygon vs. centre+size).
- Which slot categories are strictly MVP for E to work — minimum set is probably Propulsion, Power, Command, Weapon.
- Migration strategy: auto-migrator, hand-reauthor, or staged deprecation.
- Radiator handling: slot-based, external-only, or deferred until Heat ships.
- Dev-gate for the dev authoring screen (build flag, runtime menu).

**Depends on.** CCD-1 locked. Existing ship-builder code (including the Keel work shipped in atmospheric-movement Slice B — already on main).

**Unblocks.** E (needs slot geometry). Any future content authored via A after D ships.

**Complexity estimate.** Medium–Large. Likely sliced internally (schema + migration, then dev-authoring UI refactor), but lands as one roadmap item because all slices gate E.

---

### Item E — Combat depth: ricochet, penetration, line trace + hit-event VFX  (roadmap #5 / user #3)

**Goal.** Replace the current damage-on-hit model with the projectile-interaction model described in `docs/ship.md` + `docs/weapons.md`. Projectiles that clear armour draw a line through the ship and deal damage system-by-system. **Owns the hit-event VFX** (glancing vs. penetrating indicators, turret flashes) because E defines the event taxonomy.

**Scope seeds (from docs/weapons.md Kinetic flow).**
- Per-armour-segment ricochet check (angle-of-incidence + hardness + random roll).
- Per-armour-segment penetration check (AP vs. hardness + random roll).
- Penetrating line trace from impact point through internal systems, ordered by intersection.
- Damage absorption per system (density/hardness vs. AP) with residual carried forward.
- Overpenetration event when residual damage exits the far side.
- Concussive projectile analogue (explosion circle vs. internal systems, no ricochet).
- Vector VFX for each distinct hit outcome (deflection, penetration, overpenetration, explosion).

**Key open decisions (resolve in E's brainstorm).**
- Per-segment armour vs. uniform hull armour in v1. Per CCD-4, divergence from docs is allowed — `docs/ship.md` is updated to match the shipped model.
- RNG model (seeded per projectile for reproducibility, or per-check).
- Defer or include: Damage Control behaviour, Magazine collateral.
- Beam / Thermal interactions — in scope for E or deferred.
- VFX visual vocabulary (vector style, palette).

**Depends on.** D (slot geometry). C (hit VFX inherits the scale and projectile speeds). CCD-7 (per-system state must be observable at the Ship actor level — today `ShipSystems.getSystem(type)` returns reachable but non-observable state; E adds an observable surface plus an aggregate hull-HP field if G needs one).

**Unblocks.** G (per-system state for status markers).

**Complexity estimate.** Large. If scope slips, see Re-evaluation Triggers #3 for the v1 fallback.

---

### Item F — Terrain + structures  (roadmap #6 / user #1)

**Goal.** New actor category: stationary convex-polygon obstacles that provide cover, block navigation, and inflict collision damage. **Structures** are a sub-category: stationary ships with zero thrust that carry turrets and internal systems (per the user's original framing). Because structures are logically ships, they reuse D's slot model for their internal layout and E's damage model for hits — this avoids a parallel data path. Environmental terrain (asteroids, debris, obstacles) has no internal systems and is schema-lighter.

**Scope seeds.**
- Terrain authoring: reuse dev ship-builder's polygon tool (now dev-only per CCD-1) or a terrain-specific variant.
- Collision: ships hitting terrain take collision damage scaled to impact velocity.
- Projectile interaction: terrain blocks projectiles (indestructible in v1).
- Optional body rotation: cosmetic only in v1 (no dynamic collision bounds).
- Structures: stationary ships, inherit D's slot model, mount turrets, take damage per E.

**Key open decisions (resolve in F's brainstorm).**
- Authoring pipeline: reuse dev ship-builder with a "terrain" part type, or separate tool.
- Destructibility: indestructible v1 vs. terrain HP.
- Rotation: cosmetic-only vs. dynamic collision.
- Structures schema: same as ships with velocity pinned to zero, or separate.
- Terrain placement UX in scenario builder (B).

**Depends on.** D (structures reuse slot model). B helps for placement testing. E for shared projectile-interaction model.

**Unblocks.** Game-feel richness (cover + tactical terrain).

**Complexity estimate.** Medium.

---

### Item G — Player AI + status UI markers  (roadmap #7 / user #5)

**Goal.** Player ships run autonomous target-selection + movement (reusing BasicAI), with a player-override affordance. UI markers on ships show damage state.

**Scope seeds.**
- Reuse / extend existing `BasicAI` to run on player ships.
- Per-ship override: tap-to-select, issue move-to / engage-target commands.
- World-space markers per CCD-7: hull HP bar, per-system damaged/disabled/destroyed icons on each ship.
- Screen-space selected-ship panel per CCD-7: full status + command actions.
- AI resumption behaviour when a player-issued command completes.

**Key open decisions (resolve in G's brainstorm).**
- Control granularity: per-ship only, or a lightweight fleet-level affordance.
- Command palette depth: minimal (move, engage) vs. richer (advance, stay-at-range, disengage per `docs/game_design.md`).
- Marker fidelity on low zoom (ships too small for per-system icons — graceful degradation).
- Auto-resume: does AI take over after a player command completes, or does player-command state persist.

**Depends on.** E (per-system state for markers). C (scale + feel). F (tactical terrain makes AI behaviour interesting).

**Complexity estimate.** Medium–Large, UI-dominated.

## Cross-Cutting Decisions Deferred (For Each Item's Brainstorm)

- **SceneUnit → metres ratio.** → C.
- **Export mechanism specifics.** → A.
- **Scenario persistence model + empty/stale states.** → B.
- **Slot geometry shape, MVP category pruning, migration strategy.** → D.
- **Per-segment vs. uniform armour, RNG model, VFX palette.** → E.
- **Terrain rotation, destructibility, authoring pipeline.** → F.
- **Control granularity, command palette, marker degradation.** → G.

## Success Criteria

- All 7 items have shipped as merged feature branches on `main`.
- A committed asset library of at least a handful of hull parts and several complete ships exists under `components/game-core/api/src/commonMain/composeResources/files/`.
- A dev can launch the app, open the scenario builder, compose a non-default battle in under a minute, and playtest it. (Sets B's UI-fidelity floor.)
- A projectile that breaches a ship's armour draws an internal line, damages the systems it passes through, and either terminates or overpenetrates per the (possibly-updated) `docs/ship.md`.
- Player ships fight autonomously by default and accept override commands with visible UI feedback.
- Terrain exists on the battlefield, provides cover, and collides meaningfully.
- Battlefield scale + hit-event VFX have landed; the game looks and feels like what the fiction promises.
- **No known structural rewrite is blocking a sustained game-feel polish phase.** (Known deferred structural work — heat, ammo, thermal/beam, player assembly screen — is tracked explicitly, not confused with "ready.")

## Handoff

Each roadmap item will get its own `/ce:brainstorm` when it becomes next-up. This roadmap is a living document:

- When an item ships, update the sequencing table and mark it complete inline.
- When a cross-cutting decision changes, edit the CCD here so inheriting brainstorms see current truth.
- If an item's brainstorm meaningfully reshapes scope or ordering, revise the relevant Per-Item Summary after that brainstorm lands.
- Re-evaluation Triggers are explicit — when one fires, come back here and re-open the relevant sections before starting the next item.

**Next action (post-this-brainstorm):** run `/ce:brainstorm` on **Item A (Export library)** when ready to start the roadmap.

## Outstanding Questions

None blocking. All remaining open questions are listed in the per-item sections and belong to each item's own brainstorm.
