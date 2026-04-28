package jez.lastfleetprotocol.prototype.components.gamecore.scenarios

import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SceneOffsetSerializer
import kotlinx.serialization.Serializable

/**
 * A named, persistable battle composition: the slot layout the dev wants to
 * launch into the game. Saved as JSON in app-data; loaded by the scenario
 * builder; converted into the spawn-loop's input by [GameStateManager].
 *
 * `formatVersion` follows the same rule as `ShipDesign.formatVersion`: bump on
 * any breaking schema change, and version-detection lives at the read site.
 *
 * The `terrain` field is reserved for Item F (terrain placement). v1 always
 * defaults to an empty list and the scenario-builder UI does not expose it.
 */
@Serializable
data class Scenario(
    val name: String,
    val formatVersion: Int = CURRENT_VERSION,
    val slots: List<SpawnSlotConfig> = emptyList(),
    val terrain: List<TerrainConfig> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

/**
 * Public, serializable shape of a single spawn slot: which design to load,
 * where to place it, which team owns it, whether AI drives it, and which
 * draw-order layer it renders into.
 *
 * Replaces the previously-private `SpawnSlot` data class inside
 * `GameStateManager`. Consumed directly by `GameStateManager.startScene`.
 *
 * `teamId` values must match the team constants `Ship.TEAM_PLAYER` ("player")
 * and `Ship.TEAM_ENEMY` ("enemy") to participate in spawn / target wiring.
 * `drawOrder` mirrors `DrawOrder.PLAYER_SHIP` (10f) / `DrawOrder.ENEMY_SHIP`
 * (20f). Both are validated by `DemoScenarioPresetParityTest` (Unit 7).
 */
@Serializable
data class SpawnSlotConfig(
    val designName: String,
    @Serializable(with = SceneOffsetSerializer::class) val position: SceneOffset,
    val teamId: String,
    val withAI: Boolean,
    val drawOrder: Float,
)

/**
 * Forward-compat placeholder for Item F (terrain). Reserves the schema slot
 * so v1 scenarios deserialize unchanged when the real shape lands. Replaced
 * (and `Scenario.formatVersion` bumped) when terrain ships.
 */
@Serializable
data class TerrainConfig(
    val placeholder: String = "",
)

/**
 * Persistence contract for named scenarios. Sibling to `ShipDesignRepository`;
 * `FileScenarioRepository` is the file-backed implementation in
 * `:features:scenario-builder:impl`.
 */
interface ScenarioRepository {
    fun save(scenario: Scenario)
    fun load(name: String): Scenario?
    fun listAll(): List<String>
    fun delete(name: String)
}
