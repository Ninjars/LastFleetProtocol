package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities

sealed interface ScenarioBuilderSideEffect {

    /**
     * The VM has written `PendingScenario.slots`; the screen should now
     * navigate to the Game destination. `GameVM.init` will consume-and-clear
     * the pending slots and call `startScene` with them.
     */
    data object LaunchScenario : ScenarioBuilderSideEffect

    /** Pop back to the landing screen. Discards any unsaved in-progress edits. */
    data object NavigateBack : ScenarioBuilderSideEffect

    /** Surface a transient message in the screen-root snackbar. */
    data class ShowToast(val text: String) : ScenarioBuilderSideEffect
}
