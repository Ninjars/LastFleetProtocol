package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities

sealed interface ScenarioBuilderIntent {

    // --- Slot edits ---
    data class AddSlot(val team: ScenarioTeam) : ScenarioBuilderIntent
    data class RemoveSlot(val slotId: String) : ScenarioBuilderIntent
    data class UpdateSlotShip(val slotId: String, val designName: String) : ScenarioBuilderIntent
    data class UpdateSlotPosition(val slotId: String, val x: Float, val y: Float) : ScenarioBuilderIntent
    data class ToggleSlotAi(val slotId: String) : ScenarioBuilderIntent

    // --- Per-team demo defaults ---
    data class UseDemoDefaults(val team: ScenarioTeam) : ScenarioBuilderIntent

    // --- Top bar ---
    data class RenameScenario(val name: String) : ScenarioBuilderIntent
    data object SaveClicked : ScenarioBuilderIntent
    data object LoadDialogOpenClicked : ScenarioBuilderIntent
    data object LoadDialogDismissed : ScenarioBuilderIntent
    data class LoadScenario(val name: String) : ScenarioBuilderIntent
    data object LaunchClicked : ScenarioBuilderIntent
    data object BackClicked : ScenarioBuilderIntent

    // --- Overwrite-confirm dialog ---
    data object ConfirmOverwrite : ScenarioBuilderIntent
    data object CancelOverwrite : ScenarioBuilderIntent
}
