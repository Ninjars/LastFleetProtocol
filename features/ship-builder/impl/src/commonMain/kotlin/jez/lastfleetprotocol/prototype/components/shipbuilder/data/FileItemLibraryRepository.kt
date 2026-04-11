package jez.lastfleetprotocol.prototype.components.shipbuilder.data

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemLibraryRepository
import jez.lastfleetprotocol.prototype.utils.deleteFile
import jez.lastfleetprotocol.prototype.utils.listFiles
import jez.lastfleetprotocol.prototype.utils.loadFile
import jez.lastfleetprotocol.prototype.utils.saveFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject

private const val ITEMS_DIRECTORY = "items"

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/**
 * File-based implementation of [ItemLibraryRepository].
 * Saves each [ItemDefinition] as a JSON file in the "items" directory via platform [FileStorage].
 *
 * Files are named by item id (sanitized). Item names are user-editable and stored inside the
 * file rather than reflected in the filename, to keep id-based lookup stable across renames.
 */
@Inject
class FileItemLibraryRepository : ItemLibraryRepository {

    override fun save(item: ItemDefinition) {
        val fileName = "${sanitizeId(item.id)}.json"
        val content = json.encodeToString(item)
        saveFile(ITEMS_DIRECTORY, fileName, content)
    }

    override fun load(id: String): ItemDefinition? {
        val fileName = "${sanitizeId(id)}.json"
        val content = loadFile(ITEMS_DIRECTORY, fileName) ?: return null
        return try {
            json.decodeFromString<ItemDefinition>(content)
        } catch (e: Exception) {
            println("Failed to deserialise item $id: $e")
            null
        }
    }

    override fun loadAll(): List<ItemDefinition> {
        return listFiles(ITEMS_DIRECTORY)
            .filter { it.endsWith(".json") }
            .mapNotNull { fileName ->
                val content = loadFile(ITEMS_DIRECTORY, fileName) ?: return@mapNotNull null
                try {
                    json.decodeFromString<ItemDefinition>(content)
                } catch (e: Exception) {
                    println("Failed to deserialise item file $fileName: $e")
                    null
                }
            }
    }

    override fun delete(id: String) {
        deleteFile(ITEMS_DIRECTORY, "${sanitizeId(id)}.json")
    }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
}
