package com.salahmute.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.salahmute.app.R
import com.salahmute.app.model.ThemeMode

const val ACCENT_DYNAMIC = "DYNAMIC"
const val ACCENT_MONO = "MONO"
val DEFAULT_ACCENT = Color(0xFF5865F2) // Discord Blurple / Modern Indigo

val ACCENT_PRESETS = listOf(
    "#5865F2", // Blurple (default)
    "#2563EB", // Royal Blue
    "#0D9488", // Teal
    "#10B981", // Emerald
    "#F59E0B", // Amber
    "#F43F5E", // Rose
    "#9333EA", // Purple
    ACCENT_MONO,
)

fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.BLACK -> R.string.theme_black
    ThemeMode.NAVY -> R.string.theme_navy
    ThemeMode.FROST -> R.string.theme_frost
    ThemeMode.SEPIA -> R.string.theme_sepia
    ThemeMode.FOREST -> R.string.theme_forest
}

/** Parses `#RRGGBB` or `RRGGBB` into an ARGB int, or null if invalid. */
fun parseHexColor(hex: String): Int? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    val rgb = clean.toLongOrNull(16) ?: return null
    return (0xFF000000L or rgb).toInt()
}

private data class SurfacePalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardLow: Color,
    val container: Color,
    val containerHigh: Color,
    val variant: Color,
    val onText: Color,
    val onSub: Color,
    val outline: Color,
    val outlineVar: Color,
    val defaultAccent: Color,
)

private val WHITE_PALETTE = SurfacePalette(
    dark = false,
    bg = Color(0xFFF2F3F5), card = Color(0xFFFFFFFF), cardLow = Color(0xFFFAFAFB),
    container = Color(0xFFEBEDEF), containerHigh = Color(0xFFE3E5E8), variant = Color(0xFFE3E5E8),
    onText = Color(0xFF060607), onSub = Color(0xFF4E5058),
    outline = Color(0xFF80848E), outlineVar = Color(0xFFD4D7DC),
    defaultAccent = Color(0xFF5865F2),
)

private val DISCORD_DARK_PALETTE = SurfacePalette(
    dark = true,
    bg = Color(0xFF1E1F22), card = Color(0xFF2B2D31), cardLow = Color(0xFF26282C),
    container = Color(0xFF232428), containerHigh = Color(0xFF313338), variant = Color(0xFF313338),
    onText = Color(0xFFF2F3F5), onSub = Color(0xFFB5BAC1),
    outline = Color(0xFF6D6F78), outlineVar = Color(0xFF3F4147),
    defaultAccent = Color(0xFF5865F2),
)

private val AMOLED_BLACK_PALETTE = SurfacePalette(
    dark = true,
    bg = Color(0xFF000000), card = Color(0xFF111214), cardLow = Color(0xFF141518),
    container = Color(0xFF18191D), containerHigh = Color(0xFF1F2026), variant = Color(0xFF1A1B1E),
    onText = Color(0xFFF2F3F5), onSub = Color(0xFF949BA4),
    outline = Color(0xFF6D6F78), outlineVar = Color(0xFF26282E),
    defaultAccent = Color(0xFF5865F2),
)

private val MIDNIGHT_NAVY_PALETTE = SurfacePalette(
    dark = true,
    bg = Color(0xFF0B1120), card = Color(0xFF151F33), cardLow = Color(0xFF111A2C),
    container = Color(0xFF19253D), containerHigh = Color(0xFF22314F), variant = Color(0xFF1E2B45),
    onText = Color(0xFFF1F5F9), onSub = Color(0xFF94A3B8),
    outline = Color(0xFF64748B), outlineVar = Color(0xFF2B3B59),
    defaultAccent = Color(0xFF38BDF8),
)

private val NORDIC_FROST_PALETTE = SurfacePalette(
    dark = false,
    bg = Color(0xFFEDF2F8), card = Color(0xFFFFFFFF), cardLow = Color(0xFFF7FAFD),
    container = Color(0xFFE2EAF4), containerHigh = Color(0xFFD5E1F0), variant = Color(0xFFDCE6F2),
    onText = Color(0xFF0F172A), onSub = Color(0xFF475569),
    outline = Color(0xFF7B8FA8), outlineVar = Color(0xFFC5D4E6),
    defaultAccent = Color(0xFF2563EB),
)

private val WARM_SEPIA_PALETTE = SurfacePalette(
    dark = false,
    bg = Color(0xFFF6F1E7), card = Color(0xFFFFFDF8), cardLow = Color(0xFFFAF6EE),
    container = Color(0xFFECE4D5), containerHigh = Color(0xFFE2D7C5), variant = Color(0xFFE7DEC9),
    onText = Color(0xFF231C14), onSub = Color(0xFF635544),
    outline = Color(0xFF968572), outlineVar = Color(0xFFD9CEBC),
    defaultAccent = Color(0xFFB45309),
)

private val FOREST_NIGHT_PALETTE = SurfacePalette(
    dark = true,
    bg = Color(0xFF0C1411), card = Color(0xFF14211C), cardLow = Color(0xFF111C18),
    container = Color(0xFF182822), containerHigh = Color(0xFF20342C), variant = Color(0xFF1D3029),
    onText = Color(0xFFECF3EF), onSub = Color(0xFF9BB4A8),
    outline = Color(0xFF658074), outlineVar = Color(0xFF2A4238),
    defaultAccent = Color(0xFF34D399),
)

private fun paletteFor(mode: ThemeMode, systemDark: Boolean): SurfacePalette = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) DISCORD_DARK_PALETTE else WHITE_PALETTE
    ThemeMode.DARK -> DISCORD_DARK_PALETTE
    ThemeMode.LIGHT -> WHITE_PALETTE
    ThemeMode.BLACK -> AMOLED_BLACK_PALETTE
    ThemeMode.NAVY -> MIDNIGHT_NAVY_PALETTE
    ThemeMode.FROST -> NORDIC_FROST_PALETTE
    ThemeMode.SEPIA -> WARM_SEPIA_PALETTE
    ThemeMode.FOREST -> FOREST_NIGHT_PALETTE
}

private fun accentColorFor(accentHex: String, p: SurfacePalette): Color {
    if (accentHex == ACCENT_MONO) {
        return if (p.dark) Color(0xFFF2F3F5) else Color(0xFF18181B)
    }
    val base = if (accentHex.isBlank()) p.defaultAccent else (parseHexColor(accentHex)?.let { Color(it) } ?: p.defaultAccent)
    return if (p.dark && base.luminance() < 0.14f) lerp(base, Color.White, 0.32f) else base
}

private fun onColorFor(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF0E1013) else Color(0xFFFFFFFF)

private fun buildScheme(p: SurfacePalette, accent: Color): ColorScheme {
    val container = if (p.dark) lerp(accent, p.card, 0.68f) else lerp(accent, Color.White, 0.85f)
    val onContainer = if (p.dark) lerp(accent, Color.White, 0.84f) else lerp(accent, Color.Black, 0.48f)
    return if (p.dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onColorFor(accent),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = Color(0xFF5EEAD4),
            onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF115E59),
            onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = Color(0xFFFBBF24),
            onTertiary = Color(0xFF451A03),
            tertiaryContainer = Color(0xFF5C2D0C),
            onTertiaryContainer = Color(0xFFFEF3C7),
            background = p.bg,
            onBackground = p.onText,
            surface = p.bg,
            onSurface = p.onText,
            surfaceVariant = p.variant,
            onSurfaceVariant = p.onSub,
            surfaceContainerLowest = p.card,
            surfaceContainerLow = p.cardLow,
            surfaceContainer = p.container,
            surfaceContainerHigh = p.containerHigh,
            outline = p.outline,
            outlineVariant = p.outlineVar,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onColorFor(accent),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = Color(0xFF0F766E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCFBF1),
            onSecondaryContainer = Color(0xFF042F2E),
            tertiary = Color(0xFFD97706),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFEF3C7),
            onTertiaryContainer = Color(0xFF451A03),
            background = p.bg,
            onBackground = p.onText,
            surface = p.bg,
            onSurface = p.onText,
            surfaceVariant = p.variant,
            onSurfaceVariant = p.onSub,
            surfaceContainerLowest = p.card,
            surfaceContainerLow = p.cardLow,
            surfaceContainer = p.container,
            surfaceContainerHigh = p.containerHigh,
            outline = p.outline,
            outlineVariant = p.outlineVar,
        )
    }
}

@Composable
fun isDarkThemeActive(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT, ThemeMode.FROST, ThemeMode.SEPIA -> false
    ThemeMode.DARK, ThemeMode.BLACK, ThemeMode.NAVY, ThemeMode.FOREST -> true
}

@Composable
fun SalahTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentHex: String = "",
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val palette = paletteFor(themeMode, sysDark)
    val scheme = if (accentHex == ACCENT_DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        val dyn = if (palette.dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        if (themeMode == ThemeMode.BLACK) {
            dyn.copy(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                surfaceContainerLowest = Color(0xFF111214),
                surfaceContainer = Color(0xFF18191D),
            )
        } else {
            dyn
        }
    } else {
        buildScheme(palette, accentColorFor(accentHex, palette))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
