package jez.lastfleetprotocol.prototype.components.game.managers

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.actor.traits.Unique
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.EnemyShip
import jez.lastfleetprotocol.prototype.components.game.actors.PlayerShip
import jez.lastfleetprotocol.prototype.components.game.actors.Turret
import me.tatarka.inject.annotations.Inject

@Inject
class GameStateManager(
    private val stateManager: StateManager,
    private val actorManager: ActorManager,
    private val viewportManager: ViewportManager,
) : Manager(), Unique {
    fun setPaused(paused: Boolean) {
        if (paused == !stateManager.isRunning.value) return

        stateManager.updateIsRunning(paused)
    }

    fun startDemoScene() {
        val topLeft = viewportManager.topLeft.value
        val bottomRight = viewportManager.bottomRight.value
        val halfWidth = bottomRight.x - topLeft.x
        val height = bottomRight.y - topLeft.y

        val playerShip = PlayerShip(
            SceneOffset(
                x = topLeft.x + halfWidth / 2f,
                y = bottomRight.y - height / 10f,
            )
        )
        actorManager.add(
            playerShip
        )
        Turret(
            parent = playerShip.body,
            offsetFromParentPivot = SceneOffset(Offset(-50f, 0f)),
            pivot = SceneOffset(Offset(32f, 32f)),
        ).apply {
            actorManager.add(this)
        }
        Turret(
            parent = playerShip.body,
            offsetFromParentPivot = SceneOffset(Offset(50f, 0f)),
            pivot = SceneOffset(Offset(32f, 32f)),
        ).apply {
            actorManager.add(this)
        }
        actorManager.add(
            EnemyShip(
                SceneOffset(
                    x = topLeft.x + halfWidth / 2f,
                    y = bottomRight.y - (height / 10f) * 9f,
                )
            )
        )
    }
}