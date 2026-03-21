package jez.lastfleetprotocol.prototype.components.splashscreen.ui

import androidx.lifecycle.viewModelScope
import jez.lastfleetprotocol.prototype.ui.common.LFViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

sealed interface SplashIntent {
    data object OnKubrikoInitialized : SplashIntent
}

sealed interface SplashState {
    data object Loading : SplashState
}

sealed interface SplashSideEffect {
    data object LoadingComplete : SplashSideEffect
}

@Inject
class SplashVM(
) : LFViewModel<SplashIntent, SplashState, SplashSideEffect>() {
    override val state: StateFlow<SplashState> =
        MutableStateFlow(SplashState.Loading)

    override fun accept(intent: SplashIntent) {
        viewModelScope.launch {
            when (intent) {
                SplashIntent.OnKubrikoInitialized -> sendSideEffect(SplashSideEffect.LoadingComplete)
            }
        }
    }
}
