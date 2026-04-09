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
)

@Serializable
data class SerializableArmourStats(
    val hardness: Float,
    val density: Float,
)

@Serializable
data class PlacedHullPiece(
    override val id: String,
    override val itemDefinitionId: String,
    @Serializable(with = SceneOffsetSerializer::class) override val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) override val rotation: AngleRadians,
    override val mirrorX: Boolean = false,
    override val mirrorY: Boolean = false,
) : PlacedItem

@Serializable
data class PlacedModule(
    override val id: String,
    override val itemDefinitionId: String = "",
    val systemType: String,
    @Serializable(with = SceneOffsetSerializer::class) override val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) override val rotation: AngleRadians,
    override val mirrorX: Boolean = false,
    override val mirrorY: Boolean = false,
    val parentHullId: String,
) : PlacedItem

@Serializable
data class PlacedTurret(
    override val id: String,
    override val itemDefinitionId: String = "",
    val turretConfigId: String,
    @Serializable(with = SceneOffsetSerializer::class) override val position: SceneOffset,
    @Serializable(with = AngleRadiansSerializer::class) override val rotation: AngleRadians,
    override val mirrorX: Boolean = false,
    override val mirrorY: Boolean = false,
    val parentHullId: String,
) : PlacedItem
