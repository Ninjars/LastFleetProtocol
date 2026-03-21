package jez.lastfleetprotocol.prototype.ui.common

import androidx.lifecycle.ViewModel
import java.util.function.Consumer


abstract class ViewModelContract<Intent, ViewState, SideEffect>
    : ViewModel(),
    StateProvider<ViewState>,
    Consumer<Intent>,
    HasSideEffect<SideEffect> by DefaultSideEffect()
