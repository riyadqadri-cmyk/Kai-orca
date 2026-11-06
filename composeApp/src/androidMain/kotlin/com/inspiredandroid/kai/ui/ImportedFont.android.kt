package com.inspiredandroid.kai.ui

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

private const val FONT_FILE = "orca_imported_font.ttf"
private fun fontFile(context: android.content.Context) = File(context.filesDir, FONT_FILE)

@Composable
actual fun ImportedFontPicker(onImported: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: error("Cannot read selected font")
                require(bytes.size in 1..(12 * 1024 * 1024)) { "Font must be smaller than 12 MB" }
                val temporary = File(context.cacheDir, "orca_font_candidate")
                temporary.writeBytes(bytes)
                try {
                    Typeface.createFromFile(temporary) // Validate before replacing saved font
                    temporary.copyTo(fontFile(context), overwrite = true)
                } finally {
                    temporary.delete()
                }
                onImported("Font imported successfully")
            }.onFailure { onError(it.message ?: "Invalid or unsupported font") }
        }
    }
    Button(onClick = { picker.launch("*/*") }) { Text("Import .ttf / .otf") }
}

@Composable
actual fun importedFontFamily(): FontFamily? {
    val context = LocalContext.current
    return remember(context) {
        fontFile(context).takeIf { it.isFile }?.let { file ->
            runCatching { FontFamily(Font(Typeface.createFromFile(file))) }.getOrNull()
        }
    }
}

@Composable
actual fun resetImportedFont(onReset: () -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { fontFile(context).delete(); onReset() }) { Text("Remove imported font") }
}
