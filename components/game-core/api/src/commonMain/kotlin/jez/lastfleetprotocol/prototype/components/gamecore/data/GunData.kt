package jez.lastfleetprotocol.prototype.components.gamecore.data

import com.pandulapeter.kubriko.types.AngleRadians
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.AngleRadiansSerializer
import kotlinx.serialization.Serializable

@Serializable
data class GunData(
    val projectileStats: ProjectileStats,
    @Serializable(with = AngleRadiansSerializer::class)
    val aimTolerance: AngleRadians,
    val magazineCapacity: Int,
    val reloadMilliseconds: Int,
    val shotsPerBurst: Int,
    val burstCycleMilliseconds: Int,
    val cycleMilliseconds: Int,
)
