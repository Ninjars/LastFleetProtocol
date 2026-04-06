package jez.lastfleetprotocol.prototype.components.gamecore.data

import org.jetbrains.compose.resources.DrawableResource

object DrawableResourceRegistry {
    private val resources = mutableMapOf<String, DrawableResource>()

    fun register(id: String, resource: DrawableResource) {
        resources[id] = resource
    }

    fun get(id: String): DrawableResource? = resources[id]
}
