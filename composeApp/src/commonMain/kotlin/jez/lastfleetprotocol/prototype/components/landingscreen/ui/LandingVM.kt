package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.lifecycle.viewModelScope
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface LandingEvent {
    data class ToggleMusicClicked(val setEnabled: Boolean) : LandingEvent
    data class ToggleSoundEffectsClicked(val setEnabled: Boolean) : LandingEvent
    data object ShowSettingsClicked : LandingEvent
    data object PlayClicked : LandingEvent
}

data class LandingState(
    val musicEnabled: Boolean,
    val soundEffectsEnabled: Boolean,
    val hasSaveGame: Boolean,
)

sealed interface LandingSideEffect

private data class InternalState(
    val saveGame: SaveGameState,
    val musicEnabled: Boolean,
    val soundEffectsEnabled: Boolean,
) {
    sealed interface SaveGameState {
        data object Checking : SaveGameState
        data object NoSaveGame : SaveGameState
        data object Data : SaveGameState // TODO: populate
    }
}

@Inject
class LandingVM() : LFViewModel<LandingEvent, LandingState, LandingSideEffect>() {


    override val state: StateFlow<LandingState>
        get() = TODO("Not yet implemented")

    override fun accept(event: LandingEvent) {
        viewModelScope.launch {
            when (event) {
                is LandingEvent.PlayClicked -> TODO()
                is LandingEvent.ShowSettingsClicked -> TODO()
                is LandingEvent.ToggleMusicClicked -> TODO()
                is LandingEvent.ToggleSoundEffectsClicked -> TODO()
            }
        }
    }
}
