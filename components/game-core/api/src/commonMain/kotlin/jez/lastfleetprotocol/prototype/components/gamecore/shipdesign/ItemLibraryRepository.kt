package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

/**
 * Persistence contract for custom [ItemDefinition]s authored in the ship builder.
 *
 * The library is independent of any single [ShipDesign] — items live across sessions
 * and can be referenced by multiple designs. Pre-defined catalog items are not stored
 * here; only user-created items.
 */
interface ItemLibraryRepository {
    /** Save (or overwrite) an item definition. The item's [ItemDefinition.id] is the key. */
    fun save(item: ItemDefinition)

    /** Load a single item by its id. Returns null if missing or unreadable. */
    fun load(id: String): ItemDefinition?

    /** Load all items currently in the library. */
    fun loadAll(): List<ItemDefinition>

    /** Delete an item by id. No-op if it does not exist. */
    fun delete(id: String)
}
