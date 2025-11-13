package jez.lastfleetprotocol.prototype.ui.common

import kotlinx.coroutines.flow.StateFlow

interface StateProvider<T> {
    val state: StateFlow<T>
}