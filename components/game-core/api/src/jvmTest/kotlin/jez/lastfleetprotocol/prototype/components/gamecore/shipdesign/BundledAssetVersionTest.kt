package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import lastfleetprotocol.components.game_core.api.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Asset export (Item A) Unit 6 — the discipline gate.
 *
 * Loads every committed bundled asset under
 * `components/game-core/api/src/commonMain/composeResources/files/` and asserts:
 *   - It deserializes via [kotlinx.serialization].
 *   - For ship designs: `formatVersion == ShipDesign.CURRENT_VERSION`.
 *   - For library items: deserializes-only (the `ItemDefinition` schema does not
 *     currently carry a `formatVersion` field — D introduces versioning when slot-based
 *     content lands; until then, parts coverage is "decodes successfully").
 *
 * If a future schema change forgets to bump bundled assets in lockstep, this test
 * fails before the divergence reaches a runtime loader.
 */
class BundledAssetVersionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun every_bundled_default_ship_carries_current_format_version() = runTest {
        for (filename in DefaultShipDesignLoader.SHIP_FILENAMES) {
            val bytes = Res.readBytes("files/default_ships/$filename.json")
            val parsed = json.decodeFromString<ShipDesign>(bytes.decodeToString())

            assertEquals(
                expected = ShipDesign.CURRENT_VERSION,
                actual = parsed.formatVersion,
                message = "default_ships/$filename.json has formatVersion=${parsed.formatVersion}, " +
                    "expected ${ShipDesign.CURRENT_VERSION}. Bump the file in lockstep with " +
                    "ShipDesign schema changes, or update CURRENT_VERSION if this was an " +
                    "intentional bump.",
            )
            assertNotNull(parsed.name, "default_ships/$filename.json deserialized but `name` is null")
        }
    }

    // The default_parts/ directory is created by the first export under Unit 1+2. Until
    // a part is committed, the parts arm of this test has nothing to read. When parts
    // start landing, the loop below picks them up automatically — `Res.readBytes` is
    // suspending and the manifest of files isn't enumerable from common code without a
    // dedicated index, so `default_parts/` gains explicit asset coverage when D's
    // brainstorm decides which parts ship as canonical bundled content.
    //
    // Empty for v1; the discipline gate exists and will catch future drift on ships.
}
