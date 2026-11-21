package com.salahmute.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Slot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SlotEditorScreen(slotId: String?, initialDay: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val locale = currentLocale()
    val data by Store.data.collectAsState()
    val existing = remember(slotId) { data.active.slots.firstOrNull { it.id == slotId } }
    val isMultiDay = (existing?.days?.size ?: 1) > 1
    val canSwitchPrayerMode = existing?.prayer != null

    // Smart default for new slots: next rounded 5-minute mark + 20 min
    val defaultStart = remember {
        val now = LocalTime.now()
        val raw = now.hour * 60 + now.minute
        (((raw / 5) + 1) * 5) % 1440
    }
    val defaultEnd = remember(defaultStart) { (defaultStart + 20) % 1440 }

    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var isPrayer by rememberSaveable { mutableStateOf(existing?.prayer != null) }
    var start by rememberSaveable { mutableIntStateOf(existing?.startMin ?: defaultStart) }
    var end by rememberSaveable { mutableIntStateOf(existing?.endMin ?: defaultEnd) }
    var prayer by rememberSaveable { mutableStateOf(existing?.prayer ?: Prayer.DHUHR) }
    var before by rememberSaveable { mutableStateOf((existing?.before ?: 0).toString()) }
    var after by rememberSaveable { mutableStateOf((existing?.after ?: 20).toString()) }
    var mode by rememberSaveable { mutableStateOf(existing?.mode ?: RingMode.VIBRATE) }
    var applyAllDays by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var timePickerTarget by remember { mutableStateOf<Boolean?>(null) } // true=start, false=end, null=closed
    var confirmDelete by remember { mutableStateOf(false) }

    val errSame = stringResource(R.string.err_same_time)
    val initialDayName = remember(initialDay, locale) {
        DayOfWeek.of(initialDay).getDisplayName(TextStyle.FULL, locale)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) stringResource(R.string.add_to_day, initialDayName)
                        else stringResource(R.string.edit_slot),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Only shown when editing a slot that currently appears on multiple days (defaults to this day only)
            if (isMultiDay && existing != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !applyAllDays,
                        onClick = { applyAllDays = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.apply_this_day, initialDayName)) }
                    SegmentedButton(
                        selected = applyAllDays,
                        onClick = { applyAllDays = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.apply_all_days, existing.days.size)) }
                }
            }

            // Card 1: Time
            EditorCard(title = stringResource(R.string.slot_when)) {
                if (canSwitchPrayerMode) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !isPrayer,
                            onClick = { isPrayer = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.fixed_time)) }
                        SegmentedButton(
                            selected = isPrayer,
                            onClick = { isPrayer = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.prayer_based)) }
                    }
                }

                if (!isPrayer) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        TimeBox(
                            label = stringResource(R.string.start),
                            timeText = formatMinutes(start.toDouble(), locale),
                            onClick = { timePickerTarget = true },
                            modifier = Modifier.weight(1f),
                        )
                        TimeBox(
                            label = stringResource(R.string.end),
                            timeText = formatMinutes(end.toDouble(), locale),
                            onClick = { timePickerTarget = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val durMin = if (end > start) end - start else (1440 - start + end)
                    val durLabel = if (durMin < 60) {
                        stringResource(R.string.duration_min, durMin)
                    } else {
                        stringResource(R.string.duration_hr_min, durMin / 60, durMin % 60)
                    }
                    val suffix = if (end < start) " · ${stringResource(R.string.overnight)}" else ""
                    Text(
                        durLabel + suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Prayer.entries.forEach { p ->
                            FilterChip(selected = prayer == p, onClick = { prayer = p }, label = { Text(stringResource(p.labelRes())) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = before,
                            onValueChange = { before = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.minutes_before)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = after,
                            onValueChange = { after = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.minutes_after)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val previewSlot = Slot(
                        prayer = prayer,
                        before = (before.toIntOrNull() ?: 0).coerceIn(0, 180),
                        after = (after.toIntOrNull() ?: 0).coerceIn(0, 180),
                    )
                    val previewRange = Engine.slotRange(previewSlot, LocalDate.now(), data.settings, ZoneId.systemDefault())
                    if (previewRange != null) {
                        val txt = "${formatMinutes(previewRange.first, locale)} – ${formatMinutes(previewRange.second, locale)}"
                        Text(
                            stringResource(R.string.prayer_today_preview, txt),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(stringResource(R.string.loc_not_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Card 2: Label & Mode
            EditorCard(title = stringResource(R.string.mode)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text(stringResource(R.string.label)) },
                    placeholder = { Text(stringResource(R.string.slot_label_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RingMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(stringResource(m.labelRes())) })
                    }
                }
                Text(
                    stringResource(R.string.slot_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = {
                    if (!isPrayer && start == end) {
                        error = errSame
                    } else {
                        Store.upsertSlotForDay(
                            ctx = ctx,
                            originalId = existing?.id,
                            edited = Slot(
                                label = label.trim(),
                                startMin = start,
                                endMin = end,
                                mode = mode,
                                days = listOf(initialDay),
                                enabled = existing?.enabled ?: true,
                                prayer = if (isPrayer) prayer else null,
                                before = (before.toIntOrNull() ?: 0).coerceIn(0, 180),
                                after = (after.toIntOrNull() ?: 0).coerceIn(0, 180),
                            ),
                            day = initialDay,
                            applyAllDays = applyAllDays,
                        )
                        onClose()
                    }
                },
            ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        }
    }

    timePickerTarget?.let { editingStart ->
        SlotTimePickerDialog(
            initialStartMinutes = start,
            initialEndMinutes = end,
            initialEditingStart = editingStart,
            onDismiss = { timePickerTarget = null },
            onConfirm = { newStart, newEnd ->
                start = newStart
                end = newEnd
                error = null
                timePickerTarget = null
            },
        )
    }
    if (confirmDelete && existing != null) {
        val baseName = existing.label.ifBlank {
            existing.prayer?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.slot_untitled)
        }
        val targetName = if (isMultiDay) {
            val scope = if (applyAllDays) {
                stringResource(R.string.apply_all_days, existing.days.size)
            } else {
                stringResource(R.string.apply_this_day, initialDayName)
            }
            "$baseName ($scope)"
        } else {
            baseName
        }
        ConfirmDialog(
            stringResource(R.string.delete_confirm, targetName),
            { confirmDelete = false },
        ) {
            Store.deleteSlotForDay(ctx, existing.id, initialDay, applyAllDays)
            confirmDelete = false
            onClose()
        }
    }
}

@Composable
private fun TimeBox(label: String, timeText: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(timeText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
