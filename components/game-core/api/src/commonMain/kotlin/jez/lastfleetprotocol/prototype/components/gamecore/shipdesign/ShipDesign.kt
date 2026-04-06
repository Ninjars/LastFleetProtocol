package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.Serializable

@Serializable
data class ShipDesign(
    val name: String,
    val hullPieces: List<HullPieceDefinition>,
    val placedHulls: List<PlacedHullPiece>,
    val placedModules: List<PlacedModule>,
    val placedTurrets: List<PlacedTurret>,
)

@Serializable
data class HullPieceDefinition(
    val id: String,
    val vertices: List<@Serializable(with = SceneOffsetSerializer::class) SceneOffset>,
    val armour: SerializableArmourStats,
    val sizeCategory: String,
    val mass: Float,
)

@Serializable
data class SerializableArmourStats(
    val hardness: Float,
    val density: Float,
)

@Serializable
data class PlacedHullPiece(
    val id: String,
    val hullPieceId: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
)

@Serializable
data class PlacedModule(
    val id: String,
    val systemType: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
    val parentHullId: String,
)

@Serializable
data class PlacedTurret(
    val id: String,
    val turretConfigId: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
    val parentHullId: String,
)
