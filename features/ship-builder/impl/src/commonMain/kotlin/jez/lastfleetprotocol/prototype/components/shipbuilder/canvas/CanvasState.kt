package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset

data class CanvasState(
    val offset: Offset = Offset.Zero,
    val zoom: Float = 1f,
) {
    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 10f
        const val DRAG_THRESHOLD = 8f
        const val SCROLL_ZOOM_FACTOR = 0.05f
    }

    /**
     * Convert a screen-space point to world-space, accounting for pan offset and zoom.
     */
    fun screenToWorld(screenPoint: Offset): Offset =
        (screenPoint - offset) / zoom

    /**
     * Convert a world-space point to screen-space, accounting for pan offset and zoom.
     */
    fun worldToScreen(worldPoint: Offset): Offset =
        worldPoint * zoom + offset
}
