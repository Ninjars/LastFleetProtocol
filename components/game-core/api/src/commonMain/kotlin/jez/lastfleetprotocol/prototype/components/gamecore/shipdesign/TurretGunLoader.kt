package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import jez.lastfleetprotocol.prototype.components.gamecore.data.GunData
import kotlinx.serialization.json.Json
import lastfleetprotocol.components.game_core.api.generated.resources.Res
/**
 * Loads turret gun data from the bundled turret_guns.json resource at startup.
 * Results are cached — [load] can be called repeatedly without re-reading the file.
 *
 * Returns a [Map] keyed by turret gun ID (e.g., "turret_standard", "turret_light",
 * "turret_heavy"), used by [ShipDesignConverter] to resolve [PlacedTurret.turretConfigId].
 *
 * Provide via `@Provides` in AppComponent — kotlin-inject is not available in game-core/api.
 */
class TurretGunLoader {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: Map<String, GunData>? = null

    /**
     * Load turret gun data. Must be called from a coroutine context
     * (Res.readBytes is suspending). Safe to call multiple times — returns cached results.
     */
    suspend fun load(): Map<String, GunData> {
        cached?.let { return it }

        val bytes = Res.readBytes("files/turret_guns.json")
        val guns: Map<String, GunData> = json.decodeFromString(bytes.decodeToString())

        cached = guns
        return guns
    }
}
