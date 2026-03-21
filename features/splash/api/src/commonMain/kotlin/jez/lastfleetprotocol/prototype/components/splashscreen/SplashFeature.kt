package jez.lastfleetprotocol.prototype.components.splashscreen

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

typealias SplashScreenEntry = @Composable (NavController) -> Unit
