package com.salahmute.app.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.ThemeMode
import java.util.Locale
import kotlin.math.roundToInt

/** Reusable modern Theme Mode (live mini preview + 8 visual theme cards) + Accent swatches + Custom Color dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePickerSection(
    themeMode: ThemeMode,
    accentHex: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (String) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    val dark = isDarkThemeActive(themeMode)
    val currentAccent = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live mini SalahMute preview so the user sees the real app look instantly!
        LiveThemePreviewCard()

        Text(
            stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 2-column visual theme cards: Default (System) first, then Dark, White, Black + 4 curated modern themes
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ThemePreviewCard(
                    title = stringResource(R.string.theme_system),
                    subtitle = stringResource(R.string.theme_system_sub),
                    selected = themeMode == ThemeMode.SYSTEM,
                    bg = Color(0xFF1E1F22),
                    card = Color(0xFFF2F3F5),
                    line = Color(0xFF4E5058),
                    accent = currentAccent,
                    splitHalf = true,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    title = stringResource(R.string.theme_dark),
                    subtitle = stringResource(R.string.theme_dark_sub),
                    selected = themeMode == ThemeMode.DARK,
                    bg = Color(0xFF1E1F22),
                    card = Color(0xFF2B2D31),
                    line = Color(0xFFF2F3F5),
                    accent = Color(0xFF5865F2),
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ThemePreviewCard(
                    title = stringResource(R.string.theme_light),
                    subtitle = stringResource(R.string.theme_light_sub),
                    selected = themeMode == ThemeMode.LIGHT,
                    bg = Color(0xFFF2F3F5),
                    card = Color(0xFFFFFFFF),
                    line = Color(0xFF1E1F22),
                    accent = Color(0xFF5865F2),
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    title = stringResource(R.string.theme_black),
                    subtitle = stringResource(R.string.theme_black_sub),
                    selected = themeMode == ThemeMode.BLACK,
                    bg = Color(0xFF000000),
                    card = Color(0xFF111214),
                    line = Color(0xFFF2F3F5),
                    accent = Color(0xFF5865F2),
                    onClick = { onThemeModeChange(ThemeMode.BLACK) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ThemePreviewCard(
                    title = stringResource(R.string.theme_navy),
                    subtitle = stringResource(R.string.theme_navy_sub),
                    selected = themeMode == ThemeMode.NAVY,
                    bg = Color(0xFF0B1120),
                    card = Color(0xFF151F33),
                    line = Color(0xFFF1F5F9),
                    accent = Color(0xFF38BDF8),
                    onClick = { onThemeModeChange(ThemeMode.NAVY) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    title = stringResource(R.string.theme_frost),
                    subtitle = stringResource(R.string.theme_frost_sub),
                    selected = themeMode == ThemeMode.FROST,
                    bg = Color(0xFFEDF2F8),
                    card = Color(0xFFFFFFFF),
                    line = Color(0xFF0F172A),
                    accent = Color(0xFF2563EB),
                    onClick = { onThemeModeChange(ThemeMode.FROST) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ThemePreviewCard(
                    title = stringResource(R.string.theme_sepia),
                    subtitle = stringResource(R.string.theme_sepia_sub),
                    selected = themeMode == ThemeMode.SEPIA,
                    bg = Color(0xFFF6F1E7),
                    card = Color(0xFFFFFDF8),
                    line = Color(0xFF231C14),
                    accent = Color(0xFFB45309),
                    onClick = { onThemeModeChange(ThemeMode.SEPIA) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    title = stringResource(R.string.theme_forest),
                    subtitle = stringResource(R.string.theme_forest_sub),
                    selected = themeMode == ThemeMode.FOREST,
                    bg = Color(0xFF0C1411),
                    card = Color(0xFF14211C),
                    line = Color(0xFFECF3EF),
                    accent = Color(0xFF34D399),
                    onClick = { onThemeModeChange(ThemeMode.FOREST) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            stringResource(R.string.accent_color),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Tactile circular color swatches
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ACCENT_PRESETS.forEach { preset ->
                val isSelected = when {
                    preset == "#5865F2" && accentHex.isEmpty() -> true
                    else -> accentHex.equals(preset, ignoreCase = true)
                }
                val swatchColor = if (preset == ACCENT_MONO) {
                    if (dark) Color(0xFFF2F3F5) else Color(0xFF18181B)
                } else {
                    Color(parseHexColor(preset) ?: DEFAULT_ACCENT.toArgb())
                }
                val checkTint = if (swatchColor.luminance() > 0.45f) Color(0xFF0E1013) else Color.White

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            },
                        )
                        .clickable { onAccentChange(if (preset == "#5865F2") "" else preset) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = checkTint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        val isCustomSelected = accentHex.isNotEmpty() &&
            accentHex != ACCENT_DYNAMIC &&
            ACCENT_PRESETS.none { it.equals(accentHex, ignoreCase = true) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (Build.VERSION.SDK_INT >= 31) {
                FilterChip(
                    selected = accentHex == ACCENT_DYNAMIC,
                    onClick = { onAccentChange(ACCENT_DYNAMIC) },
                    label = { Text(stringResource(R.string.accent_dynamic)) },
                )
            }
            FilterChip(
                selected = isCustomSelected,
                onClick = { showCustom = true },
                label = {
                    Text(
                        if (isCustomSelected) "${stringResource(R.string.accent_custom)} ($accentHex)"
                        else stringResource(R.string.accent_custom),
                    )
                },
            )
        }
    }

    if (showCustom) {
        val initialArgb = parseHexColor(accentHex) ?: currentAccent.toArgb()
        CustomColorDialog(
            initialArgb = initialArgb,
            onDismiss = { showCustom = false },
            onConfirm = { hex ->
                onAccentChange(hex)
                showCustom = false
            },
        )
    }
}

@Composable
private fun LiveThemePreviewCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.theme_preview_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        stringResource(R.string.badge_in_min, 15),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("1:00 PM – 1:30 PM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.prayer_dhuhr), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            ModePill(RingMode.SILENT)
                        }
                    }
                    Switch(checked = true, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    bg: Color,
    card: Color,
    line: Color,
    accent: Color,
    splitHalf: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg),
            ) {
                if (splitHalf) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFF2F3F5)))
                        Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF1E1F22)))
                    }
                    Row(
                        Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
                        Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF949BA4)))
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(7.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(card)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(11.dp).clip(CircleShape).background(accent))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(line.copy(alpha = 0.85f)))
                                Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(line.copy(alpha = 0.35f)))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var r by remember { mutableIntStateOf(android.graphics.Color.red(initialArgb)) }
    var g by remember { mutableIntStateOf(android.graphics.Color.green(initialArgb)) }
    var b by remember { mutableIntStateOf(android.graphics.Color.blue(initialArgb)) }
    var hexText by remember { mutableStateOf("#%02X%02X%02X".format(Locale.US, r, g, b)) }

    val preview = Color(r, g, b)

    fun syncHex(nr: Int, ng: Int, nb: Int) {
        r = nr; g = ng; b = nb
        hexText = "#%02X%02X%02X".format(Locale.US, nr, ng, nb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(preview),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        hexText.uppercase(Locale.US),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (preview.luminance() > 0.45f) Color(0xFF0E1013) else Color.White,
                    )
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        hexText = raw.take(7)
                        parseHexColor(raw)?.let { argb ->
                            r = android.graphics.Color.red(argb)
                            g = android.graphics.Color.green(argb)
                            b = android.graphics.Color.blue(argb)
                        }
                    },
                    label = { Text(stringResource(R.string.hex_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Slider(
                    value = r.toFloat(),
                    onValueChange = { syncHex(it.roundToInt(), g, b) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFEF4444), activeTrackColor = Color(0xFFEF4444)),
                )
                Slider(
                    value = g.toFloat(),
                    onValueChange = { syncHex(r, it.roundToInt(), b) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF22C55E), activeTrackColor = Color(0xFF22C55E)),
                )
                Slider(
                    value = b.toFloat(),
                    onValueChange = { syncHex(r, g, it.roundToInt()) },
                    valueRange = 0f..255f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6)),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm("#%02X%02X%02X".format(Locale.US, r, g, b)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
