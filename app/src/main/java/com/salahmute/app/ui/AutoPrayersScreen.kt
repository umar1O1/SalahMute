package com.salahmute.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.system.Scheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val DURATION_PRESETS = listOf(
    0 to 15,
    0 to 20,
    5 to 25,
    10 to 30,
)

/** Dedicated full-screen Auto Prayer Times setup with live city times, presets, and +/- steppers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoPrayersScreen(
    onPickCity: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val data by Store.data.collectAsState()
    val cacheVer by OnlinePrayerSync.version.collectAsState()
    val settings = data.settings
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    var syncing by remember { mutableStateOf(false) }

    // Pre-populate from any existing prayer slots so returning here feels seamless
    val existingPrayers = remember(data.active.id) {
        data.active.slots.filter { it.prayer != null }
    }
    val initialMask = remember(existingPrayers) {
        if (existingPrayers.isEmpty()) 0b11111
        else Prayer.entries.foldIndexed(0) { i, acc, p ->
            if (existingPrayers.any { it.prayer == p }) acc or (1 shl i) else acc
        }
    }
    var selectedMask by rememberSaveable { mutableIntStateOf(initialMask) }
    var beforeMin by rememberSaveable { mutableIntStateOf(existingPrayers.firstOrNull()?.before ?: 0) }
    var afterMin by rememberSaveable { mutableIntStateOf(existingPrayers.firstOrNull()?.after ?: 20) }
    var mode by rememberSaveable { mutableStateOf(existingPrayers.firstOrNull()?.mode ?: RingMode.VIBRATE) }

    val selectedCount = Prayer.entries.indices.count { selectedMask and (1 shl it) != 0 }
    val hasCity = settings.lat != null && settings.lon != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_prayers_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.prayer_dialog_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Card 1: City & Calculation Method
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (hasCity) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.tertiaryContainer,
                border = if (hasCity) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasCity) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = if (hasCity) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (hasCity) {
                                settings.locationName.ifBlank {
                                    "%.2f, %.2f".format(Locale.US, settings.lat!!, settings.lon!!)
                                }
                            } else {
                                stringResource(R.string.prayer_set_city_title)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasCity) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            if (hasCity) {
                                "${stringResource(settings.effectiveFiqh.labelRes())} · ${stringResource(settings.method.labelRes())}"
                            } else {
                                stringResource(R.string.prayer_set_city_first)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasCity) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (hasCity) {
                        TextButton(onClick = onPickCity) { Text(stringResource(R.string.change)) }
                    } else {
                        FilledTonalButton(onClick = onPickCity, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.pick_on_map))
                        }
                    }
                }
            }

            // Source card: Internet sync vs Offline calculation
            if (hasCity) {
                EditorCard(title = stringResource(R.string.prayer_source_title)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = settings.useInternetPrayers,
                            onClick = {
                                Store.setSettings(ctx) { it.copy(useInternetPrayers = true) }
                                scope.launch {
                                    syncing = true
                                    val ok = OnlinePrayerSync.sync(ctx, Store.value.settings)
                                    Scheduler.reconcile(ctx)
                                    syncing = false
                                    if (!ok) Toast.makeText(ctx, R.string.prayer_sync_failed, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.prayer_source_internet), maxLines = 1) }
                        SegmentedButton(
                            selected = !settings.useInternetPrayers,
                            onClick = { Store.setSettings(ctx) { it.copy(useInternetPrayers = false) } },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.prayer_source_offline), maxLines = 1) }
                    }
                    if (settings.useInternetPrayers) {
                        val synced = remember(cacheVer, settings) { OnlinePrayerSync.isSyncedForToday(settings) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    syncing -> stringResource(R.string.prayer_syncing)
                                    synced -> stringResource(R.string.prayer_synced_ok)
                                    else -> stringResource(R.string.prayer_source_internet_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                enabled = !syncing,
                                onClick = {
                                    scope.launch {
                                        syncing = true
                                        val ok = OnlinePrayerSync.sync(ctx, Store.value.settings)
                                        Scheduler.reconcile(ctx)
                                        syncing = false
                                        Toast.makeText(
                                            ctx,
                                            if (ok) R.string.prayer_synced_ok else R.string.prayer_sync_failed,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.prayer_sync_now))
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.prayer_source_offline_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Card 2: 5 Daily Prayers with Live Today Times
            EditorCard(title = stringResource(R.string.pick_prayers)) {
                Prayer.entries.forEachIndexed { idx, p ->
                    val checked = selectedMask and (1 shl idx) != 0
                    val preview = remember(cacheVer, settings, beforeMin, afterMin, p) {
                        Engine.slotRange(
                            Slot(prayer = p, before = beforeMin, after = afterMin),
                            today,
                            settings,
                            zone,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedMask = selectedMask xor (1 shl idx) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(p.labelRes()),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (preview != null) {
                                    "${formatMinutes(preview.first, locale)} – ${formatMinutes(preview.second, locale)}"
                                } else {
                                    stringResource(R.string.prayer_offset_summary, beforeMin, afterMin)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (checked && preview != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { selectedMask = selectedMask xor (1 shl idx) },
                        )
                    }
                    if (idx < Prayer.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }

            // Card 3: Duration around each prayer (Presets + +/- Steppers)
            EditorCard(title = stringResource(R.string.prayer_duration_title)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATION_PRESETS.forEach { (b, a) ->
                        FilterChip(
                            selected = beforeMin == b && afterMin == a,
                            onClick = { beforeMin = b; afterMin = a },
                            label = { Text(if (b == 0) "+${a}m" else "−${b}m / +${a}m") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MinuteStepper(
                        label = stringResource(R.string.minutes_before),
                        value = beforeMin,
                        onChange = { beforeMin = it.coerceIn(0, 120) },
                        modifier = Modifier.weight(1f),
                    )
                    MinuteStepper(
                        label = stringResource(R.string.minutes_after),
                        value = afterMin,
                        onChange = { afterMin = it.coerceIn(5, 180) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.prayer_offset_summary, beforeMin, afterMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Card 4: Phone Mode
            EditorCard(title = stringResource(R.string.mode)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RingMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(stringResource(m.labelRes())) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.slot_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                onClick = {
                    val slots = Prayer.entries.filterIndexed { i, _ -> selectedMask and (1 shl i) != 0 }.map { p ->
                        Slot(
                            label = ctx.getString(p.labelRes()),
                            mode = mode,
                            days = Slot.ALL_DAYS,
                            prayer = p,
                            before = beforeMin,
                            after = afterMin,
                        )
                    }
                    Store.setPrayerSlots(ctx, slots)
                    if (!hasCity) Toast.makeText(ctx, R.string.loc_not_set, Toast.LENGTH_LONG).show()
                    onClose()
                },
            ) {
                Text(
                    stringResource(
                        if (existingPrayers.isEmpty()) R.string.enable_prayers_cta else R.string.save_prayers_cta,
                        selectedCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MinuteStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    onClick = { onChange(value - 5) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "${value}m",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    onClick = { onChange(value + 5) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
