package jez.lastfleetprotocol.prototype.ui.common.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import jez.lastfleetprotocol.prototype.ui.common.PreviewWrapper
import lastfleetprotocol.composeapp.generated.resources.Res
import lastfleetprotocol.composeapp.generated.resources.button_continue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LFTextButton(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    LFTextButton(
        text = stringResource(textRes),
        modifier = modifier,
        fillWidth = fillWidth,
        onClick = onClick,
    )
}

@Composable
fun LFTextButton(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(
            if (fillWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
            }
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun LFTextButtonPreview() {
    PreviewWrapper {
        LFTextButton(
            text = stringResource(Res.string.button_continue),
            modifier = Modifier.fillMaxWidth(),
            onClick = {}
        )
    }
}