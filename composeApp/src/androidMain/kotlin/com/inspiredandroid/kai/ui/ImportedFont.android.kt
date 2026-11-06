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
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import java.io.File

// A font stored in app-private storage cannot use Compose's resource-ID Font constructor.
// AndroidFont bridges a file-backed Android Typeface to Compose text rendering.
private class ImportedFileFont(val file: File) : AndroidFont(
    FontLoadingStrategy.Blocking,
    FileTypefaceLoader,
    FontVariation.Settings(),
) {
    override val weight: FontWeight = FontWeight.Normal
    override val style: FontStyle = FontStyle.Normal
}

private object FileTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: android.content.Context, font: AndroidFont): Typeface? =
        (font as? ImportedFileFont)?.let { Typeface.createFromFile(it.file) }

    override suspend fun awaitLoad(context: android.content.Context, font: AndroidFont): Typeface? =
        loadBlocking(context, font)
}


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
            runCatching { FontFamily(ImportedFileFont(file)) }.getOrNull()
        }
    }
}

@Composable
actual fun resetImportedFont(onReset: () -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { fontFile(context).delete(); onReset() }) { Text("Remove imported font") }
}
