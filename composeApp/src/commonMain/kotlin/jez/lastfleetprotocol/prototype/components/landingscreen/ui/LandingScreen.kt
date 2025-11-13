package jez.lastfleetprotocol.prototype.components.landingscreen.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import me.tatarka.inject.annotations.Inject


typealias LandingScreen = @Composable () -> Unit

@Inject
@Composable
fun LandingScreen(viewModelFactory: () -> LandingVM) {
    val viewModel = viewModel { viewModelFactory() }
}