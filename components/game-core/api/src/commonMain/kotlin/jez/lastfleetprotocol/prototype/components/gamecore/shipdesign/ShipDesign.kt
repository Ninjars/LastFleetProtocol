package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.Serializable

@Serializable
data class ShipDesign(
    val name: String,
    val formatVersion: Int = 2,
    val itemDefinitions: List<ItemDefinition> = emptyList(),
    val placedHulls: List<PlacedHullPiece> = emptyList(),
    val placedModules: List<PlacedModule> = emptyList(),
    val placedTurrets: List<PlacedTurret> = emptyList(),
    @Deprecated("Use itemDefinitions instead")
    val hullPieces: List<HullPieceDefinition> = emptyList(),
)

@Deprecated("Use ItemDefinition with ItemAttributes.HullAttributes instead")
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
    val itemDefinitionId: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    @Deprecated("Use itemDefinitionId instead")
    val hullPieceId: String = "",
)

@Serializable
data class PlacedModule(
    val id: String,
    val itemDefinitionId: String = "",
    val systemType: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    val parentHullId: String,
)

@Serializable
data class PlacedTurret(
    val id: String,
    val itemDefinitionId: String = "",
    val turretConfigId: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) val rotation: AngleRadians,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    val parentHullId: String,
)
