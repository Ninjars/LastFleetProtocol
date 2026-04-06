package jez.lastfleetprotocol.prototype.components.game.ai

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Ship

/**
 * Basic combat AI that selects the nearest valid target from the ship's
 * target provider, manoeuvres to weapon range, and keeps turrets tracking.
 */
class BasicAI(
    private val weaponRange: Float = 300f,
    private val retargetIntervalMs: Int = 500,
) : AIModule {
    private var retargetTimerMs: Int = 0
    private var currentTarget: Ship? = null

    override fun update(ship: Ship, deltaMs: Int) {
        val targets = ship.targetProvider()

        retargetTimerMs -= deltaMs

        if (retargetTimerMs <= 0) {
            retargetTimerMs = retargetIntervalMs
            if (ship.shipSystems.hasFireControl()) {
                currentTarget = findNearestValidTarget(ship, targets)
            }
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
        targets: List<Ship>,
    ): Ship? {
        var nearest: Ship? = null
        var nearestDistSq = Float.MAX_VALUE

        for (candidate in targets) {
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

    private fun calculateEngagementPosition(ship: Ship, target: Ship): SceneOffset {
        val toTarget = target.body.position - ship.body.position
        val distance = toTarget.length()

        if (distance.raw < 1f) {
            return target.body.position + SceneOffset(
                x = weaponRange.sceneUnit,
                y = 0f.sceneUnit,
            )
        }

        val direction = toTarget.normalized()
        val perpendicular = SceneOffset(
            x = (-direction.y.raw).sceneUnit,
            y = direction.x.raw.sceneUnit,
        )

        val rangeOffset = direction * weaponRange
        val sideOffset = perpendicular * (weaponRange * 0.3f)

        return target.body.position - rangeOffset + sideOffset
    }
}
