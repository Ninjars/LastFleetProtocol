package jez.lastfleetprotocol.prototype.components.game.debug

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.actors.Ship
import jez.lastfleetprotocol.prototype.components.game.actors.ShipLifecycle
import jez.lastfleetprotocol.prototype.components.game.debug.DebugVisualiser.Companion.RANGE_RING_ORBIT_FRACTION
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
 * - Faint outer ring: largest turret effective range (drag-aware firing reach)
 * - Faint inner ring: AI orbit-engagement distance ([RANGE_RING_ORBIT_FRACTION] × max effective range)
 * - Faint grey line + cross: per-turret current lead-aim point — tail anchored
 *   at the turret position, cross marking the world-space point the turret has
 *   solved for. Drawn only while a turret has a valid target.
 */
class DebugVisualiser : Visible, Dynamic {

    override val body: BoxBody = BoxBody(
        initialPosition = SceneOffset.Zero,
    )

    override val isAlwaysActive: Boolean = true
    override val drawingOrder: Float = -10f
    override val shouldClip: Boolean = false

    private lateinit var actorManager: ActorManager

    override fun onAdded(kubriko: Kubriko) {
        actorManager = kubriko.get()
    }

    override fun update(deltaTimeInMilliseconds: Int) {
    }

    init {
        body.size = SceneSize(1f.sceneUnit, 1f.sceneUnit)
    }

    override fun DrawScope.draw() {
        for (actor in actorManager.allActors.value) {
            if (actor is Ship) {
                if (actor.lifecycle !is ShipLifecycle.Destroyed) {
                    drawShipDebug(actor)
                }
            }
        }
    }

    private fun DrawScope.drawShipDebug(ship: Ship) {
        val shipPos = ship.body.position
        val shipX = shipPos.x.raw
        val shipY = shipPos.y.raw

        // Range rings: faint concentric circles centred on the ship showing
        // its turret reach. Drawn first so other debug elements layer on top.
        drawShipRangeRings(ship, shipX, shipY)

        // White outline: hull collision polygon edges (armour segments) — one per hull collider
        for (hullCollider in ship.hullColliders) {
            val mask = hullCollider.collisionMask
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

        // Draw destination circle
        ship.destination?.let { dest ->
            val destX = dest.x.raw
            val destY = dest.y.raw
            val color = if (ship.teamId == Ship.TEAM_PLAYER) Color.White else Color.Red
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

        // Per-turret lead-aim visualisation: faint line from turret to its
        // current aim point, cross at the aim point. Skipped when the turret
        // has no valid target.
        for (turret in ship.turrets) {
            val aim = turret.currentAimPoint ?: continue
            val turretPos = turret.body.position
            drawAimMarker(
                turretX = turretPos.x.raw,
                turretY = turretPos.y.raw,
                aimX = aim.x.raw,
                aimY = aim.y.raw,
            )
        }
    }

    private fun DrawScope.drawAimMarker(
        turretX: Float,
        turretY: Float,
        aimX: Float,
        aimY: Float,
    ) {
        drawLine(
            color = AIM_MARKER_COLOR,
            start = Offset(turretX, turretY),
            end = Offset(aimX, aimY),
            strokeWidth = 1f,
            alpha = AIM_MARKER_LINE_ALPHA,
        )
        drawLine(
            color = AIM_MARKER_COLOR,
            start = Offset(aimX - AIM_MARKER_HALF_SIZE, aimY),
            end = Offset(aimX + AIM_MARKER_HALF_SIZE, aimY),
            strokeWidth = 1.5f,
            alpha = AIM_MARKER_CROSS_ALPHA,
        )
        drawLine(
            color = AIM_MARKER_COLOR,
            start = Offset(aimX, aimY - AIM_MARKER_HALF_SIZE),
            end = Offset(aimX, aimY + AIM_MARKER_HALF_SIZE),
            strokeWidth = 1.5f,
            alpha = AIM_MARKER_CROSS_ALPHA,
        )
    }

    /**
     * Draws two concentric circles per ship at scene-unit (= metres) radii:
     * the outer at the ship's largest turret effective range, the inner at
     * the AI orbit-engagement distance ([RANGE_RING_ORBIT_FRACTION] × outer).
     * Mirrors the firing-range gate ([Turret.effectiveRangeM]) and the
     * BasicAI orbit-distance derivation. No-op for ships without turrets
     * ([Ship.maxTurretEffectiveRangeM] returns 0 → guard skips the draws).
     *
     * `RANGE_RING_ORBIT_FRACTION` mirrors `BasicAI.ORBIT_RANGE_FRACTION`. The
     * value is duplicated here rather than referenced because the BasicAI
     * constant is module-private and the two debug elements are intentionally
     * tied to that single ratio — if the orbit fraction changes, both move
     * together.
     */
    private fun DrawScope.drawShipRangeRings(ship: Ship, shipX: Float, shipY: Float) {
        val maxRange = ship.maxTurretEffectiveRangeM()
        if (maxRange <= 0f) return

        val color = if (ship.teamId == Ship.TEAM_PLAYER) Color.White else Color.Red
        val centre = Offset(shipX, shipY)

        drawCircle(
            color = color,
            radius = maxRange,
            center = centre,
            style = Stroke(width = 6f),
            alpha = RANGE_RING_OUTER_ALPHA,
        )
        drawCircle(
            color = color,
            radius = maxRange * RANGE_RING_ORBIT_FRACTION,
            center = centre,
            style = Stroke(width = 4f),
            alpha = RANGE_RING_INNER_ALPHA,
        )
    }

    companion object {
        private const val DEST_CIRCLE_RADIUS = 15f
        private const val FACING_LINE_LENGTH = 80f
        private const val VECTOR_LINE_LENGTH = 300f
        private const val MAX_DISPLAY_SPEED = 200f
        private const val MAX_DISPLAY_ACCEL = 25f
        private const val RANGE_RING_ORBIT_FRACTION = 0.8f
        private const val RANGE_RING_OUTER_ALPHA = 0.25f
        private const val RANGE_RING_INNER_ALPHA = 0.18f
        private val AIM_MARKER_COLOR = Color(0.85f, 0.85f, 0.85f, 1f)
        private const val AIM_MARKER_LINE_ALPHA = 0.25f
        private const val AIM_MARKER_CROSS_ALPHA = 0.7f
        private const val AIM_MARKER_HALF_SIZE = 12f
    }
}
