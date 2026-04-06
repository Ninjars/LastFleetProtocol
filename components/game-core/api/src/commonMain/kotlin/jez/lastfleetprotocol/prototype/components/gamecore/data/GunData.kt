package jez.lastfleetprotocol.prototype.components.gamecore.data

import com.pandulapeter.kubriko.types.AngleRadians
import org.jetbrains.compose.resources.DrawableResource

data class GunData(
    val drawable: DrawableResource,
    val projectileStats: ProjectileStats,
    val aimTolerance: AngleRadians,
    val magazineCapacity: Int,
    val reloadMilliseconds: Int,
    val shotsPerBurst: Int,
    val burstCycleMilliseconds: Int,
    val cycleMilliseconds: Int,
)
