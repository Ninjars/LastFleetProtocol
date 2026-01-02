package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.turret_simple_1
import org.jetbrains.compose.resources.DrawableResource

data class GunData(
    val drawable: DrawableResource = Res.drawable.turret_simple_1,
    val reloadTime: Long = 500L,
    val bulletSpeed: Float = 100f,
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

    fun spawnBullet() {
        // TODO
    }

    fun update(deltaTimeInMilliseconds: Int) {
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
            Condition.FIRING -> fire()
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

    private fun fire() {
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
        spawnBullet()
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
        angleToTarget?.let { it < gunData.aimTolerance } ?: false
}
