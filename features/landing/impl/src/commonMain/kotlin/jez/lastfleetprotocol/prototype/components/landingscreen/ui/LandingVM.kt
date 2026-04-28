package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.gamecore.GameSessionState
import jez.lastfleetprotocol.prototype.components.preferences.SetMusicEnabled
import jez.lastfleetprotocol.prototype.components.preferences.SetSoundEffectsEnabled
import jez.lastfleetprotocol.prototype.components.preferences.UserPreferences
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import jez.lastfleetprotocol.prototype.utils.export.DevToolsGate
import jez.lastfleetprotocol.prototype.utils.stateInWhileSubscribed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface LandingIntent {
    data class ToggleMusicClicked(val setEnabled: Boolean) : LandingIntent
    data class ToggleSoundEffectsClicked(val setEnabled: Boolean) : LandingIntent
    data object ShowSettingsClicked : LandingIntent
    data object PlayClicked : LandingIntent
    data object ShipBuilderClicked : LandingIntent
    data object ScenarioBuilderClicked : LandingIntent
}

data class LandingState(
    val musicEnabled: Boolean,
    val soundEffectsEnabled: Boolean,
    val hasSaveGame: Boolean?,
    val kubriko: Kubriko,
    /**
     * Item B: dev-only `Scenario Builder (dev)` link visibility. Frozen at
     * VM construction from `DevToolsGate.isAvailable` — the gate doesn't
     * change at runtime within a process, so a static snapshot is sufficient.
     */
    val canShowDevTools: Boolean,
)

sealed interface LandingSideEffect {
    data object StartNewGame : LandingSideEffect
    data object GoToSettings : LandingSideEffect
    data object GoToShipBuilder : LandingSideEffect
    data object GoToScenarioBuilder : LandingSideEffect
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
    private val gameStateHolder: GameSessionState,
    userPreferences: UserPreferences,
    private val setMusicEnabled: SetMusicEnabled,
    private val setSoundEffectsEnabled: SetSoundEffectsEnabled,
    devToolsGate: DevToolsGate,
) : ViewModelContract<LandingIntent, LandingState, LandingSideEffect>() {

    private val internalState = MutableStateFlow(InternalState.default)

    // Item B: gate snapshot is taken once at construction. The gate's
    // underlying value (system property + filesystem) is fixed for the
    // process lifetime, so a one-shot read is sufficient — no Flow needed.
    private val canShowDevTools: Boolean = devToolsGate.isAvailable

    override val state: StateFlow<LandingState> = combine(
        internalState,
        userPreferences.isMusicEnabled,
        userPreferences.areSoundEffectsEnabled
    ) { internalState, musicEnabled, soundEffectsEnabled ->
        createViewState(
            internalState,
            musicEnabled,
            soundEffectsEnabled,
            gameStateHolder.gameKubriko,
            canShowDevTools,
        )
    }.stateInWhileSubscribed(
        viewModelScope, createViewState(
            internalState = InternalState.default,
            musicEnabled = false,
            soundEffectsEnabled = false,
            kubriko = gameStateHolder.gameKubriko,
            canShowDevTools = canShowDevTools,
        )
    )

    override fun accept(intent: LandingIntent) {
        viewModelScope.launch {
            when (intent) {
                is LandingIntent.PlayClicked -> handlePlayClicked(internalState.value.saveGame)
                is LandingIntent.ShowSettingsClicked -> sendSideEffect(LandingSideEffect.GoToSettings)
                is LandingIntent.ShipBuilderClicked -> sendSideEffect(LandingSideEffect.GoToShipBuilder)
                is LandingIntent.ScenarioBuilderClicked -> sendSideEffect(LandingSideEffect.GoToScenarioBuilder)
                is LandingIntent.ToggleMusicClicked -> setMusicEnabled(intent.setEnabled)
                is LandingIntent.ToggleSoundEffectsClicked -> setSoundEffectsEnabled(intent.setEnabled)
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
        fun createViewState(
            internalState: InternalState,
            musicEnabled: Boolean,
            soundEffectsEnabled: Boolean,
            kubriko: Kubriko,
            canShowDevTools: Boolean,
        ) =
            LandingState(
                kubriko = kubriko,
                musicEnabled = musicEnabled,
                soundEffectsEnabled = soundEffectsEnabled,
                hasSaveGame = when (internalState.saveGame) {
                    is InternalState.SaveGameState.Checking -> null
                    is InternalState.SaveGameState.Data -> true
                    is InternalState.SaveGameState.NoSaveGame -> false
                },
                canShowDevTools = canShowDevTools,
            )
    }
}
