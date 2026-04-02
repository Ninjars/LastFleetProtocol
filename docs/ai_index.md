## AI Docs Index

Start here for AI-assisted development in this repository.

## Primary Entry

- `AGENTS.md` (repository root): canonical instructions and guardrails for agents.

## AI Guidance Docs

- `docs/ai_architecture.md`
  - Module topology
  - Dependency direction rules
  - `api`/`impl` ownership guidance
  - `composeApp` integration role

- `docs/ai_workflows.md`
  - Standard workflows (feature, bugfix, game logic, tuning, refactor)
  - Validation checklists by change type
  - Safe defaults for ambiguous tasks

- `docs/ai_kubriko_constraints.md`
  - Kubriko runtime constraints
  - Rendering/simulation/loading/audio cautions
  - Plugin usage expectations

## Domain Design Docs

- `docs/game_design.md` for game-level design constraints and interaction model.
- `docs/ship.md` for ship mechanics and behavior details.
- `docs/weapons.md` for weapon systems and balancing context.

## Suggested Agent Read Order

1. `AGENTS.md`
2. `docs/ai_architecture.md`
3. `docs/ai_workflows.md`
4. `docs/ai_kubriko_constraints.md`
5. Domain docs relevant to task (`game_design`, `ship`, `weapons`)

## Quick Routing by Task Type

- Module/dependency question → `docs/ai_architecture.md`
- “How should I implement this task?” → `docs/ai_workflows.md`
- Game runtime/render/audio/loading change → `docs/ai_kubriko_constraints.md`
- Balance/mechanics behavior question → `docs/ship.md` and `docs/weapons.md`
