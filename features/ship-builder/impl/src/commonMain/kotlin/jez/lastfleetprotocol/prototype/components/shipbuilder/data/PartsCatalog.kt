package jez.lastfleetprotocol.prototype.components.shipbuilder.data

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.InternalSystemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats

data class CatalogHullPiece(
    val id: String,
    val name: String,
    val vertices: List<SceneOffset>,
    val armour: SerializableArmourStats,
    val sizeCategory: String,
    val mass: Float,
)

data class CatalogSystemModule(
    val id: String,
    val name: String,
    val type: InternalSystemType,
)

data class CatalogTurretModule(
    val id: String,
    val name: String,
    val configId: String,
)

/**
 * Pre-defined parts available in the ship builder.
 * Hull piece vertex/armour/mass values are duplicated from DemoScenarioConfig
 * since the builder module cannot depend on the game impl module.
 */
object PartsCatalog {

    val hullPieces: List<CatalogHullPiece> = listOf(
        CatalogHullPiece(
            id = "hull_player",
            name = "Player Hull",
            vertices = listOf(
                SceneOffset(56f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-30f).sceneUnit, 37f.sceneUnit),
                SceneOffset((-56f).sceneUnit, 20f.sceneUnit),
                SceneOffset((-56f).sceneUnit, (-20f).sceneUnit),
                SceneOffset((-30f).sceneUnit, (-37f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 5f, density = 2f),
            sizeCategory = "medium",
            mass = 50f,
        ),
        CatalogHullPiece(
            id = "hull_light",
            name = "Light Hull",
            vertices = listOf(
                SceneOffset(42f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-20f).sceneUnit, 30f.sceneUnit),
                SceneOffset((-42f).sceneUnit, 15f.sceneUnit),
                SceneOffset((-42f).sceneUnit, (-15f).sceneUnit),
                SceneOffset((-20f).sceneUnit, (-30f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 3f, density = 1f),
            sizeCategory = "light",
            mass = 30f,
        ),
        CatalogHullPiece(
            id = "hull_medium",
            name = "Medium Hull",
            vertices = listOf(
                SceneOffset(52f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-25f).sceneUnit, 42f.sceneUnit),
                SceneOffset((-52f).sceneUnit, 25f.sceneUnit),
                SceneOffset((-52f).sceneUnit, (-25f).sceneUnit),
                SceneOffset((-25f).sceneUnit, (-42f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 5f, density = 2f),
            sizeCategory = "medium",
            mass = 50f,
        ),
        CatalogHullPiece(
            id = "hull_heavy",
            name = "Heavy Hull",
            vertices = listOf(
                SceneOffset(52f.sceneUnit, 0f.sceneUnit),
                SceneOffset((-20f).sceneUnit, 50f.sceneUnit),
                SceneOffset((-52f).sceneUnit, 35f.sceneUnit),
                SceneOffset((-52f).sceneUnit, (-35f).sceneUnit),
                SceneOffset((-20f).sceneUnit, (-50f).sceneUnit),
            ),
            armour = SerializableArmourStats(hardness = 8f, density = 4f),
            sizeCategory = "heavy",
            mass = 100f,
        ),
    )

    val systemModules: List<CatalogSystemModule> = listOf(
        CatalogSystemModule(
            id = "system_reactor",
            name = "Reactor",
            type = InternalSystemType.REACTOR,
        ),
        CatalogSystemModule(
            id = "system_engine",
            name = "Main Engine",
            type = InternalSystemType.MAIN_ENGINE,
        ),
        CatalogSystemModule(
            id = "system_bridge",
            name = "Bridge",
            type = InternalSystemType.BRIDGE,
        ),
    )

    val turretModules: List<CatalogTurretModule> = listOf(
        CatalogTurretModule(
            id = "turret_standard",
            name = "Standard Turret",
            configId = "standard",
        ),
        CatalogTurretModule(
            id = "turret_light",
            name = "Light Turret",
            configId = "light",
        ),
        CatalogTurretModule(
            id = "turret_heavy",
            name = "Heavy Turret",
            configId = "heavy",
        ),
    )
}
