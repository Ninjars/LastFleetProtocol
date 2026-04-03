package jez.lastfleetprotocol.prototype.components.game.debug

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.actors.EnemyShip
import jez.lastfleetprotocol.prototype.components.game.actors.PlayerShip
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.utils.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Debug overlay actor that draws movement and physics vectors for all registered ships.
 *
 * - White circle: player ship movement destination
 * - Red circle: enemy ship movement destination
 * - White line from ship origin: current rotation/facing direction
 * - Red line from ship origin: normalised velocity vector
 * - Blue line from ship origin: normalised acceleration vector
 * - White outline: hull collision polygon edges (armour segments)
 */
class DebugVisualiser : Visible, Dynamic {

    override val body: BoxBody = BoxBody(
        initialPosition = SceneOffset.Zero,
    )

    override val isAlwaysActive: Boolean = true
    override val drawingOrder: Float = -10f
    override val shouldClip: Boolean = false

    private val ships = mutableListOf<Ship>()

    fun registerShip(ship: Ship) {
        ships.add(ship)
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        ships.removeAll { it.isDestroyed }
    }

    init {
        body.size = SceneSize(1f.sceneUnit, 1f.sceneUnit)
    }

    override fun DrawScope.draw() {
        for (ship in ships) {
            drawShipDebug(ship)
        }
    }

    private fun DrawScope.drawShipDebug(ship: Ship) {
        val shipPos = ship.body.position
        val shipX = shipPos.x.raw
        val shipY = shipPos.y.raw

        // Draw destination circle
        ship.destination?.let { dest ->
            val destX = dest.x.raw
            val destY = dest.y.raw
            val color = if (ship is PlayerShip) Color.White else Color.Red
            drawCircle(
                color = color,
                radius = DEST_CIRCLE_RADIUS,
                center = Offset(destX, destY),
                style = Stroke(width = 2f),
            )
        }

        // White line: current facing direction (fixed length)
        val facingAngle = ship.body.rotation.normalized
        val facingEndX = shipX + cos(facingAngle) * FACING_LINE_LENGTH
        val facingEndY = shipY + sin(facingAngle) * FACING_LINE_LENGTH
        drawLine(
            color = Color.White,
            start = Offset(shipX, shipY),
            end = Offset(facingEndX, facingEndY),
            strokeWidth = 2f,
        )

        // Red line: normalised velocity vector
        val velocity = ship.physics.velocity
        val speed = velocity.length().raw
        if (speed > 0.1f) {
            val velScale = (speed / MAX_DISPLAY_SPEED).coerceAtMost(1f) * VECTOR_LINE_LENGTH
            val velNormX = velocity.x.raw / speed
            val velNormY = velocity.y.raw / speed
            drawLine(
                color = Color.Red,
                start = Offset(shipX, shipY),
                end = Offset(
                    shipX + velNormX * velScale,
                    shipY + velNormY * velScale,
                ),
                strokeWidth = 2f,
            )
        }

        // Blue line: normalised acceleration vector
        val accel = ship.physics.lastAcceleration
        val accelMag = accel.length().raw
        if (accelMag > 0.01f) {
            val accelScale = (accelMag / MAX_DISPLAY_ACCEL).coerceAtMost(1f) * VECTOR_LINE_LENGTH
            val accelNormX = accel.x.raw / accelMag
            val accelNormY = accel.y.raw / accelMag
            drawLine(
                color = Color.Blue,
                start = Offset(shipX, shipY),
                end = Offset(
                    shipX + accelNormX * accelScale,
                    shipY + accelNormY * accelScale,
                ),
                strokeWidth = 2f,
            )
        }

        // White outline: hull collision polygon edges (armour segments)
        val mask = ship.collisionMask
        val hullVertices = mask.vertices
        if (hullVertices.size >= 2) {
            val rotation = mask.rotation
            val maskPos = mask.position
            for (i in hullVertices.indices) {
                val v0 = hullVertices[i].rotate(rotation)
                val v1 = hullVertices[(i + 1) % hullVertices.size].rotate(rotation)
                drawLine(
                    color = Color.White,
                    start = Offset(
                        maskPos.x.raw + v0.x.raw,
                        maskPos.y.raw + v0.y.raw,
                    ),
                    end = Offset(
                        maskPos.x.raw + v1.x.raw,
                        maskPos.y.raw + v1.y.raw,
                    ),
                    strokeWidth = 1.5f,
                    alpha = 0.6f,
                )
            }
        }
    }

    companion object {
        private const val DEST_CIRCLE_RADIUS = 15f
        private const val FACING_LINE_LENGTH = 80f
        private const val VECTOR_LINE_LENGTH = 60f
        private const val MAX_DISPLAY_SPEED = 200f
        private const val MAX_DISPLAY_ACCEL = 50f
    }
}
