## AI Workflows

Standard operating workflows for agents working in this codebase.

## Workflow 1: New Feature (Non-Game-Loop)

Use for splash/landing/settings/common UI and standard app logic.

1. Confirm affected modules and ownership.
2. Decide whether contract changes are needed (`api`) or implementation-only (`impl`).
3. Implement using existing MVI conventions.
4. Update navigation/composition in `:composeApp` only when required.
5. Validate compile paths and summarize impact.

## Workflow 2: Bug Fix

1. Reproduce or locate fault path.
2. Find smallest safe fix in owning module.
3. Avoid opportunistic refactors during fix.
4. Add/update tests if present for touched area.
5. Validate and document behavior change.

## Workflow 3: Game Logic / Simulation Change

1. Read domain context first:
    - `docs/game_engine_principles.md`
    - `docs/game_design.md`
    - `docs/ship.md`
    - `docs/weapons.md`
2. Confirm whether change affects deterministic simulation, rendering, loading, or audio.
3. Keep simulation rules separate from UI concerns.
4. Make minimal Kubriko-aligned edits.
5. Validate for regressions in update/render behavior and side effects.
6. Document new features in appropriate `docs/` .md files, either updating existing documents or
   creating new .md files specific to that feature, system or functionality. This documentation will
   be to help AI agents in the future have appropriate context for continuing developing that
   feature, or for understanding how to interact with it.

## Workflow 4: Balance/Data Tuning

1. Identify tuning surface (ship stats, weapon behavior, AI parameters).
2. Prefer data-level/config-level updates over logic rewrites.
3. Keep changes traceable (clear commit-ready summary and rationale).
4. Re-check consistency with design docs.

## Workflow 5: Architecture Refactor

Only execute when explicitly requested.

1. Capture current module/dependency state.
2. Define target dependency direction.
3. Move one boundary at a time.
4. Verify each step builds before next move.
5. Update architecture docs and AGENTS instructions.

## Validation Checklist by Change Type

### All Changes

- Builds remain logically valid.
- Boundaries are respected or intentionally updated.
- Summary explains affected modules and why.

### UI Changes

- State and side-effects still route through ViewModel contract.
- No business logic drifted into Composables.

### Data/Domain Changes

- Rules align with `docs/game_design.md`, `docs/ship.md`, `docs/weapons.md`.
- Serialization/state model impact is considered.

### Kubriko Changes

- Engine/plugin lifecycle impact reviewed.
- Loading/audio/render loop behavior remains coherent.

## Safe Defaults for Ambiguous Tasks

If requirements are unclear:

- Prefer implementation-only changes over API changes.
- Prefer additive changes over destructive rewrites.
- Preserve existing module graph unless refactor is requested.
- Ask for clarification when multiple architecture-impacting options exist.
