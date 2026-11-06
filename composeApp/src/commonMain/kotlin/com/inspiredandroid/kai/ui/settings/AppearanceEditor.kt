package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.ui.ImportedFontPicker
import com.inspiredandroid.kai.ui.resetImportedFont
import com.inspiredandroid.kai.ui.ThemeEditorConfig
import org.koin.compose.koinInject

@Composable
fun AppearanceEditor(settings: AppSettings = koinInject()) {
    var fontStatus by remember { mutableStateOf("") }
    val stored by settings.themeJsonFlow.collectAsStateWithLifecycle()
    val thinking by settings.deepSeekThinkingFlow.collectAsStateWithLifecycle()
    var draft by remember(stored) { mutableStateOf(stored.ifBlank { ThemeEditorConfig.DEFAULT }) }
    val error = remember(draft) { runCatching { ThemeEditorConfig.parse(draft) }.exceptionOrNull()?.message }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theme file", style = MaterialTheme.typography.titleLarge)
        Text("Edit JSON to customize typography and colors. Changes apply when you press Apply. Unknown keys are preserved but do not affect the UI until supported.")
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Theme JSON") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 12,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        )
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { settings.setThemeJson(draft) }, enabled = error == null) { Text("Apply") }
            OutlinedButton(onClick = { draft = ThemeEditorConfig.DEFAULT; settings.setThemeJson("") }) { Text("Reset") }
        }
        Text("Custom Android font", style = MaterialTheme.typography.titleLarge)
        Text("Import a TTF or OTF file from your phone. The selected font is copied into private app storage and applied throughout the app.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportedFontPicker(
                onImported = { fontStatus = it; settings.notifyImportedFontChanged() },
                onError = { fontStatus = it },
            )
            resetImportedFont { fontStatus = "Font removed"; settings.notifyImportedFontChanged() }
        }
        if (fontStatus.isNotBlank()) Text(fontStatus)
        Text("DeepSeek", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Thinking mode (new requests)", modifier = Modifier.weight(1f))
            Switch(checked = thinking, onCheckedChange = settings::setDeepSeekThinkingEnabled)
        }
        Text("When enabled, DeepSeek uses deepseek-reasoner; when disabled, deepseek-chat. Applies to DeepSeek requests only.")
    }
}
