package jez.lastfleetprotocol.prototype.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun <T> HasSideEffect<T>.HandleSideEffect(handler: (T) -> Unit) {
    LaunchedEffect(sideEffect) {
        sideEffect.collect { handler(it) }
    }
}