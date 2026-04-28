package jez.lastfleetprotocol.prototype.components.scenariobuilder

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

typealias ScenarioBuilderScreenEntry = @Composable (NavController) -> Unit
