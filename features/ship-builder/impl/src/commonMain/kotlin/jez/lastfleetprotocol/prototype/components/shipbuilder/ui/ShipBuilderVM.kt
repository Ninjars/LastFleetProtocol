package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject

sealed interface ShipBuilderIntent {
    data object Noop : ShipBuilderIntent
}

data class ShipBuilderState(
    val designName: String = "New Ship",
)

sealed interface ShipBuilderSideEffect {
    data object NavigateBack : ShipBuilderSideEffect
}

@Inject
class ShipBuilderVM : ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>() {

    override val state: StateFlow<ShipBuilderState> = MutableStateFlow(ShipBuilderState())

    override fun accept(intent: ShipBuilderIntent) {
        when (intent) {
            is ShipBuilderIntent.Noop -> Unit
        }
    }
}
