package com.salahmute.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.salahmute.app.BuildConfig
import com.salahmute.app.R
import com.salahmute.app.data.BackupManager
import com.salahmute.app.data.Store
import com.salahmute.app.map.CityCatalog
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.PermState
import com.salahmute.app.system.Perms
import com.salahmute.app.system.Scheduler
import com.salahmute.app.system.StateStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class SettingsPage { MENU, APPEARANCE, PERMISSIONS, PRAYERS, LOCATIONS, BACKUP, LANGUAGE, ABOUT }

private fun safeStart(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        try {
            ctx.startActivity(Perms.appDetailsIntent(ctx))
        } catch (_: ActivityNotFoundException) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    page: SettingsPage,
    onPageChange: (SettingsPage) -> Unit,
    onOpenShare: () -> Unit,
    onOpenRestoreBackup: () -> Unit,
    onPickPrayerLocationOnMap: (lat: Double?, lon: Double?) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(Perms.read(ctx)) }
    val data by Store.data.collectAsState()
    val s = data.settings

    var latText by remember { mutableStateOf(s.lat?.let { "%.4f".format(Locale.US, it) } ?: "") }
    var lonText by remember { mutableStateOf(s.lon?.let { "%.4f".format(Locale.US, it) } ?: "") }
    var showManualCoords by remember { mutableStateOf(false) }

    LaunchedEffect(s.lat, s.lon) {
        s.lat?.let { latText = "%.4f".format(Locale.US, it) }
        s.lon?.let { lonText = "%.4f".format(Locale.US, it) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = Perms.read(ctx)
        Scheduler.reconcile(ctx)
    }

    BackHandler(enabled = page != SettingsPage.MENU) { onPageChange(SettingsPage.MENU) }

    fun commitLocation(lat: String, lon: String, name: String = "") {
        val la = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val lo = lon.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
        Store.setSettings(ctx) { it.copy(lat = la, lon = lo, locationName = name) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = Perms.read(ctx)
    }
    val fineLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        perms = Perms.read(ctx)
        GeoController.sync(ctx)
        Scheduler.reconcile(ctx)
    }
    val bgLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = Perms.read(ctx)
        GeoController.sync(ctx)
        Scheduler.reconcile(ctx)
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        GeoController.fetchCurrentLocation(ctx) { loc ->
            if (loc != null) {
                latText = "%.4f".format(Locale.US, loc.latitude)
                lonText = "%.4f".format(Locale.US, loc.longitude)
                commitLocation(latText, lonText, "")
            } else {
                Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    val titleRes = when (page) {
        SettingsPage.MENU -> R.string.settings
        SettingsPage.APPEARANCE -> R.string.appearance
        SettingsPage.PERMISSIONS -> R.string.permissions
        SettingsPage.PRAYERS -> R.string.prayer_settings
        SettingsPage.LOCATIONS -> R.string.section_location_silence
        SettingsPage.BACKUP -> R.string.backup_title
        SettingsPage.LANGUAGE -> R.string.language
        SettingsPage.ABOUT -> R.string.about_title
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (page == SettingsPage.MENU) onClose() else onPageChange(SettingsPage.MENU)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                SettingsPage.MENU -> {
                    val readyCount = perms.coreGrantedCount
                    val themeName = stringResource(s.themeMode.labelRes())
                    val prayerSub = when {
                        s.locationName.isNotBlank() -> "${s.locationName} · ${stringResource(s.method.labelRes())}"
                        s.lat != null && s.lon != null -> "${"%.2f, %.2f".format(Locale.US, s.lat, s.lon)} · ${stringResource(s.method.labelRes())}"
                        else -> stringResource(R.string.settings_prayer_unset)
                    }
                    val locSub = if (data.geoZones.isNotEmpty()) {
                        stringResource(R.string.settings_loc_count, data.geoZones.size)
                    } else {
                        stringResource(R.string.settings_loc_optional)
                    }
                    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
                    val langLabel = when (currentTag) {
                        "en" -> "English"
                        "ur" -> "اردو"
                        "ar" -> "العربية"
                        "tr" -> "Türkçe"
                        "id", "in" -> "Bahasa Indonesia"
                        "ms" -> "Bahasa Melayu"
                        "bn" -> "বাংলা"
                        "hi" -> "हिन्दी"
                        "fa" -> "فارسی"
                        "fr" -> "Français"
                        "de" -> "Deutsch"
                        "es" -> "Español"
                        "ru" -> "Русский"
                        else -> stringResource(R.string.lang_system)
                    }

                    // Group 1: Appearance & Language
                    MenuGroupCard {
                        SettingsMenuRow(
                            icon = Icons.Filled.Edit,
                            title = stringResource(R.string.appearance),
                            subtitle = themeName,
                            onClick = { onPageChange(SettingsPage.APPEARANCE) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.language),
                            subtitle = langLabel,
                            onClick = { onPageChange(SettingsPage.LANGUAGE) },
                        )
                    }

                    // Group 2: Permissions, Prayer Times, Locations
                    MenuGroupCard {
                        SettingsMenuRow(
                            icon = if (perms.essentialsOk) Icons.Filled.Notifications else Icons.Filled.Warning,
                            iconTint = if (perms.essentialsOk) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            iconBg = if (perms.essentialsOk) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            title = stringResource(R.string.permissions),
                            subtitle = "$readyCount / 3 · ${stringResource(R.string.section_essentials)}",
                            onClick = { onPageChange(SettingsPage.PERMISSIONS) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuRow(
                            icon = Icons.Filled.DateRange,
                            title = stringResource(R.string.prayer_settings),
                            subtitle = prayerSub,
                            onClick = { onPageChange(SettingsPage.PRAYERS) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuRow(
                            icon = Icons.Filled.LocationOn,
                            title = stringResource(R.string.section_location_silence),
                            subtitle = locSub,
                            onClick = { onPageChange(SettingsPage.LOCATIONS) },
                        )
                    }

                    // Group 3: Backup & Share
                    MenuGroupCard {
                        SettingsMenuRow(
                            icon = Icons.Filled.Refresh,
                            title = stringResource(R.string.backup_title),
                            subtitle = stringResource(R.string.backup_sub),
                            onClick = { onPageChange(SettingsPage.BACKUP) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsMenuRow(
                            icon = Icons.Filled.Share,
                            title = stringResource(R.string.share_title),
                            subtitle = stringResource(R.string.settings_share_sub),
                            onClick = onOpenShare,
                        )
                    }

                    // Group 4: About
                    MenuGroupCard {
                        SettingsMenuRow(
                            icon = Icons.Filled.Favorite,
                            title = stringResource(R.string.about_title),
                            subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                            onClick = { onPageChange(SettingsPage.ABOUT) },
                        )
                    }
                }

                SettingsPage.APPEARANCE -> {
                    SettingsCard(title = stringResource(R.string.appearance)) {
                        ThemePickerSection(
                            themeMode = s.themeMode,
                            accentHex = s.accentHex,
                            onThemeModeChange = { m -> Store.setSettings(ctx) { it.copy(themeMode = m) } },
                            onAccentChange = { hex -> Store.setSettings(ctx) { it.copy(accentHex = hex) } },
                        )
                    }
                    SettingsCard(title = stringResource(R.string.card_actions_style)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = s.compactCardActions,
                                onClick = { Store.setSettings(ctx) { it.copy(compactCardActions = true) } },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text(stringResource(R.string.card_actions_icons), maxLines = 1) }
                            SegmentedButton(
                                selected = !s.compactCardActions,
                                onClick = { Store.setSettings(ctx) { it.copy(compactCardActions = false) } },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(stringResource(R.string.card_actions_labels), maxLines = 1) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.tap_card_to_edit),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.tap_card_to_edit_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = s.tapCardToEdit,
                                onCheckedChange = { enabled ->
                                    Store.setSettings(ctx) { it.copy(tapCardToEdit = enabled) }
                                },
                            )
                        }
                    }
                }

                SettingsPage.PERMISSIONS -> {
                    val readyCount = perms.coreGrantedCount
                    SettingsCard(
                        title = stringResource(R.string.section_essentials),
                        badge = "$readyCount / 3",
                    ) {
                        PermRow(R.string.perm_dnd, R.string.perm_dnd_desc, perms.policy) {
                            safeStart(ctx, Perms.dndSettingsIntent(ctx))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        PermRow(R.string.perm_alarm, R.string.perm_alarm_desc, perms.exactAlarm) {
                            if (Build.VERSION.SDK_INT >= 31) {
                                safeStart(ctx, Perms.exactAlarmSettingsIntent(ctx))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        PermRow(R.string.perm_battery, R.string.perm_battery_desc, perms.battery) {
                            safeStart(ctx, Perms.batterySettingsIntent(ctx))
                        }
                    }

                    SettingsCard(title = stringResource(R.string.section_optional_reliability)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.perm_keep_alive),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.perm_keep_alive_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = s.keepAliveService,
                                onCheckedChange = { enabled ->
                                    Store.setSettings(ctx) { it.copy(keepAliveService = enabled) }
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        PermRow(R.string.perm_notif, R.string.perm_notif_desc, perms.notifications) {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        val autostartIntent = remember { Perms.oemAutostartIntent(ctx) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (autostartIntent != null) R.string.perm_autostart else R.string.perm_app_battery),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(if (autostartIntent != null) R.string.perm_autostart_desc else R.string.perm_app_battery_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { safeStart(ctx, autostartIntent ?: Perms.appDetailsIntent(ctx)) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.open_settings_btn))
                            }
                        }
                    }

                    // Simple user-friendly status line
                    val live by StateStore.live.collectAsState()
                    val timeFmt = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
                    val statusLine = when {
                        !perms.exactAlarm -> stringResource(R.string.bg_status_needs_alarm)
                        live.nextAlarmAt > 0L -> {
                            val t = timeFmt.format(Instant.ofEpochMilli(live.nextAlarmAt).atZone(ZoneId.systemDefault()))
                            stringResource(R.string.bg_status_active_next, t)
                        }
                        else -> stringResource(R.string.bg_status_active_idle)
                    }
                    SettingsCard(title = stringResource(R.string.bg_status_title)) {
                        Text(
                            statusLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!perms.exactAlarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                SettingsPage.PRAYERS -> {
                    val scope = rememberCoroutineScope()
                    val cacheVer by OnlinePrayerSync.version.collectAsState()
                    var syncing by remember { mutableStateOf(false) }

                    SettingsCard(title = stringResource(R.string.location)) {
                        val locLabel = when {
                            s.locationName.isNotBlank() -> s.locationName
                            s.lat != null && s.lon != null -> stringResource(R.string.location_selected)
                            else -> null
                        }
                        if (locLabel != null) {
                            Text(locLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        } else {
                            Text(stringResource(R.string.loc_not_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { onPickPrayerLocationOnMap(s.lat, s.lon) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.prayer_loc_pick), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(stringResource(R.string.my_location))
                            }
                        }

                        TextButton(onClick = { showManualCoords = !showManualCoords }) {
                            Text(stringResource(R.string.manual_coords))
                        }
                        if (showManualCoords) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = latText,
                                    onValueChange = {
                                        latText = it
                                        commitLocation(it, lonText)
                                    },
                                    label = { Text(stringResource(R.string.latitude)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = lonText,
                                    onValueChange = {
                                        lonText = it
                                        commitLocation(latText, it)
                                    },
                                    label = { Text(stringResource(R.string.longitude)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Selectable Fiqh / School option cards (Sunni Hanafi, Sunni Shafi, Shia Jafari — NO toggle!)
                    SettingsCard(title = stringResource(R.string.fiqh_title)) {
                        FiqhOptionCards(
                            selected = s.effectiveFiqh,
                            onSelect = { chosen ->
                                Store.setSettings(ctx) { cur ->
                                    val nextMethod = when {
                                        chosen == Fiqh.SHIA_JAFARI -> CalcMethod.JAFARI
                                        cur.method == CalcMethod.JAFARI -> CityCatalog.suggestMethodFor(cur.locationName.ifBlank { cur.countryName })
                                        else -> cur.method
                                    }
                                    cur.copy(fiqh = chosen, hanafi = chosen == Fiqh.HANAFI, method = nextMethod)
                                }
                                if (Store.value.settings.useInternetPrayers) {
                                    scope.launch {
                                        syncing = true
                                        OnlinePrayerSync.sync(ctx, Store.value.settings)
                                        Scheduler.reconcile(ctx)
                                        syncing = false
                                    }
                                }
                            },
                        )
                    }

                    // Prayer time source (Internet sync vs Offline calculation)
                    SettingsCard(title = stringResource(R.string.prayer_source_title)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = s.useInternetPrayers,
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
                                selected = !s.useInternetPrayers,
                                onClick = { Store.setSettings(ctx) { it.copy(useInternetPrayers = false) } },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(stringResource(R.string.prayer_source_offline), maxLines = 1) }
                        }
                        if (s.useInternetPrayers) {
                            val synced = remember(cacheVer, s) { OnlinePrayerSync.isSyncedForToday(s) }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
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
                                FilledTonalButton(
                                    enabled = !syncing && s.lat != null && s.lon != null,
                                    shape = RoundedCornerShape(12.dp),
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

                    SettingsCard(title = stringResource(R.string.calc_method)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CalcMethod.entries.forEach { m ->
                                FilterChip(
                                    selected = s.method == m,
                                    onClick = {
                                        Store.setSettings(ctx) { it.copy(method = m) }
                                        if (Store.value.settings.useInternetPrayers) {
                                            scope.launch {
                                                syncing = true
                                                OnlinePrayerSync.sync(ctx, Store.value.settings)
                                                Scheduler.reconcile(ctx)
                                                syncing = false
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(m.labelRes())) },
                                )
                            }
                        }
                    }
                }

                SettingsPage.LOCATIONS -> {
                    // Card 1: Silent places toggle, saved places list & Check location now
                    SettingsCard(
                        title = stringResource(R.string.section_location_silence),
                        badge = if (data.geoZones.isNotEmpty()) data.geoZones.size.toString() else null,
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.silent_places_enabled),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.silent_places_enabled_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = data.geoEnabled,
                                onCheckedChange = { Store.setGeoEnabled(ctx, it) },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        if (data.geoZones.isEmpty()) {
                            Text(
                                stringResource(R.string.no_saved_places_yet),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            data.geoZones.forEachIndexed { idx, z ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            z.label.ifBlank { stringResource(R.string.location) },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "${stringResource(R.string.radius_fmt, z.radiusMeters)} · ${stringResource(z.mode.labelRes())}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = z.enabled,
                                        onCheckedChange = { Store.setGeoZoneEnabled(ctx, z.id, it) },
                                    )
                                }
                                if (idx < data.geoZones.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (!perms.fineLocation) {
                                        fineLocLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ),
                                        )
                                    } else {
                                        GeoController.refreshNow(ctx) { loc ->
                                            Toast.makeText(
                                                ctx,
                                                if (loc != null) R.string.location_checked else R.string.loc_failed,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.check_now))
                            }
                        }
                    }

                    // Card 2: Location access permissions for silent places
                    SettingsCard(title = stringResource(R.string.location_access_title)) {
                        PermRow(R.string.perm_fine_loc, R.string.perm_fine_loc_desc, perms.fineLocation) {
                            fineLocLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        }
                        if (Build.VERSION.SDK_INT >= 29 && perms.fineLocation) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            PermRow(R.string.perm_bg_loc, R.string.perm_bg_loc_desc, perms.backgroundLocation) {
                                bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                        Text(
                            stringResource(R.string.loc_privacy_inline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsPage.BACKUP -> {
                    var autoBackup by remember { mutableStateOf(BackupManager.isAutoBackupEnabled(ctx)) }
                    var lastBackupMs by remember { mutableStateOf(BackupManager.lastBackupAt(ctx)) }
                    var confirmDeleteBackup by remember { mutableStateOf(false) }

                    val saveOtherLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/octet-stream"),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        if (BackupManager.saveToDocumentUri(ctx, uri, Store.value)) {
                            lastBackupMs = BackupManager.lastBackupAt(ctx)
                            Toast.makeText(ctx, R.string.backup_saved_ok, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, R.string.backup_save_failed, Toast.LENGTH_LONG).show()
                        }
                    }

                    SettingsCard(title = stringResource(R.string.backup_title)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.auto_backup),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    stringResource(R.string.auto_backup_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = autoBackup,
                                onCheckedChange = { enabled ->
                                    autoBackup = enabled
                                    BackupManager.setAutoBackupEnabled(ctx, enabled)
                                    if (enabled && BackupManager.saveLocalBackup(ctx, Store.value)) {
                                        lastBackupMs = BackupManager.lastBackupAt(ctx)
                                    }
                                },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text(
                            stringResource(R.string.backup_folder_path),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        val lastTxt = if (lastBackupMs > 0L) {
                            val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                .format(Instant.ofEpochMilli(lastBackupMs).atZone(ZoneId.systemDefault()))
                            stringResource(R.string.backup_last_at, formatted)
                        } else {
                            stringResource(R.string.backup_not_yet)
                        }
                        Text(
                            lastTxt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                if (BackupManager.saveLocalBackup(ctx, Store.value)) {
                                    lastBackupMs = BackupManager.lastBackupAt(ctx)
                                    Toast.makeText(ctx, R.string.backup_saved_ok, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(ctx, R.string.backup_save_failed, Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.backup_now))
                        }
                        OutlinedButton(
                            onClick = { saveOtherLauncher.launch(BackupManager.BACKUP_FILENAME) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.backup_save_other))
                        }
                    }

                    SettingsCard(title = stringResource(R.string.restore_backup)) {
                        FilledTonalButton(
                            onClick = onOpenRestoreBackup,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.restore_backup))
                        }
                        OutlinedButton(
                            onClick = { confirmDeleteBackup = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (confirmDeleteBackup) {
                        AlertDialog(
                            onDismissRequest = { confirmDeleteBackup = false },
                            title = { Text(stringResource(R.string.backup_delete)) },
                            text = { Text(stringResource(R.string.backup_delete_confirm)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        BackupManager.deleteLocalBackup(ctx)
                                        lastBackupMs = BackupManager.lastBackupAt(ctx)
                                        confirmDeleteBackup = false
                                        Toast.makeText(ctx, R.string.backup_deleted_ok, Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDeleteBackup = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }

                SettingsPage.LANGUAGE -> {
                    val rawTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
                    val current = if (rawTag == "in") "id" else rawTag
                    val options = listOf(
                        "" to null,
                        "en" to "English",
                        "ur" to "اردو",
                        "ar" to "العربية",
                        "tr" to "Türkçe",
                        "id" to "Bahasa Indonesia",
                        "ms" to "Bahasa Melayu",
                        "bn" to "বাংলা",
                        "hi" to "हिन्दी",
                        "fa" to "فارسی",
                        "fr" to "Français",
                        "de" to "Deutsch",
                        "es" to "Español",
                        "ru" to "Русский",
                    )
                    MenuGroupCard {
                        options.forEachIndexed { idx, (tag, name) ->
                            val selected = current == tag
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name ?: stringResource(R.string.lang_system),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (idx < options.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }

                SettingsPage.ABOUT -> {
                    // Header card: App emblem, name, version pill & privacy tagline
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_notif),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = stringResource(R.string.about_tagline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Talab-e-Dua card
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.about_dua_request_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.about_dua_request_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            Text(
                                text = stringResource(R.string.about_dua_request_body),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 23.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Dua for You in Return card (soft primary-tinted blessing card)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.about_dua_return_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.about_dua_return_body),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 23.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Feedback & bug report card (GitHub Issues + 1-tap Copy device info)
                    SettingsCard(title = stringResource(R.string.about_feedback_title)) {
                        Text(
                            text = stringResource(R.string.about_feedback_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                val uri = buildBugReportUri(perms)
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (_: ActivityNotFoundException) {
                                    val cm = ctx.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(ClipData.newPlainText("SalahMute Info", buildDiagnosticInfo(perms)))
                                    Toast.makeText(ctx, R.string.device_info_copied, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.report_bug_btn))
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("SalahMute Info", buildDiagnosticInfo(perms)))
                                Toast.makeText(ctx, R.string.device_info_copied, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painterResource(R.drawable.ic_copy), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_device_info))
                        }
                    }

                    // Privacy guarantee footer card
                    SettingsCard(title = stringResource(R.string.privacy_title)) {
                        Text(
                            text = stringResource(R.string.privacy_p2_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun buildDeviceSummary(): String {
    val mfg = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    val phone = if (model.startsWith(mfg, ignoreCase = true)) model else "$mfg $model".trim()
    return "$phone · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

private fun buildPermissionSummary(perms: PermState): String =
    "DND=${if (perms.policy) "OK" else "NO"}, " +
        "Alarm=${if (perms.exactAlarm) "OK" else "NO"}, " +
        "Battery=${if (perms.battery) "OK" else "NO"}, " +
        "Notif=${if (perms.notifications) "OK" else "NO"}, " +
        "Loc=${if (perms.backgroundLocation) "BG" else if (perms.fineLocation) "FG" else "OFF"}"

private fun buildDiagnosticInfo(perms: PermState): String =
    "App: SalahMute ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Device: ${buildDeviceSummary()}\n" +
        "Permissions: ${buildPermissionSummary(perms)}"

private fun buildBugReportUri(perms: PermState): Uri =
    Uri.parse("https://github.com/umar1O1/SalahMute/issues/new")
        .buildUpon()
        .appendQueryParameter("template", "bug_report.yml")
        .appendQueryParameter("app_version", BuildConfig.VERSION_NAME)
        .appendQueryParameter("device_info", buildDeviceSummary())
        .appendQueryParameter("permissions", buildPermissionSummary(perms))
        .build()

@Composable
private fun MenuGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    iconBg: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun PermRow(title: Int, desc: Int, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Icon(Icons.Filled.CheckCircle, stringResource(R.string.granted), tint = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.grant)) }
        }
    }
}
