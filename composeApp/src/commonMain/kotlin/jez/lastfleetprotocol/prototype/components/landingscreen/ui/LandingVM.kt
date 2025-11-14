package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.lifecycle.viewModelScope
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import jez.lastfleetprotocol.prototype.utils.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    val hasSaveGame: Boolean?,
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

    companion object {
        val default = InternalState(
            saveGame = SaveGameState.Checking,
            musicEnabled = false,
            soundEffectsEnabled = false
        )
    }
}

@Inject
class LandingVM() : LFViewModel<LandingEvent, LandingState, LandingSideEffect>() {

    private val internalState = MutableStateFlow(InternalState.default)

    override val state: StateFlow<LandingState>
        get() = internalState
            .map { createViewState(it) }
            .stateInWhileSubscribed(viewModelScope, createViewState(InternalState.default))

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

    private companion object {
        fun createViewState(internalState: InternalState) = LandingState(
            musicEnabled = internalState.musicEnabled,
            soundEffectsEnabled = internalState.soundEffectsEnabled,
            hasSaveGame = when (internalState.saveGame) {
                is InternalState.SaveGameState.Checking -> null
                is InternalState.SaveGameState.Data -> true
                is InternalState.SaveGameState.NoSaveGame -> false
            }
        )
    }
}
