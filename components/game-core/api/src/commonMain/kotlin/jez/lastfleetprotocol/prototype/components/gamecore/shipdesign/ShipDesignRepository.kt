package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

interface ShipDesignRepository {
    fun save(design: ShipDesign)
    fun load(name: String): ShipDesign?
    fun listAll(): List<String>
    fun delete(name: String)
}
