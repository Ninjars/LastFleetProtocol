package jez.lastfleetprotocol.prototype.components.game.managers

import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import me.tatarka.inject.annotations.Inject

@Inject
class GameStateManager(
    private val stateManager: StateManager,
) : Manager(), Unique {
    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return

        stateManager.updateIsRunning(paused)
    }
}