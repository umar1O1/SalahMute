package com.salahmute.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.StringRes
import com.salahmute.app.R
import com.salahmute.app.engine.Engine
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Settings
import com.salahmute.app.model.Slot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

@StringRes
fun RingMode.labelRes(): Int = when (this) {
    RingMode.VIBRATE -> R.string.mode_vibrate
    RingMode.SILENT -> R.string.mode_silent
    RingMode.DND -> R.string.mode_dnd
}

@StringRes
fun Prayer.labelRes(): Int = when (this) {
    Prayer.FAJR -> R.string.prayer_fajr
    Prayer.DHUHR -> R.string.prayer_dhuhr
    Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib
    Prayer.ISHA -> R.string.prayer_isha
}

@StringRes
fun CalcMethod.labelRes(): Int = when (this) {
    CalcMethod.MWL -> R.string.method_mwl
    CalcMethod.ISNA -> R.string.method_isna
    CalcMethod.EGYPT -> R.string.method_egypt
    CalcMethod.MAKKAH -> R.string.method_makkah
    CalcMethod.KARACHI -> R.string.method_karachi
    CalcMethod.TURKEY -> R.string.method_turkey
    CalcMethod.JAFARI -> R.string.method_jafari
}

@StringRes
fun Fiqh.labelRes(): Int = when (this) {
    Fiqh.HANAFI -> R.string.fiqh_hanafi
    Fiqh.SHAFI -> R.string.fiqh_shafi
    Fiqh.SHIA_JAFARI -> R.string.fiqh_shia
}

@StringRes
fun Fiqh.descRes(): Int = when (this) {
    Fiqh.HANAFI -> R.string.fiqh_hanafi_desc
    Fiqh.SHAFI -> R.string.fiqh_shafi_desc
    Fiqh.SHIA_JAFARI -> R.string.fiqh_shia_desc
}

data class LanguageOption(val tag: String, val nativeName: String, val englishName: String)

val APP_LANGUAGES = listOf(
    LanguageOption("en", "English", "English"),
    LanguageOption("ur", "اردو", "Urdu"),
    LanguageOption("ar", "العربية", "Arabic"),
    LanguageOption("tr", "Türkçe", "Turkish"),
    LanguageOption("id", "Bahasa Indonesia", "Indonesian"),
    LanguageOption("ms", "Bahasa Melayu", "Malay"),
    LanguageOption("bn", "বাংলা", "Bengali"),
    LanguageOption("hi", "हिन्दी", "Hindi"),
    LanguageOption("fa", "فارسی", "Persian"),
    LanguageOption("fr", "Français", "French"),
    LanguageOption("de", "Deutsch", "German"),
    LanguageOption("es", "Español", "Spanish"),
    LanguageOption("ru", "Русский", "Russian"),
)

/** Selectable option cards for Sunni (Hanafi / Shafi) & Shia (Jafari) — replaces the old toggle switch. */
@Composable
fun FiqhOptionCards(selected: Fiqh, onSelect: (Fiqh) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Fiqh.entries.forEach { f ->
            val isSelected = selected == f
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                onClick = { onSelect(f) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(f.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(f.descRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = if (isSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(24.dp),
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

fun dayName(dow: Int, locale: Locale): String = DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, locale)

fun formatMinutes(minutes: Double, locale: Locale): String {
    val m = ((minutes.roundToInt() % 1440) + 1440) % 1440
    return LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

/** The next calendar date (today included) that falls on ISO weekday [dow]. */
fun nextDateOf(dow: Int): LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)))

fun slotRangeText(slot: Slot, dow: Int, settings: Settings, locale: Locale): String {
    val r = Engine.slotRange(slot, nextDateOf(dow), settings, ZoneId.systemDefault()) ?: return "—"
    return "${formatMinutes(r.first, locale)} – ${formatMinutes(r.second, locale)}"
}

fun daysMaskOf(days: List<Int>): Int = days.fold(0) { acc, d -> acc or (1 shl (d - 1)) }
fun daysOfMask(mask: Int): List<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    val locale = currentLocale()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (d in 1..7) {
            val bit = 1 shl (d - 1)
            val selected = mask and bit != 0
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                onClick = { onChange(mask xor bit) },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = dayShort(d, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

fun dayShort(dow: Int, locale: Locale): String {
    return when (locale.language) {
        "ar", "fa" -> DayOfWeek.of(dow).getDisplayName(TextStyle.NARROW_STANDALONE, locale)
        "ur", "hi", "bn" -> DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, locale)
        else -> DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, locale).take(3)
    }
}

private val QUICK_SLOT_DURATIONS = listOf(10, 15, 20, 30, 60)

/**
 * Modern unified Start/End slot time picker:
 * - Properly bounded modal width + scrollable on small screens
 * - Start & End tabs inside one window with live range & duration readout
 * - Clock dial <-> Keyboard (TimeInput) toggle
 * - 1-tap quick duration chips (+10m, +15m, +20m, +30m, +1h) from Start
 * - Smartly preserves duration when Start changes
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SlotTimePickerDialog(
    initialStartMinutes: Int,
    initialEndMinutes: Int,
    initialEditingStart: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (startMin: Int, endMin: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val locale = currentLocale()
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(ctx) }

    var startMin by remember { mutableIntStateOf(((initialStartMinutes % 1440) + 1440) % 1440) }
    var endMin by remember { mutableIntStateOf(((initialEndMinutes % 1440) + 1440) % 1440) }
    var editingStart by remember { mutableStateOf(initialEditingStart) }
    var useKeyboard by rememberSaveable { mutableStateOf(false) }
    var pickerRev by remember { mutableIntStateOf(0) }

    val durMin = remember(startMin, endMin) {
        val d = (endMin - startMin + 1440) % 1440
        if (d == 0) 1440 else d
    }
    val durText = if (durMin < 60) {
        stringResource(R.string.duration_min, durMin)
    } else if (durMin % 60 == 0) {
        "${durMin / 60}h"
    } else {
        stringResource(R.string.duration_hr_min, durMin / 60, durMin % 60)
    }

    val activeMinutes = if (editingStart) startMin else endMin

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header: Title + live range + Dial/Keyboard toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.set_slot_time),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${formatMinutes(startMin.toDouble(), locale)} → ${formatMinutes(endMin.toDouble(), locale)} · $durText",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { useKeyboard = !useKeyboard },
                    ) {
                        Text(
                            text = stringResource(if (useKeyboard) R.string.use_clock_dial else R.string.use_keyboard),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }

                // Start / End pill switcher inside one modal
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(4.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(true to startMin, false to endMin).forEach { (isStartTab, mins) ->
                            val selected = editingStart == isStartTab
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = {
                                    if (editingStart != isStartTab) {
                                        editingStart = isStartTab
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = stringResource(if (isStartTab) R.string.start else R.string.end),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Text(
                                        text = formatMinutes(mins.toDouble(), locale),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // Synced Material 3 TimePicker or TimeInput with custom theme-matched colors
                val pickerColors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainer,
                    clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    periodSelectorBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )

                key(editingStart, useKeyboard, pickerRev) {
                    val state = rememberTimePickerState(
                        initialHour = activeMinutes / 60,
                        initialMinute = activeMinutes % 60,
                        is24Hour = is24Hour,
                    )
                    LaunchedEffect(state.hour, state.minute) {
                        val picked = state.hour * 60 + state.minute
                        if (editingStart) {
                            if (picked != startMin) {
                                val keepDur = ((endMin - startMin + 1440) % 1440).takeIf { it > 0 } ?: 20
                                startMin = picked
                                endMin = (picked + keepDur) % 1440
                            }
                        } else {
                            if (picked != endMin) {
                                endMin = picked
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (useKeyboard) {
                            TimeInput(state = state, colors = pickerColors)
                        } else {
                            TimePicker(state = state, colors = pickerColors)
                        }
                    }
                }

                // Quick duration chips (sets End cleanly relative to Start)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.quick_duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QUICK_SLOT_DURATIONS.forEach { d ->
                            val selected = durMin == d
                            val chipLabel = if (d < 60) "+${d}m" else "+${d / 60}h"
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                onClick = {
                                    endMin = (startMin + d) % 1440
                                    if (!editingStart) pickerRev += 1
                                },
                            ) {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                // Footer buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        shape = RoundedCornerShape(14.dp),
                        onClick = { onConfirm(startMin, endMin) },
                    ) {
                        Text(stringResource(R.string.set_time_btn))
                    }
                }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Smooth, glitch-free silence radius slider (50 m – 1000 m) with square-root spacing
 * so 50 m / 100 m / 200 m feel natural and without Material 3's clipped stop-dot gap at 50 m.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadiusSlider(
    radiusMeters: Int,
    onRadiusChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sqrtMin = sqrt(50.0)
    val sqrtMax = sqrt(1000.0)
    fun toFrac(r: Int): Float = ((sqrt(r.coerceIn(50, 1000).toDouble()) - sqrtMin) / (sqrtMax - sqrtMin))
        .toFloat().coerceIn(0f, 1f)
    fun toMeters(f: Float): Int {
        val s = sqrtMin + f.coerceIn(0f, 1f) * (sqrtMax - sqrtMin)
        return (((s * s) / 10.0).roundToInt() * 10).coerceIn(50, 1000)
    }

    var dragPos by remember { mutableFloatStateOf(toFrac(radiusMeters)) }
    var dragging by remember { mutableStateOf(false) }
    val pos = if (dragging) dragPos else toFrac(radiusMeters)

    Slider(
        value = pos,
        onValueChange = { f ->
            dragging = true
            dragPos = f
            onRadiusChange(toMeters(f))
        },
        onValueChangeFinished = { dragging = false },
        valueRange = 0f..1f,
        modifier = modifier.fillMaxWidth(),
        thumb = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.surfaceContainerLowest),
                shadowElevation = 3.dp,
                modifier = Modifier.size(22.dp),
            ) {}
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                modifier = Modifier.height(6.dp),
            )
        },
    )
}
