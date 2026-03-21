package jez.lastfleetprotocol.prototype.components.shared.usecases

import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import me.tatarka.inject.annotations.Inject

@Inject
class SetGamePausedUseCase(
    private val gameStateManager: GameStateManager,
) {
    operator fun invoke(paused: Boolean) {
        gameStateManager.setPaused(paused)
    }
}
