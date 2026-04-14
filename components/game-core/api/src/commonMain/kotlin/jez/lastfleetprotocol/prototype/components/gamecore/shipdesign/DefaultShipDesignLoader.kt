package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import kotlinx.serialization.json.Json
import lastfleetprotocol.components.game_core.api.generated.resources.Res
/**
 * Loads the bundled default ship designs from commonMain composeResources at startup.
 * Results are cached — [loadAll] can be called repeatedly without re-reading files.
 *
 * Returns a [Map] keyed by filename stem (e.g., "player_ship", "enemy_light"),
 * which [GameStateManager] uses for filename-indexed spawn-slot mapping.
 *
 * Provide via `@Provides` in AppComponent — kotlin-inject is not available in game-core/api.
 */
class DefaultShipDesignLoader {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: Map<String, ShipDesign>? = null

    /**
     * Load all default ship designs. Must be called from a coroutine context
     * (Res.readBytes is suspending). Safe to call multiple times — returns cached results.
     */
    suspend fun loadAll(): Map<String, ShipDesign> {
        cached?.let { return it }

        val designs = mutableMapOf<String, ShipDesign>()
        for (name in SHIP_FILENAMES) {
            val bytes = Res.readBytes("files/default_ships/$name.json")
            val design = json.decodeFromString(ShipDesign.serializer(), bytes.decodeToString())
            designs[name] = design
        }

        cached = designs
        return designs
    }

    companion object {
        val SHIP_FILENAMES = listOf(
            "player_ship",
            "enemy_light",
            "enemy_medium",
            "enemy_heavy",
        )
    }
}
