package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.diagnostics.AppLog
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_logs_empty
import org.jetbrains.compose.resources.stringResource

/** A single selectable log box, without filters or other settings. */
@Composable
internal fun LogsContent(modifier: Modifier = Modifier) {
    val text by AppLog.text.collectAsStateWithLifecycle()
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        SelectionContainer(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(
                text = text.ifBlank { stringResource(Res.string.settings_logs_empty) },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
            )
        }
    }
}
