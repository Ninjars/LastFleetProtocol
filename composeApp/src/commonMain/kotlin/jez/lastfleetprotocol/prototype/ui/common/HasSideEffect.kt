package jez.lastfleetprotocol.prototype.ui.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

interface HasSideEffect<T> {
    val sideEffect: Flow<T>

    suspend fun sendSideEffect(sideEffect: T)
}

class DefaultSideEffect<T> : HasSideEffect<T> {
    private val _sideEffect = MutableSharedFlow<T>()
    override val sideEffect: Flow<T> = _sideEffect
    override suspend fun sendSideEffect(sideEffect: T) {
        _sideEffect.emit(sideEffect)
    }
}