package jez.lastfleetprotocol.prototype.components.scenariobuilder.data

import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.Scenario
import jez.lastfleetprotocol.prototype.components.gamecore.scenarios.ScenarioRepository
import jez.lastfleetprotocol.prototype.utils.deleteFile
import jez.lastfleetprotocol.prototype.utils.listFiles
import jez.lastfleetprotocol.prototype.utils.loadFile
import jez.lastfleetprotocol.prototype.utils.sanitizeFilenameStem
import jez.lastfleetprotocol.prototype.utils.saveFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject

private const val SCENARIOS_DIRECTORY = "scenarios"

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/**
 * File-based implementation of [ScenarioRepository].
 * Saves scenarios as JSON files in the "scenarios" directory via platform [FileStorage].
 *
 * Sibling to `FileShipDesignRepository` — same `FileStorage` substrate, same
 * shared [sanitizeFilenameStem] helper, same null-on-decode-error pattern.
 *
 * File-IO orchestration is exercised manually (and indirectly via the
 * scenario-builder UI's save/load flow) rather than in unit tests, matching
 * the precedent set by `FileShipDesignRepository`. The pure pieces — JSON
 * round-tripping and filename sanitisation — are covered in
 * `:components:game-core:api:ScenarioSerializationTest` and
 * `:components:shared:api:SanitisationTest` respectively.
 */
@Inject
class FileScenarioRepository : ScenarioRepository {

    override fun save(scenario: Scenario) {
        val fileName = sanitizeFilenameStem(scenario.name)
        val content = json.encodeToString(scenario)
        saveFile(SCENARIOS_DIRECTORY, "$fileName.json", content)
    }

    override fun load(name: String): Scenario? {
        val fileName = sanitizeFilenameStem(name)
        val content = loadFile(SCENARIOS_DIRECTORY, "$fileName.json") ?: return null
        return try {
            json.decodeFromString<Scenario>(content)
        } catch (e: Exception) {
            null
        }
    }

    override fun listAll(): List<String> {
        return listFiles(SCENARIOS_DIRECTORY)
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
    }

    override fun delete(name: String) {
        val fileName = sanitizeFilenameStem(name)
        deleteFile(SCENARIOS_DIRECTORY, "$fileName.json")
    }
}
