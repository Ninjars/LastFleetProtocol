package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Bullet
import jez.lastfleetprotocol.prototype.components.game.actors.BulletData
import jez.lastfleetprotocol.prototype.components.game.utils.getRelativePoint
import jez.lastfleetprotocol.prototype.components.game.utils.rotate
import lastfleetprotocol.components.design.generated.resources.Res
import lastfleetprotocol.components.design.generated.resources.bullet_laser_green_10
import lastfleetprotocol.components.design.generated.resources.turret_simple_1
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs

data class GunData(
    val drawable: DrawableResource = Res.drawable.turret_simple_1,
    val projectileStats: ProjectileStats = ProjectileStats(
        damage = 10f,
        armourPiercing = 5f,
        toHitModifier = 0.1f,
        speed = 100f,
        lifetimeMs = 5000,
    ),
    val aimTolerance: AngleRadians = AngleRadians.TwoPi / 1440f,
    val magazineCapacity: Int,
    val reloadMilliseconds: Int,
    val shotsPerBurst: Int = 1,
    val burstCycleMilliseconds: Int = 0,
    val cycleMilliseconds: Int,
)

class Gun(
    private val turretBody: BoxBody,
    private val muzzleOffset: SceneOffset,
    private val gunData: GunData,
) {

    enum class Condition {
        READY,
        TARGETING,
        START_BURST,
        FIRING,
        CYCLING,
        EMPTY_MAG,
        LOADING,
    }

    var angleToTarget: AngleRadians? = null
    private var condition: Condition = Condition.READY
    private var fireCycleMillis: Int = 0
    private var reloadCycleMillis: Int = 0
    private var magazine: Int = gunData.magazineCapacity
    private var burstCounter: Int = gunData.shotsPerBurst

    fun spawnBullet(actorManager: ActorManager) {
        val bullet = Bullet(
            initialPosition = turretBody.getRelativePoint(muzzleOffset),
            initialRotation = turretBody.rotation,
            velocity = SceneOffset(
                gunData.projectileStats.speed.sceneUnit,
                0.sceneUnit
            ).rotate(turretBody.rotation),
            bulletData = BulletData(
                drawable = Res.drawable.bullet_laser_green_10,
            ),
            projectileStats = gunData.projectileStats,
            collidableTypes = emptyList()
        )
        actorManager.add(bullet)
    }

    fun update(deltaTimeInMilliseconds: Int, actorManager: ActorManager) {
        when (condition) {
            Condition.READY -> if (isMagEmpty()) {
                condition = Condition.EMPTY_MAG
            } else if (angleToTarget != null) {
                condition = Condition.TARGETING
            }

            Condition.TARGETING -> {
                val currentTarget = angleToTarget
                if (isMagEmpty()) {
                    condition = Condition.EMPTY_MAG
                } else if (currentTarget == null) {
                    condition = Condition.READY
                } else if (isAligned()) {
                    condition = Condition.START_BURST
                }
            }

            Condition.START_BURST -> startBurst()
            Condition.FIRING -> fire(actorManager)
            Condition.CYCLING -> cycle(deltaTimeInMilliseconds)
            Condition.EMPTY_MAG -> beginReload()
            Condition.LOADING -> updateReload(deltaTimeInMilliseconds)
        }
    }

    private fun isMagEmpty() = magazine <= 0

    private fun startBurst() {
        if (angleToTarget == null) {
            condition = Condition.READY
        } else {
            burstCounter = gunData.shotsPerBurst
            condition = Condition.FIRING
        }
    }

    private fun fire(actorManager: ActorManager) {
        if (isMagEmpty()) {
            condition = Condition.EMPTY_MAG
            return
        }
        magazine -= 1
        burstCounter -= 1
        fireCycleMillis += if (burstCounter > 0) {
            gunData.burstCycleMilliseconds
        } else {
            gunData.cycleMilliseconds
        }
        condition = Condition.CYCLING
        spawnBullet(actorManager)
    }

    private fun cycle(deltaTimeInMilliseconds: Int) {
        if (isMagEmpty()) {
            condition = Condition.EMPTY_MAG
            return
        }
        fireCycleMillis -= deltaTimeInMilliseconds
        if (fireCycleMillis <= 0) {
            condition = if (burstCounter > 0) {
                Condition.FIRING
            } else {
                Condition.START_BURST
            }
        }
    }

    private fun beginReload() {
        reloadCycleMillis = gunData.reloadMilliseconds
        fireCycleMillis = 0
        condition = Condition.LOADING
    }

    private fun updateReload(deltaTimeInMilliseconds: Int) {
        reloadCycleMillis -= deltaTimeInMilliseconds
        if (reloadCycleMillis <= 0) {
            magazine = gunData.magazineCapacity
            reloadCycleMillis = 0
            condition = Condition.READY
        }
    }

    private fun isAligned() =
        angleToTarget?.let {
            val normalized = it.normalized
            val wrappedAround = abs(Math.PI.toFloat() * 2f - normalized)
            normalized.rad < gunData.aimTolerance || wrappedAround.rad < gunData.aimTolerance
        } ?: false
}
