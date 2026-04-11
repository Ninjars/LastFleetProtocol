package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

sealed interface ShipBuilderSideEffect {
    data object NavigateBack : ShipBuilderSideEffect
}
