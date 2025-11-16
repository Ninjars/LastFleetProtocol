package jez.lastfleetprotocol.prototype.components.game.ui

import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject

sealed interface GameEvent {
    data object OpenMenuClicked : GameEvent
    data object BackPressed : GameEvent
}

data class GameState(
    val kubriko: Kubriko,
)

sealed interface GameSideEffect

@Inject
class GameVM(
    gameStateHolder: GameStateHolder,
) : LFViewModel<GameEvent, GameState, GameSideEffect>() {
    override val state: StateFlow<GameState> = MutableStateFlow(GameState(gameStateHolder.gameKubriko))

    override fun accept(event: GameEvent) {
//        TODO("Not yet implemented")
    }
}
