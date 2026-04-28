package jez.lastfleetprotocol.prototype.components.scenariobuilder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.composables.LoadScenarioDialog
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.composables.ScenarioMiniMap
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.composables.SlotRow
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderIntent
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioBuilderState
import jez.lastfleetprotocol.prototype.components.scenariobuilder.ui.entities.ScenarioTeam
import jez.lastfleetprotocol.prototype.ui.common.HandleSideEffect
import jez.lastfleetprotocol.prototype.ui.common.composables.LFIconButton
import jez.lastfleetprotocol.prototype.ui.common.composables.LFTextButton
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavDestination
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

typealias ScenarioBuilderScreen = @Composable (NavController) -> Unit

@Inject
@Composable
fun ScenarioBuilderScreen(
    viewModelFactory: () -> ScenarioBuilderVM,
    @Assisted navController: NavController,
) {
    val viewModel = viewModel { viewModelFactory() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            ScenarioBuilderSideEffect.NavigateBack -> navController.popBackStack()
            ScenarioBuilderSideEffect.LaunchScenario ->
                navController.navigate(LFNavDestination.GAME)
            is ScenarioBuilderSideEffect.ShowToast -> coroutineScope.launch {
                snackbarHostState.showSnackbar(effect.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScenarioBuilderScreenContent(
            state = viewModel.state.collectAsStateWithLifecycle().value,
            onIntent = viewModel::accept,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ScenarioBuilderScreenContent(
    state: ScenarioBuilderState,
    onIntent: (ScenarioBuilderIntent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        TopBar(state, onIntent)
        Spacer(modifier = Modifier.height(8.dp))
        if (state.brokenSlotIds.isNotEmpty()) {
            BrokenSlotBanner(brokenCount = state.brokenSlotIds.size)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                modifier = Modifier.width(TEAM_PANEL_WIDTH).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TeamPanel(
                    title = stringResource(LFRes.String.scenario_player_team),
                    team = ScenarioTeam.PLAYER,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
                TeamPanel(
                    title = stringResource(LFRes.String.scenario_enemy_team),
                    team = ScenarioTeam.ENEMY,
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ScenarioMiniMap(
                state = state,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    if (state.showLoadDialog) {
        LoadScenarioDialog(
            scenarios = state.savedScenarios,
            onSelect = { onIntent(ScenarioBuilderIntent.LoadScenario(it)) },
            onDismiss = { onIntent(ScenarioBuilderIntent.LoadDialogDismissed) },
        )
    }
    if (state.showOverwriteConfirm != null) {
        OverwriteConfirmDialog(
            onConfirm = { onIntent(ScenarioBuilderIntent.ConfirmOverwrite) },
            onCancel = { onIntent(ScenarioBuilderIntent.CancelOverwrite) },
        )
    }
}

@Composable
private fun TopBar(
    state: ScenarioBuilderState,
    onIntent: (ScenarioBuilderIntent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LFIconButton(
            drawable = LFRes.Drawable.ic_back,
            contentDescription = null,
            onClick = { onIntent(ScenarioBuilderIntent.BackClicked) },
        )
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedTextField(
            value = state.designName,
            onValueChange = { onIntent(ScenarioBuilderIntent.RenameScenario(it)) },
            label = { Text(stringResource(LFRes.String.scenario_name_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        LFTextButton(
            text = stringResource(LFRes.String.scenario_save),
            onClick = { onIntent(ScenarioBuilderIntent.SaveClicked) },
            fillWidth = false,
        )
        Spacer(modifier = Modifier.width(4.dp))
        LFTextButton(
            text = stringResource(LFRes.String.scenario_load),
            onClick = { onIntent(ScenarioBuilderIntent.LoadDialogOpenClicked) },
            fillWidth = false,
            enabled = state.canShowLoadDialog,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.End) {
            LFTextButton(
                text = stringResource(LFRes.String.scenario_launch),
                onClick = { onIntent(ScenarioBuilderIntent.LaunchClicked) },
                fillWidth = false,
                enabled = state.canLaunch,
            )
            if (!state.canLaunch) {
                Text(
                    text = stringResource(LFRes.String.scenario_launch_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun BrokenSlotBanner(brokenCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                painter = painterResource(LFRes.Drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$brokenCount " + stringResource(LFRes.String.scenario_broken_slot_banner),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun TeamPanel(
    title: String,
    team: ScenarioTeam,
    state: ScenarioBuilderState,
    onIntent: (ScenarioBuilderIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
        val teamSlots = state.slots.filter { it.team == team }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(teamSlots, key = { it.id }) { slot ->
                SlotRow(
                    slot = slot,
                    libraryShipNames = state.libraryShipNames,
                    isBroken = slot.id in state.brokenSlotIds,
                    onShipChange = { onIntent(ScenarioBuilderIntent.UpdateSlotShip(slot.id, it)) },
                    onPositionChange = { x, y ->
                        onIntent(ScenarioBuilderIntent.UpdateSlotPosition(slot.id, x, y))
                    },
                    onToggleAi = { onIntent(ScenarioBuilderIntent.ToggleSlotAi(slot.id)) },
                    onRemove = { onIntent(ScenarioBuilderIntent.RemoveSlot(slot.id)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LFTextButton(
            text = stringResource(LFRes.String.scenario_add_slot),
            onClick = { onIntent(ScenarioBuilderIntent.AddSlot(team)) },
        )
        LFTextButton(
            text = stringResource(LFRes.String.scenario_use_demo_defaults),
            onClick = { onIntent(ScenarioBuilderIntent.UseDemoDefaults(team)) },
        )
    }
}

@Composable
private fun OverwriteConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(LFRes.String.scenario_overwrite_confirm_title)) },
        text = { Text(stringResource(LFRes.String.scenario_overwrite_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(LFRes.String.scenario_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(LFRes.String.button_cancel))
            }
        },
    )
}

private val TEAM_PANEL_WIDTH = 220.dp
