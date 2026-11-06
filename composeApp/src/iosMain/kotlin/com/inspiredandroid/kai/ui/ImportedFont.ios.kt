package com.inspiredandroid.kai.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable actual fun ImportedFontPicker(onImported: (String) -> Unit, onError: (String) -> Unit) {
    Text("Font import is available on Android")
}
@Composable actual fun importedFontFamily(): FontFamily? = null
@Composable actual fun resetImportedFont(onReset: () -> Unit) {}
