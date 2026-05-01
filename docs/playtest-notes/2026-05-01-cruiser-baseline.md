# Cruiser baseline — pre-rebase vs post-rebase

Created: 2026-05-01
Plan: `docs/plans/2026-05-01-001-feat-battle-feel-pass-plan.md` (Unit 5)

Stub note created at the rebase commit. **The dev fills in subjective playtest comparison** — pre-rebase + post-rebase paragraphs — as part of the Exit Criteria for item C.

## Pre-rebase (item B Unit 9 demo, before this commit)

| Field | Value |
|---|---|
| Keel mass | 100 |
| Keel lift | 350 |
| REACTOR mass | 30 |
| MAIN_ENGINE mass | 20 |
| MAIN_ENGINE forwardThrust | 500 |
| MAIN_ENGINE lateralThrust | 100 |
| MAIN_ENGINE reverseThrust | 150 |
| MAIN_ENGINE angularThrust | 60 |
| BRIDGE mass | 15 |
| Turret mass (×2) | 5 |
| **Total mass** | **175** |
| **Implied accel** | **2.86 m/s²** |

These values were hand-tuned during item B's combat vertical slice without an explicit physical-plausibility framing. At 1 SU = 1 m, 2.86 m/s² is a *snappy* cruiser — leans more toward destroyer / corvette feel than a slow heavy cruiser.

### Pre-rebase feel (TODO — fill in during dev playtest)

Play 1 cruiser-vs-cruiser session at the pre-rebase values (revert via `git stash` of the post-rebase commit, or check out the parent commit). Capture in 1–2 paragraphs:

- What does combat tempo feel like?
- How responsive does the cruiser feel to controls?
- Does mass/inertia register as a tactical factor?

## Post-rebase (this commit)

| Field | Value | Rationale |
|---|---|---|
| Keel mass | 30000 | 30 t hull — small cruiser scale |
| Keel lift | 80000 | 80 t lift — comfortable headroom over 58 t total |
| REACTOR mass | 10000 | 10 t — relative ratio preserved |
| MAIN_ENGINE mass | 7000 | 7 t |
| MAIN_ENGINE forwardThrust | 60000 | 60 kN → ~1.03 m/s² accel at total mass 58 t |
| MAIN_ENGINE lateralThrust | 12000 | 0.2× forward — cruisers don't strafe hard |
| MAIN_ENGINE reverseThrust | 25000 | 0.4× forward |
| MAIN_ENGINE angularThrust | 5000 | tunable; turn-rate target empirical |
| BRIDGE mass | 5000 | 5 t |
| Turret mass (×2) | 3000 | 3 t each |
| **Total mass** | **58000** | **~58 tonnes** |
| **Target accel** | **~1.03 m/s²** | **R4 plausibility target** |

### Post-rebase feel (TODO — fill in during dev playtest)

Play 1 cruiser-vs-cruiser session at the post-rebase values (current state). Capture:

- Does the slower accel land "tactical inertia is a feature, not friction" or feel sluggish?
- Compare engagement-distance closing time vs pre-rebase.
- Compare turn-radius / brake-distance feel.

## Comparison

The criterion (per origin D4 + plan D4) is *not* "feels identical" but **"the tuned-physical version is at least as fun as the unconstrained version."** If the rebased version feels worse, the dimensionless thrust-multiplier (or angular-thrust specifically) gets the next tuning pass. If it feels better, the physical-plausibility framing pays off.

Tracked under item C Exit Criteria.
