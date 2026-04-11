package jez.lastfleetprotocol.prototype.components.shipbuilder.data

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesignRepository
import jez.lastfleetprotocol.prototype.utils.deleteFile
import jez.lastfleetprotocol.prototype.utils.listFiles
import jez.lastfleetprotocol.prototype.utils.loadFile
import jez.lastfleetprotocol.prototype.utils.saveFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject

private const val DESIGNS_DIRECTORY = "designs"

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/**
 * File-based implementation of [ShipDesignRepository].
 * Saves designs as JSON files in the "designs" directory via platform [FileStorage].
 */
@Inject
class FileShipDesignRepository : ShipDesignRepository {

    override fun save(design: ShipDesign) {
        val fileName = sanitizeName(design.name)
        val content = json.encodeToString(design)
        saveFile(DESIGNS_DIRECTORY, "$fileName.json", content)
    }

    override fun load(name: String): ShipDesign? {
        val fileName = sanitizeName(name)
        val content = loadFile(DESIGNS_DIRECTORY, "$fileName.json") ?: return null
        return try {
            json.decodeFromString<ShipDesign>(content)
        } catch (e: Exception) {
            null
        }
    }

    override fun listAll(): List<String> {
        return listFiles(DESIGNS_DIRECTORY)
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
    }

    override fun delete(name: String) {
        val fileName = sanitizeName(name)
        deleteFile(DESIGNS_DIRECTORY, "$fileName.json")
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
}
