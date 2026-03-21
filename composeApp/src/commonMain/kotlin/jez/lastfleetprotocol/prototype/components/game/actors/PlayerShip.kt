package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.toSceneOffset
import com.pandulapeter.kubriko.pointerInput.PointerInputAware
import com.pandulapeter.kubriko.pointerInput.PointerInputManager
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.data.DrawOrder
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.ship_player_1

class PlayerShip(
    spec: ShipSpec,
    initialPosition: SceneOffset,
    turrets: List<Turret>,
) : PointerInputAware, Ship(
    spec = spec,
    drawable = Res.drawable.ship_player_1,
    initialPosition = initialPosition,
    turrets = turrets,
) {
    override val drawingOrder: Float = DrawOrder.PLAYER_SHIP

    private lateinit var pointerInputManager: PointerInputManager

    override fun onAdded(kubriko: Kubriko) {
        super<Ship>.onAdded(kubriko)
        pointerInputManager = kubriko.get()
    }

    override fun onPointerPressed(pointerId: PointerId, screenOffset: Offset) {
//        super.onPointerPressed(pointerId, screenOffset)
//
//        moveTo(screenOffset.toSceneOffset(viewportManager))
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        super.update(deltaTimeInMilliseconds)
        val pressedPointerPositions = pointerInputManager.pressedPointerPositions.value

        pressedPointerPositions.firstNotNullOfOrNull { it }?.let {
            moveTo(it.value.toSceneOffset(viewportManager))
        }
    }
}
