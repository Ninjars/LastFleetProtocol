package jez.lastfleetprotocol.prototype.components.game.ui

import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager.GameResult
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.tatarka.inject.annotations.Inject

sealed interface GameIntent {
    data object OpenMenuClicked : GameIntent
    data object BackPressed : GameIntent
    data object RestartClicked : GameIntent
}

data class GameState(
    val kubriko: Kubriko,
    val gameResult: GameResult? = null,
)

sealed interface GameSideEffect

@Inject
class GameVM(
    gameStateHolder: GameStateHolder,
    private val gameStateManager: GameStateManager,
) : ViewModelContract<GameIntent, GameState, GameSideEffect>() {
    override val state: StateFlow<GameState> = gameStateManager.gameResult
        .map { result -> GameState(gameStateHolder.gameKubriko, result) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameState(gameStateHolder.gameKubriko))

    override fun accept(intent: GameIntent) {
        when (intent) {
            GameIntent.RestartClicked -> gameStateManager.restartScene()
            GameIntent.OpenMenuClicked -> {} // TODO
            GameIntent.BackPressed -> {} // TODO
        }
    }

    init {
        gameStateManager.startDemoScene()
    }
}
