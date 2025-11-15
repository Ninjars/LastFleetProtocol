package jez.lastfleetprotocol.prototype.components.splashscreen.ui

import androidx.lifecycle.viewModelScope
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface SplashEvent {
    data object OnKubrikoInitialized : SplashEvent
}

sealed interface SplashState {
    data object Loading : SplashState
}

sealed interface SplashSideEffect {
    data object LoadingComplete : SplashSideEffect
}

@Inject
class SplashVM(
) : LFViewModel<SplashEvent, SplashState, SplashSideEffect>() {
    override val state: StateFlow<SplashState> =
        MutableStateFlow(SplashState.Loading)

    override fun accept(event: SplashEvent) {
        viewModelScope.launch {
            when (event) {
                SplashEvent.OnKubrikoInitialized -> sendSideEffect(SplashSideEffect.LoadingComplete)
            }
        }
    }
}
