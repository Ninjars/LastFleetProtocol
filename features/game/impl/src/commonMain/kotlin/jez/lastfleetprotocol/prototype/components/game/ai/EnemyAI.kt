package jez.lastfleetprotocol.prototype.components.game.ai

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.PlayerShip
import jez.lastfleetprotocol.prototype.components.game.actors.Ship

/**
 * Encapsulates enemy AI decision-making: target selection, engagement
 * positioning, and weapon tracking.
 *
 * The AI periodically selects the nearest valid player ship as its target,
 * manoeuvres to weapon range offset perpendicular to the bearing (to avoid
 * head-on approaches), and keeps turrets tracking the target.
 */
class EnemyAI(
    private val weaponRange: Float = 300f,
    private val retargetIntervalMs: Int = 500,
) {
    private var retargetTimerMs: Int = 0
    private var currentTarget: PlayerShip? = null

    fun update(ship: Ship, playerShips: List<PlayerShip>, deltaMs: Int) {
        retargetTimerMs -= deltaMs

        if (retargetTimerMs <= 0) {
            retargetTimerMs = retargetIntervalMs
            if (ship.shipSystems.hasFireControl()) {
                currentTarget = findNearestValidTarget(ship, playerShips)
            }
            // If fire control is lost, keep last target but don't retarget
        }

        val target = currentTarget
        if (target != null && target.isValidTarget()) {
            val engagementPosition = calculateEngagementPosition(ship, target)
            ship.moveTo(engagementPosition)
            ship.setTarget(target)
        } else {
            currentTarget = null
            ship.setTarget(null)
        }
    }

    private fun findNearestValidTarget(
        ship: Ship,
        playerShips: List<PlayerShip>,
    ): PlayerShip? {
        var nearest: PlayerShip? = null
        var nearestDistSq = Float.MAX_VALUE

        for (candidate in playerShips) {
            if (!candidate.isValidTarget()) continue
            val delta = candidate.body.position - ship.body.position
            val distSq = delta.length().raw.let { it * it }
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq
                nearest = candidate
            }
        }
        return nearest
    }

    /**
     * Calculate a position at [weaponRange] from the target, offset 90 degrees
     * from the direct bearing to create flanking movement.
     */
    private fun calculateEngagementPosition(ship: Ship, target: PlayerShip): SceneOffset {
        val toTarget = target.body.position - ship.body.position
        val distance = toTarget.length()

        // If very close to target, use a fallback direction
        if (distance.raw < 1f) {
            return target.body.position + SceneOffset(
                x = weaponRange.sceneUnit,
                y = 0f.sceneUnit,
            )
        }

        val direction = toTarget.normalized()

        // Perpendicular offset (rotate bearing 90 degrees: (dx, dy) -> (-dy, dx))
        val perpendicular = SceneOffset(
            x = (-direction.y.raw).sceneUnit,
            y = direction.x.raw.sceneUnit,
        )

        // Position at weaponRange along the bearing, offset to one side
        val rangeOffset = direction * weaponRange
        val sideOffset = perpendicular * (weaponRange * 0.3f)

        return target.body.position - rangeOffset + sideOffset
    }
}
