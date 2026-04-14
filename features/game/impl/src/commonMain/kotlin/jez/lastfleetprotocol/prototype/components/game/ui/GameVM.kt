package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager.GameResult
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface GameIntent {
    data object OpenMenuClicked : GameIntent
    data object BackPressed : GameIntent
    data object RestartClicked : GameIntent
    data object ResumeClicked : GameIntent
    data object ExitClicked : GameIntent
}

data class GameState(
    val kubriko: Kubriko,
    val gameResult: GameResult? = null,
    val isPaused: Boolean = false,
)

sealed interface GameSideEffect {
    data object NavigateBack : GameSideEffect
}

@Inject
class GameVM(
    gameStateHolder: GameStateHolder,
    private val gameStateManager: GameStateManager,
) : ViewModelContract<GameIntent, GameState, GameSideEffect>() {

    private val _isPaused = MutableStateFlow(false)

    override val state: StateFlow<GameState> = combine(
        gameStateManager.gameResult,
        _isPaused,
    ) { result, paused ->
        GameState(
            kubriko = gameStateHolder.gameKubriko,
            gameResult = result,
            isPaused = paused && result == null, // Don't show pause when game is over
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GameState(gameStateHolder.gameKubriko),
    )

    override fun accept(intent: GameIntent) {
        when (intent) {
            GameIntent.OpenMenuClicked -> pause()
            GameIntent.BackPressed -> {
                if (_isPaused.value) {
                    exit()
                } else {
                    pause()
                }
            }
            GameIntent.ResumeClicked -> resume()
            GameIntent.RestartClicked -> {
                _isPaused.value = false
                viewModelScope.launch { gameStateManager.restartScene() }
            }
            GameIntent.ExitClicked -> exit()
        }
    }

    private fun pause() {
        _isPaused.value = true
        gameStateManager.setPaused(true)
    }

    private fun resume() {
        _isPaused.value = false
        gameStateManager.setPaused(false)
    }

    private fun exit() {
        _isPaused.value = false
        viewModelScope.launch {
            sendSideEffect(GameSideEffect.NavigateBack)
        }
    }

    fun onWindowFocusLost() {
        // Only auto-pause if the game is actively running (not already paused or finished)
        if (!_isPaused.value && state.value.gameResult == null) {
            pause()
        }
    }

    init {
        viewModelScope.launch { gameStateManager.startDemoScene() }
    }
}
