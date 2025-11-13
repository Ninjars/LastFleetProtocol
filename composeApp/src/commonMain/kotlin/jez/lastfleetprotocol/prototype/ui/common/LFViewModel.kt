package jez.lastfleetprotocol.prototype.ui.common

import androidx.lifecycle.ViewModel
import java.util.function.Consumer


abstract class LFViewModel<Event, ViewState, SideEffect>
    : ViewModel(),
    StateProvider<ViewState>,
    Consumer<Event>,
    HasSideEffect<SideEffect> by DefaultSideEffect()
