package com.inspiredandroid.kai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Android copies selected fonts to private storage. Other targets use a safe no-op. */
@Composable
expect fun ImportedFontPicker(onImported: (String) -> Unit, onError: (String) -> Unit)

@Composable
expect fun importedFontFamily(): FontFamily?

@Composable
expect fun resetImportedFont(onReset: () -> Unit)
