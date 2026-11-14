package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.ui.ImportedFontPicker
import com.inspiredandroid.kai.ui.ThemeEditorConfig
import com.inspiredandroid.kai.ui.resetImportedFont
import org.koin.compose.koinInject

@Composable
fun AppearanceEditor(settings: AppSettings = koinInject()) {
    var fontStatus by remember { mutableStateOf("") }
    val stored by settings.themeJsonFlow.collectAsStateWithLifecycle()
    val thinking by settings.deepSeekThinkingFlow.collectAsStateWithLifecycle()
    var draft by remember(stored) { mutableStateOf(stored.ifBlank { ThemeEditorConfig.DEFAULT }) }
    val error = remember(draft) { runCatching { ThemeEditorConfig.parse(draft) }.exceptionOrNull()?.message }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Only the editor and its action live here. Every string that is shown carries an
        // explicit theme color so nothing inherits a hardcoded black on dark surfaces.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            minLines = 12,
        )
        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { settings.setThemeJson(draft) }, enabled = error == null) {
                Text(text = "Apply", color = MaterialTheme.colorScheme.onPrimary)
            }
            OutlinedButton(onClick = {
                draft = ThemeEditorConfig.DEFAULT
                settings.setThemeJson("")
            }) {
                Text(text = "Reset", color = MaterialTheme.colorScheme.onSurface)
            }
            ImportedFontPicker(
                onImported = {
                    fontStatus = it
                    settings.notifyImportedFontChanged()
                },
                onError = { fontStatus = it },
            )
        }
        if (fontStatus.isNotBlank()) {
            Text(text = fontStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            resetImportedFont {
                fontStatus = "Font removed"
                settings.notifyImportedFontChanged()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Thinking mode (DeepSeek / OpenRouter)",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(checked = thinking, onCheckedChange = settings::setDeepSeekThinkingEnabled)
        }
    }
}
