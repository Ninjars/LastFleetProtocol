package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.lifecycle.viewModelScope
import jez.lastfleetprotocol.prototype.components.game.managers.UserPreferencesManager
import jez.lastfleetprotocol.prototype.components.shared.usecases.SetMusicEnabledUseCase
import jez.lastfleetprotocol.prototype.components.shared.usecases.SetSoundEffectsEnabledUseCase
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import jez.lastfleetprotocol.prototype.utils.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

sealed interface LandingSideEffect {
    data object StartNewGame : LandingSideEffect
    data object GoToSettings : LandingSideEffect
}

private data class InternalState(
    val saveGame: SaveGameState,
) {
    sealed interface SaveGameState {
        data object Checking : SaveGameState
        data object NoSaveGame : SaveGameState
        data object Data : SaveGameState // TODO: implement save game loading
    }

    companion object {
        val default = InternalState(
            saveGame = SaveGameState.Checking,
        )
    }
}

@Inject
class LandingVM(
    userPreferencesManager: UserPreferencesManager,
    private val setMusicEnabled: SetMusicEnabledUseCase,
    private val setSoundEffectsEnabled: SetSoundEffectsEnabledUseCase,
) : LFViewModel<LandingEvent, LandingState, LandingSideEffect>() {

    private val internalState = MutableStateFlow(InternalState.default)

    override val state: StateFlow<LandingState> = combine(
        internalState,
        userPreferencesManager.isMusicEnabled,
        userPreferencesManager.areSoundEffectsEnabled
    ) { internalState, musicEnabled, soundEffectsEnabled ->
        createViewState(internalState, musicEnabled, soundEffectsEnabled)
    }.stateInWhileSubscribed(
        viewModelScope, createViewState(
            internalState = InternalState.default,
            musicEnabled = false,
            soundEffectsEnabled = false,
        )
    )

    override fun accept(event: LandingEvent) {
        viewModelScope.launch {
            when (event) {
                is LandingEvent.PlayClicked -> handlePlayClicked(internalState.value.saveGame)
                is LandingEvent.ShowSettingsClicked -> sendSideEffect(LandingSideEffect.GoToSettings)
                is LandingEvent.ToggleMusicClicked -> setMusicEnabled(event.setEnabled)
                is LandingEvent.ToggleSoundEffectsClicked -> setSoundEffectsEnabled(event.setEnabled)
            }
        }
    }

    private suspend fun handlePlayClicked(
        saveGame: InternalState.SaveGameState
    ) {
        when (saveGame) {
            is InternalState.SaveGameState.Checking,
            is InternalState.SaveGameState.NoSaveGame -> sendSideEffect(LandingSideEffect.StartNewGame)

            is InternalState.SaveGameState.Data -> TODO("implement save game loading")
        }
    }

    private companion object {
        fun createViewState(internalState: InternalState, musicEnabled: Boolean, soundEffectsEnabled: Boolean) =
            LandingState(
                musicEnabled = musicEnabled,
                soundEffectsEnabled = soundEffectsEnabled,
                hasSaveGame = when (internalState.saveGame) {
                    is InternalState.SaveGameState.Checking -> null
                    is InternalState.SaveGameState.Data -> true
                    is InternalState.SaveGameState.NoSaveGame -> false
                }
            )
    }
}
