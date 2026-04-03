package jez.lastfleetprotocol.prototype.components.game.input

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.toSceneOffset
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.pointerInput.PointerInputAware
import androidx.compose.ui.input.pointer.PointerId
import jez.lastfleetprotocol.prototype.components.game.actors.PlayerShip
import kotlin.time.TimeSource

class InputController : PointerInputAware, Dynamic {

    override val isAlwaysActive: Boolean = true

    private lateinit var viewportManager: ViewportManager

    private val playerShips = mutableListOf<PlayerShip>()
    private var selectedShip: PlayerShip? = null

    private val timeSource = TimeSource.Monotonic
    private val pointerPressPositions = mutableMapOf<PointerId, Offset>()
    private val pointerPressTimes = mutableMapOf<PointerId, TimeSource.Monotonic.ValueTimeMark>()

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
    }

    override fun update(deltaTimeInMilliseconds: Int) = Unit

    fun registerPlayerShip(ship: PlayerShip) {
        playerShips.add(ship)
    }

    override fun onPointerPressed(pointerId: PointerId, screenOffset: Offset) {
        pointerPressPositions[pointerId] = screenOffset
        pointerPressTimes[pointerId] = timeSource.markNow()
    }

    override fun onPointerReleased(pointerId: PointerId, screenOffset: Offset) {
        val pressPos = pointerPressPositions.remove(pointerId) ?: return
        val pressMark = pointerPressTimes.remove(pointerId) ?: return

        val distance = (screenOffset - pressPos).getDistance()
        val elapsed = pressMark.elapsedNow().inWholeMilliseconds

        if (distance < TAP_DISTANCE_THRESHOLD && elapsed < TAP_TIME_THRESHOLD) {
            handleTap(screenOffset)
        }
    }

    override fun onPointerDrag(screenOffset: Offset) {
        viewportManager.addToCameraPosition(screenOffset)
    }

    override fun onPointerZoom(position: Offset, factor: Float) {
        viewportManager.multiplyScaleFactor(factor)
    }

    private fun handleTap(screenOffset: Offset) {
        val scenePos = screenOffset.toSceneOffset(viewportManager)

        val hitShip = playerShips.firstOrNull { ship ->
            ship.collisionMask.isSceneOffsetInside(scenePos)
        }

        if (hitShip != null) {
            selectedShip = hitShip
        } else if (selectedShip != null) {
            selectedShip?.moveTo(scenePos)
        }
    }

    private companion object {
        const val TAP_DISTANCE_THRESHOLD = 20f
        const val TAP_TIME_THRESHOLD = 300L
    }
}
