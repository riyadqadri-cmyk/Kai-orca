package com.inspiredandroid.kai.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Editable theme overrides; unknown keys remain in the saved JSON for future versions. */
object ThemeEditorConfig {
    const val DEFAULT = """{
  "fontFamily": "Default",
  "fontScale": 1.0
}"""

    fun parse(source: String): JsonObject = Json.parseToJsonElement(source).let {
        it as? JsonObject ?: error("Theme must be a JSON object")
    }.also { obj ->
        obj["fontFamily"]?.jsonPrimitive?.content?.let {
            require(it in setOf("Default", "SansSerif", "Serif", "Monospace", "Cursive")) { "Unsupported fontFamily: $it" }
        }
        obj["fontScale"]?.jsonPrimitive?.content?.let { raw ->
            val scale = raw.toFloatOrNull() ?: error("fontScale must be a number")
            require(scale in 0.7f..2.0f) { "fontScale must be between 0.7 and 2.0" }
        }
        for (key in listOf("primary", "background", "surface", "onSurface", "onBackground", "onSurfaceVariant")) {
            obj[key]?.jsonPrimitive?.content?.let { parseColor(it) }
        }
    }

    private fun parseColor(value: String): Color {
        require(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(value)) { "Invalid color: $value" }
        val hex = value.drop(1)
        val argb = if (hex.length == 6) "FF$hex" else hex
        return Color(argb.toLong(16))
    }

    fun applyColors(base: ColorScheme, obj: JsonObject): ColorScheme = base.copy(
        primary = obj["primary"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.primary,
        background = obj["background"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.background,
        surface = obj["surface"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.surface,
        onSurface = obj["onSurface"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.onSurface,
        onBackground = obj["onBackground"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.onBackground,
        onSurfaceVariant = obj["onSurfaceVariant"]?.jsonPrimitive?.content?.let(::parseColor) ?: base.onSurfaceVariant,
    )

    fun applyTypography(base: Typography, obj: JsonObject, importedFont: FontFamily? = null): Typography {
        val font = when (obj["fontFamily"]?.jsonPrimitive?.content) {
            "SansSerif" -> FontFamily.SansSerif
            "Serif" -> FontFamily.Serif
            "Monospace" -> FontFamily.Monospace
            "Cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
        val scale = obj["fontScale"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f
        fun scaled(style: androidx.compose.ui.text.TextStyle) = style.copy(
            fontFamily = importedFont ?: font, fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale,
        )
        return Typography(
            displayLarge = scaled(base.displayLarge), displayMedium = scaled(base.displayMedium), displaySmall = scaled(base.displaySmall),
            headlineLarge = scaled(base.headlineLarge), headlineMedium = scaled(base.headlineMedium), headlineSmall = scaled(base.headlineSmall),
            titleLarge = scaled(base.titleLarge), titleMedium = scaled(base.titleMedium), titleSmall = scaled(base.titleSmall),
            bodyLarge = scaled(base.bodyLarge), bodyMedium = scaled(base.bodyMedium), bodySmall = scaled(base.bodySmall),
            labelLarge = scaled(base.labelLarge), labelMedium = scaled(base.labelMedium), labelSmall = scaled(base.labelSmall),
        )
    }
}
