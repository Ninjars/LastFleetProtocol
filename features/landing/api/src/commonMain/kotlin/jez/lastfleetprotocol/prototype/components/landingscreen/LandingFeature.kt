package jez.lastfleetprotocol.prototype.components.landingscreen

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

typealias LandingScreenEntry = @Composable (NavController) -> Unit
