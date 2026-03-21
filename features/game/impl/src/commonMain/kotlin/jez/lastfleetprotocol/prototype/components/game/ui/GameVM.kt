package jez.lastfleetprotocol.prototype.components.game.ui

import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject

sealed interface GameIntent {
    data object OpenMenuClicked : GameIntent
    data object BackPressed : GameIntent
}

data class GameState(
    val kubriko: Kubriko,
)

sealed interface GameSideEffect

@Inject
class GameVM(
    gameStateHolder: GameStateHolder,
    gameStateManager: GameStateManager,
) : ViewModelContract<GameIntent, GameState, GameSideEffect>() {
    override val state: StateFlow<GameState> =
        MutableStateFlow(GameState(gameStateHolder.gameKubriko))

    override fun accept(intent: GameIntent) {
//        TODO("Not yet implemented")
    }

    init {
        gameStateManager.startDemoScene()
    }
}
