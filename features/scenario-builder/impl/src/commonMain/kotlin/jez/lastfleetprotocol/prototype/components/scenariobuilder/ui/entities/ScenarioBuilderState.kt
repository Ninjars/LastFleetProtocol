package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities

import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.Scenario

/**
 * UI-side representation of one row in the team panels. Maps to a
 * `SpawnSlotConfig` at launch time.
 *
 * Position is held as raw Float (x, y) for direct binding to numeric input
 * fields; conversion to Kubriko's `SceneOffset` happens at the launch boundary.
 */
data class SlotEntry(
    val id: String,
    val team: ScenarioTeam,
    val designName: String,
    val x: Float,
    val y: Float,
    val withAI: Boolean,
)

/**
 * Two-team enumeration for the scenario-builder UI. Maps to the canonical
 * `Ship.TEAM_PLAYER` / `TEAM_ENEMY` String constants via [id] when launching.
 */
enum class ScenarioTeam(val id: String) {
    PLAYER("player"),
    ENEMY("enemy"),
}

/**
 * MVI state for the scenario builder.
 *
 * `brokenSlotIds` and `canLaunch` are derived: broken-flag detection is gated
 * on [libraryReady] so the UI doesn't briefly show every slot as broken
 * during the initial async library load (the race fix called out in the plan).
 *
 * `showOverwriteConfirm` carries the pending-write [Scenario]: non-null when
 * `SaveClicked` detected a name collision and is waiting on `ConfirmOverwrite`
 * vs `CancelOverwrite`.
 */
data class ScenarioBuilderState(
    val designName: String = "",
    val slots: List<SlotEntry> = emptyList(),
    val savedScenarios: List<String> = emptyList(),
    val showLoadDialog: Boolean = false,
    val showOverwriteConfirm: Scenario? = null,
    val libraryShipNames: List<String> = emptyList(),
    val libraryReady: Boolean = false,
) {

    /** Derived from [savedScenarios]; the load dialog only opens with content. */
    val canShowLoadDialog: Boolean
        get() = savedScenarios.isNotEmpty()

    /**
     * Slots whose `designName` no longer resolves against the bundled library.
     * Empty until [libraryReady] flips true so the UI doesn't flash "all
     * slots broken" during the async library load.
     */
    val brokenSlotIds: Set<String>
        get() = if (!libraryReady) {
            emptySet()
        } else {
            slots.asSequence()
                .filter { it.designName !in libraryShipNames }
                .map { it.id }
                .toSet()
        }

    /**
     * Proactive Launch validation (R9): the Launch button is bound to this.
     * Each team must have at least one non-broken slot. The library must be
     * ready so broken-slot detection is meaningful.
     */
    val canLaunch: Boolean
        get() {
            if (!libraryReady) return false
            val broken = brokenSlotIds // capture once — avoid recomputing inside the any{} loops
            return slots.any { it.team == ScenarioTeam.PLAYER && it.id !in broken } &&
                slots.any { it.team == ScenarioTeam.ENEMY && it.id !in broken }
        }
}
