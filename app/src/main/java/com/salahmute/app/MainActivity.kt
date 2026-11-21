package com.salahmute.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.salahmute.app.data.BackupManager
import com.salahmute.app.data.FoundBackup
import com.salahmute.app.data.Store
import com.salahmute.app.map.GoogleMapsLink
import com.salahmute.app.model.RingMode
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.share.Bundle as ShareBundle
import com.salahmute.app.share.ShareCodec
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.Perms
import com.salahmute.app.system.Scheduler
import com.salahmute.app.ui.AutoPrayersScreen
import com.salahmute.app.ui.BackupFoundDialog
import com.salahmute.app.ui.GeoDraft
import com.salahmute.app.ui.GeoZoneEditorScreen
import com.salahmute.app.ui.HomeScreen
import com.salahmute.app.ui.ImportDialog
import com.salahmute.app.ui.MapPickerScreen
import com.salahmute.app.ui.RestoreBackupScreen
import com.salahmute.app.ui.SalahTheme
import com.salahmute.app.ui.SettingsPage
import com.salahmute.app.ui.SettingsScreen
import com.salahmute.app.ui.ShareScreen
import com.salahmute.app.ui.SlotEditorScreen
import com.salahmute.app.ui.WelcomeScreen
import com.salahmute.app.ui.isDarkThemeActive
import com.salahmute.app.ui.readUriText
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private val incoming = mutableStateOf<String?>(null)
    private val sharedMapText = mutableStateOf<String?>(null)
    private val startupBackup = mutableStateOf<FoundBackup?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        startupBackup.value = BackupManager.detectStartupBackup(this)
        GeoController.sync(this)
        Scheduler.reconcile(this)
        handleIntent(intent)
        setContent {
            val data by Store.data.collectAsState()
            val dark = isDarkThemeActive(data.settings.themeMode)
            var showWelcomeRestore by rememberSaveable { mutableStateOf(false) }
            var openPermsAfterRestore by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Silently refresh monthly internet prayer cache when needed (works offline all month once synced)
            LaunchedEffect(
                data.settings.useInternetPrayers,
                data.settings.lat,
                data.settings.lon,
                data.settings.method,
                data.settings.effectiveFiqh,
            ) {
                val s = data.settings
                if (s.useInternetPrayers && s.lat != null && s.lon != null && !OnlinePrayerSync.isSyncedForToday(s)) {
                    if (OnlinePrayerSync.sync(this@MainActivity, s)) {
                        Scheduler.reconcile(this@MainActivity)
                    }
                }
            }
            SalahTheme(themeMode = data.settings.themeMode, accentHex = data.settings.accentHex) {
                val foundOnStart = startupBackup.value
                when {
                    foundOnStart != null -> {
                        // Show clean "Backup found" window immediately on reinstall
                        WelcomeScreen(
                            onOpenRestoreBackup = { showWelcomeRestore = true },
                            onContinue = {
                                BackupManager.markDeviceSetupDone(this)
                                Store.setSettings(this) { it.copy(onboarded = true) }
                            },
                        )
                        BackupFoundDialog(
                            found = foundOnStart,
                            isStartupPrompt = true,
                            onRestore = {
                                Store.restoreFullBackup(this, foundOnStart.data)
                                BackupManager.markDeviceSetupDone(this)
                                startupBackup.value = null
                                if (!Perms.read(this).essentialsOk) {
                                    openPermsAfterRestore = true
                                }
                                Toast.makeText(this, R.string.backup_restored_ok, Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                BackupManager.deleteLocalBackup(this, foundOnStart.sourceUri)
                                Store.resetAll(this)
                                startupBackup.value = null
                                Toast.makeText(this, R.string.backup_deleted_ok, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    !data.settings.onboarded && showWelcomeRestore -> {
                        RestoreBackupScreen(
                            onRestored = {
                                showWelcomeRestore = false
                                if (!Perms.read(this).essentialsOk) {
                                    openPermsAfterRestore = true
                                }
                            },
                            onClose = { showWelcomeRestore = false },
                        )
                    }
                    !data.settings.onboarded -> {
                        WelcomeScreen(
                            onOpenRestoreBackup = { showWelcomeRestore = true },
                            onContinue = {
                                BackupManager.markDeviceSetupDone(this)
                                Store.setSettings(this) { it.copy(onboarded = true) }
                            },
                        )
                    }
                    else -> {
                        App(
                            incoming = incoming.value,
                            onIncomingConsumed = { incoming.value = null },
                            sharedMapText = sharedMapText.value,
                            onSharedMapConsumed = { sharedMapText.value = null },
                            openPermissionsOnce = openPermsAfterRestore,
                            onOpenPermissionsConsumed = { openPermsAfterRestore = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val text = readUriText(this, uri) ?: ""
                val backup = BackupManager.decode(text)
                if (backup != null && text.contains("\"salahmute-backup\"")) {
                    startupBackup.value = FoundBackup(
                        data = backup.first,
                        createdAtMs = backup.second,
                        origin = com.salahmute.app.data.BackupOrigin.PICKED_FILE,
                        sourceUri = uri,
                    )
                } else {
                    incoming.value = text
                }
            }
            Intent.ACTION_SEND -> {
                val rawText = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() },
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() },
                ).distinct().joinToString("\n").trim()
                if (rawText.isEmpty()) return
                if (GoogleMapsLink.looksLikeMapLinkOrCoords(rawText)) {
                    sharedMapText.value = rawText
                } else if (ShareCodec.parse(rawText) != null) {
                    incoming.value = rawText
                } else {
                    sharedMapText.value = rawText
                }
            }
        }
    }
}

private enum class Screen { HOME, EDIT, AUTO_PRAYERS, EDIT_GEO, MAP_PICKER, SETTINGS, SHARE, RESTORE_BACKUP }

private data class MapRequest(
    val returnTo: Screen,
    val initialLat: Double?,
    val initialLon: Double?,
    val initialRadius: Int?,
    val forPrayerSettings: Boolean,
    val keepLabel: String = "",
    val keepMode: RingMode? = null,
)

@Composable
private fun App(
    incoming: String?,
    onIncomingConsumed: () -> Unit,
    sharedMapText: String?,
    onSharedMapConsumed: () -> Unit,
    openPermissionsOnce: Boolean,
    onOpenPermissionsConsumed: () -> Unit,
) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.MENU) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editGeoId by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var importText by remember { mutableStateOf<String?>(null) }
    var mapReq by remember { mutableStateOf<MapRequest?>(null) }
    var pendingGeoDraft by remember { mutableStateOf<GeoDraft?>(null) }
    var resolvingSharedMap by remember { mutableStateOf(false) }

    // After restoring a backup on a fresh reinstall, take user straight to Permissions if needed
    LaunchedEffect(openPermissionsOnce) {
        if (openPermissionsOnce) {
            onOpenPermissionsConsumed()
            settingsPage = SettingsPage.PERMISSIONS
            screen = Screen.SETTINGS
        }
    }

    // Handle Google Maps "Share -> SalahMute" links cleanly (consume AFTER resolving so coroutine isn't cancelled!)
    LaunchedEffect(sharedMapText) {
        val raw = sharedMapText ?: return@LaunchedEffect
        resolvingSharedMap = GoogleMapsLink.parseDirect(raw) == null
        val s = Store.value.settings
        val parsed = try {
            GoogleMapsLink.resolve(raw, biasLat = s.lat, biasLon = s.lon)
        } finally {
            resolvingSharedMap = false
        }
        onSharedMapConsumed()
        if (parsed != null) {
            tab = 1
            editGeoId = null
            pendingGeoDraft = GeoDraft(
                lat = parsed.lat,
                lon = parsed.lon,
                radiusMeters = 100,
                suggestedLabel = parsed.label,
                mode = null,
            )
            screen = Screen.EDIT_GEO
        } else {
            Toast.makeText(ctx, R.string.link_not_found, Toast.LENGTH_LONG).show()
        }
    }

    // Files opened from outside the app arrive here.
    if (incoming != null) {
        importText = incoming
        onIncomingConsumed()
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = when (screen) {
            Screen.MAP_PICKER -> mapReq?.returnTo ?: Screen.HOME
            Screen.SHARE, Screen.RESTORE_BACKUP -> Screen.SETTINGS
            else -> Screen.HOME
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            selectedTab = tab,
            onSelectTab = { tab = it },
            selectedDay = day,
            onSelectDay = { day = it },
            onEdit = { editId = it; screen = Screen.EDIT },
            onAdd = { editId = null; screen = Screen.EDIT },
            onEditGeo = {
                editGeoId = it
                pendingGeoDraft = null
                screen = Screen.EDIT_GEO
            },
            onAddGeoViaMap = {
                editGeoId = null
                pendingGeoDraft = null
                mapReq = MapRequest(
                    returnTo = Screen.HOME,
                    initialLat = null,
                    initialLon = null,
                    initialRadius = 100,
                    forPrayerSettings = false,
                )
                screen = Screen.MAP_PICKER
            },
            onSettings = {
                settingsPage = SettingsPage.MENU
                screen = Screen.SETTINGS
            },
            onQuickSetup = {
                settingsPage = SettingsPage.PERMISSIONS
                screen = Screen.SETTINGS
            },
            onOpenAutoPrayers = { screen = Screen.AUTO_PRAYERS },
        )
        Screen.EDIT -> SlotEditorScreen(editId, day) { screen = Screen.HOME }
        Screen.AUTO_PRAYERS -> AutoPrayersScreen(
            onPickCity = {
                val s = Store.value.settings
                mapReq = MapRequest(
                    returnTo = Screen.AUTO_PRAYERS,
                    initialLat = s.lat,
                    initialLon = s.lon,
                    initialRadius = null,
                    forPrayerSettings = true,
                )
                screen = Screen.MAP_PICKER
            },
            onClose = { screen = Screen.HOME },
        )
        Screen.EDIT_GEO -> GeoZoneEditorScreen(
            zoneId = editGeoId,
            mapSelection = pendingGeoDraft,
            onMapSelectionConsumed = { pendingGeoDraft = null },
            onOpenMap = { lat, lon, radius, curLabel, curMode ->
                mapReq = MapRequest(
                    returnTo = Screen.EDIT_GEO,
                    initialLat = lat,
                    initialLon = lon,
                    initialRadius = radius,
                    forPrayerSettings = false,
                    keepLabel = curLabel,
                    keepMode = curMode,
                )
                screen = Screen.MAP_PICKER
            },
            onClose = { screen = Screen.HOME },
        )
        Screen.MAP_PICKER -> {
            val req = mapReq ?: MapRequest(Screen.HOME, null, null, 100, false)
            MapPickerScreen(
                initialLat = req.initialLat,
                initialLon = req.initialLon,
                initialRadius = req.initialRadius,
                onCancel = { screen = req.returnTo },
                onConfirm = { lat, lon, radius, label ->
                    if (req.forPrayerSettings) {
                        Store.setSettings(ctx) {
                            it.copy(lat = lat, lon = lon, locationName = label.orEmpty())
                        }
                        if (req.returnTo == Screen.SETTINGS) settingsPage = SettingsPage.PRAYERS
                        screen = req.returnTo
                    } else {
                        pendingGeoDraft = GeoDraft(
                            lat = lat,
                            lon = lon,
                            radiusMeters = radius ?: 100,
                            suggestedLabel = req.keepLabel.ifBlank { label.orEmpty() }.ifBlank { null },
                            mode = req.keepMode,
                        )
                        screen = Screen.EDIT_GEO
                    }
                },
            )
        }
        Screen.SETTINGS -> SettingsScreen(
            page = settingsPage,
            onPageChange = { settingsPage = it },
            onOpenShare = { screen = Screen.SHARE },
            onOpenRestoreBackup = { screen = Screen.RESTORE_BACKUP },
            onPickPrayerLocationOnMap = { lat, lon ->
                mapReq = MapRequest(
                    returnTo = Screen.SETTINGS,
                    initialLat = lat,
                    initialLon = lon,
                    initialRadius = null,
                    forPrayerSettings = true,
                )
                screen = Screen.MAP_PICKER
            },
            onClose = { screen = Screen.HOME },
        )
        Screen.SHARE -> ShareScreen(onClose = { screen = Screen.SETTINGS }, onImportText = { importText = it })
        Screen.RESTORE_BACKUP -> RestoreBackupScreen(
            onRestored = { screen = Screen.HOME },
            onClose = { screen = Screen.SETTINGS },
        )
    }

    if (resolvingSharedMap) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(stringResource(R.string.resolving_link))
                }
            },
            confirmButton = {},
        )
    }

    importText?.let { text ->
        val bundle: ShareBundle? = remember(text) { ShareCodec.parse(text) }
        if (bundle == null) {
            Toast.makeText(ctx, R.string.import_bad, Toast.LENGTH_LONG).show()
            importText = null
        } else {
            ImportDialog(bundle) { importText = null }
        }
    }
}
