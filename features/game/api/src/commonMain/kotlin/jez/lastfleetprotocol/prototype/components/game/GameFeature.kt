package jez.lastfleetprotocol.prototype.components.game

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

typealias GameScreenEntry = @Composable (NavController) -> Unit
