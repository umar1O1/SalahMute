package com.salahmute.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine
import com.salahmute.app.map.CityCatalog
import com.salahmute.app.map.PlaceResult
import com.salahmute.app.map.PlaceSearch
import com.salahmute.app.model.CalcMethod
import com.salahmute.app.model.Fiqh
import com.salahmute.app.model.Prayer
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.Perms
import com.salahmute.app.system.Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val TOTAL_STEPS = 7
private val QUICK_DURATION_PRESETS = listOf(0 to 15, 0 to 20, 5 to 25)

private fun startSettingsSafe(ctx: Context, intent: Intent) {
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

/**
 * 7-step first-launch onboarding:
 *   Step 0: Language (search + auto-select & scroll to system language)
 *   Step 1: Theme & Accent (live preview)
 *   Step 2: Card Layout (Icons only vs Icons & text with live slot card previews)
 *   Step 3: Country / City (instant offline + worldwide search + My location)
 *   Step 4: Prayer School (Sunni Hanafi / Sunni Shafi / Shia Jafari option cards)
 *   Step 5: Quick Setup (Required permissions — blocks advancing without DND, Exact Alarms & Battery!)
 *   Step 6: Auto Prayer Times (Sync from internet vs Offline, live preview, Enable or Skip)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(
    onOpenRestoreBackup: () -> Unit,
    onContinue: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val data by Store.data.collectAsState()
    val settings = data.settings
    val cacheVer by OnlinePrayerSync.version.collectAsState()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf(Perms.read(ctx)) }
    var showWhyDialog by remember { mutableStateOf(false) }

    // Step 0 (Language)
    var langQuery by rememberSaveable { mutableStateOf("") }
    val appTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-').let {
        if (it == "in") "id" else it
    }
    val sysTag = remember {
        val raw = Locale.getDefault().language.let { if (it == "in") "id" else it }
        if (APP_LANGUAGES.any { it.tag == raw }) raw else "en"
    }
    val activeLangTag = appTag.ifBlank { sysTag }
    val langListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val idx = APP_LANGUAGES.indexOfFirst { it.tag == activeLangTag }
        if (idx > 0) langListState.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    // Step 3 (Country / City)
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var onlineCities by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searchingCity by remember { mutableStateOf(false) }
    var locatingCity by remember { mutableStateOf(false) }

    LaunchedEffect(cityQuery, step) {
        if (step != 3) return@LaunchedEffect
        val q = cityQuery.trim()
        if (q.length < 2) {
            onlineCities = emptyList()
            searchingCity = false
            return@LaunchedEffect
        }
        searchingCity = true
        delay(360)
        onlineCities = PlaceSearch.search(q, 24.86, 67.01)
        searchingCity = false
    }

    val cityGpsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) {
            Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        locatingCity = true
        GeoController.fetchCurrentLocation(ctx) { loc ->
            if (loc == null) {
                locatingCity = false
                Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
                return@fetchCurrentLocation
            }
            scope.launch {
                val label = PlaceSearch.reverseGeocode(loc.latitude, loc.longitude)
                    ?: "%.2f, %.2f".format(Locale.US, loc.latitude, loc.longitude)
                val suggested = CityCatalog.suggestMethodFor(label)
                Store.setSettings(ctx) { s ->
                    s.copy(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        locationName = label,
                        method = if (s.effectiveFiqh == Fiqh.SHIA_JAFARI) CalcMethod.JAFARI else suggested,
                    )
                }
                locatingCity = false
            }
        }
    }

    // Step 4 (Permissions)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = Perms.read(ctx)
        Scheduler.reconcile(ctx)
    }
    BackHandler(enabled = step > 0) { step -= 1 }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = Perms.read(ctx)
    }

    // Step 6 (Auto Prayer Times — Internet vs Offline)
    var useInternet by rememberSaveable { mutableStateOf(true) }
    var syncingInternet by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<Boolean?>(null) } // true=synced, false=failed, null=idle
    var beforeMin by rememberSaveable { mutableIntStateOf(0) }
    var afterMin by rememberSaveable { mutableIntStateOf(20) }
    var prayerMode by rememberSaveable { mutableStateOf(RingMode.VIBRATE) }

    val hasCity = settings.lat != null && settings.lon != null
    LaunchedEffect(step, useInternet, settings.lat, settings.lon, settings.method, settings.effectiveFiqh) {
        if (step == 6 && useInternet && hasCity) {
            if (settings.useInternetPrayers != true) {
                Store.setSettings(ctx) { it.copy(useInternetPrayers = true) }
            }
            syncingInternet = true
            syncStatus = OnlinePrayerSync.sync(ctx, Store.value.settings.copy(useInternetPrayers = true))
            syncingInternet = false
        } else if (step == 6 && !useInternet && settings.useInternetPrayers) {
            Store.setSettings(ctx) { it.copy(useInternetPrayers = false) }
            syncStatus = null
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Top bar: Back | Progress pills | Skip (ONLY on step 2 City if user wants custom-only schedules)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterStart) {
                    if (step > 0) {
                        IconButton(onClick = { step -= 1 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(TOTAL_STEPS) { i ->
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (i == step) 22.dp else 7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                    if (step == 3) {
                        TextButton(onClick = { step = 4 }) {
                            Text(
                                stringResource(R.string.skip),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            when (step) {
                // Step 0: Language selector with search + auto-select & auto-scroll to system language
                0 -> {
                    val filtered = remember(langQuery) {
                        val q = langQuery.trim().lowercase()
                        if (q.isEmpty()) APP_LANGUAGES
                        else APP_LANGUAGES.filter {
                            it.nativeName.lowercase().contains(q) ||
                                it.englishName.lowercase().contains(q) ||
                                it.tag.lowercase().contains(q)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.onboard_lang_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.onboard_lang_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        OutlinedTextField(
                            value = langQuery,
                            onValueChange = { langQuery = it },
                            placeholder = { Text(stringResource(R.string.search_language_hint)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (langQuery.isNotEmpty()) {
                                    IconButton(onClick = { langQuery = "" }) { Icon(Icons.Filled.Close, null) }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyColumn(
                            state = langListState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.tag }) { lang ->
                                val selected = activeLangTag == lang.tag
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    ),
                                    onClick = {
                                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                lang.nativeName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                            if (lang.nativeName != lang.englishName) {
                                                Text(
                                                    lang.englishName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        }
                                        if (selected) {
                                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 1: Theme & Accent
                1 -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().scale(1.45f),
                            )
                        }
                        Text(
                            stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.welcome_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(Modifier.padding(16.dp)) {
                                ThemePickerSection(
                                    themeMode = settings.themeMode,
                                    accentHex = settings.accentHex,
                                    onThemeModeChange = { m -> Store.setSettings(ctx) { it.copy(themeMode = m) } },
                                    onAccentChange = { hex -> Store.setSettings(ctx) { it.copy(accentHex = hex) } },
                                )
                            }
                        }
                    }
                }

                // Step 2: Card Layout (Icons only vs Icons & text — live slot card previews!)
                2 -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.onboard_card_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.onboard_card_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        CardStyleOptionPreview(
                            title = stringResource(R.string.card_actions_icons),
                            desc = stringResource(R.string.card_actions_icons_desc),
                            compact = true,
                            selected = settings.compactCardActions,
                            onSelect = { Store.setSettings(ctx) { it.copy(compactCardActions = true) } },
                        )
                        CardStyleOptionPreview(
                            title = stringResource(R.string.card_actions_labels),
                            desc = stringResource(R.string.card_actions_labels_desc),
                            compact = false,
                            selected = !settings.compactCardActions,
                            onSelect = { Store.setSettings(ctx) { it.copy(compactCardActions = false) } },
                        )
                    }
                }

                // Step 3: Country / City
                3 -> {
                    val offlineMatches = remember(cityQuery) { CityCatalog.searchOffline(cityQuery) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.onboard_city_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.onboard_city_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )

                        if (hasCity && settings.locationName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        stringResource(R.string.selected_city_label, settings.locationName),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = cityQuery,
                                onValueChange = { cityQuery = it },
                                placeholder = { Text(stringResource(R.string.search_city_hint)) },
                                leadingIcon = { Icon(Icons.Filled.Search, null) },
                                trailingIcon = {
                                    when {
                                        searchingCity -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        cityQuery.isNotEmpty() -> IconButton(onClick = { cityQuery = "" }) { Icon(Icons.Filled.Close, null) }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                enabled = !locatingCity,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                                onClick = {
                                    cityGpsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                },
                            ) {
                                if (locatingCity) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.LocationOn, stringResource(R.string.my_location))
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(offlineMatches, key = { "off_${it.city}_${it.country}" }) { entry ->
                                val selected = settings.locationName == entry.displayName
                                CityRow(
                                    title = entry.city,
                                    subtitle = entry.country,
                                    selected = selected,
                                    onClick = {
                                        Store.setSettings(ctx) { s ->
                                            s.copy(
                                                lat = entry.lat,
                                                lon = entry.lon,
                                                locationName = entry.displayName,
                                                countryName = entry.country,
                                                method = if (s.effectiveFiqh == Fiqh.SHIA_JAFARI) CalcMethod.JAFARI else entry.suggestedMethod,
                                            )
                                        }
                                    },
                                )
                            }
                            if (onlineCities.isNotEmpty()) {
                                items(onlineCities, key = { "on_${it.lat}_${it.lon}_${it.name}" }) { place ->
                                    val fullName = listOf(place.name, place.subtitle).filter { it.isNotBlank() }.joinToString(", ")
                                    val selected = settings.locationName == fullName
                                    CityRow(
                                        title = place.name,
                                        subtitle = place.subtitle,
                                        selected = selected,
                                        onClick = {
                                            val suggested = CityCatalog.suggestMethodFor(fullName)
                                            Store.setSettings(ctx) { s ->
                                                s.copy(
                                                    lat = place.lat,
                                                    lon = place.lon,
                                                    locationName = fullName,
                                                    countryName = place.subtitle.substringAfterLast(",").trim(),
                                                    method = if (s.effectiveFiqh == Fiqh.SHIA_JAFARI) CalcMethod.JAFARI else suggested,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 4: Fiqh / School (Sunni Hanafi, Sunni Shafi/Maliki/Hanbali, Shia Jafari — option buttons!)
                4 -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.fiqh_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.fiqh_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        FiqhOptionCards(
                            selected = settings.effectiveFiqh,
                            onSelect = { chosen ->
                                Store.setSettings(ctx) { s ->
                                    val nextMethod = when {
                                        chosen == Fiqh.SHIA_JAFARI -> CalcMethod.JAFARI
                                        s.method == CalcMethod.JAFARI -> CityCatalog.suggestMethodFor(s.locationName.ifBlank { s.countryName })
                                        else -> s.method
                                    }
                                    s.copy(
                                        fiqh = chosen,
                                        hanafi = chosen == Fiqh.HANAFI,
                                        method = nextMethod,
                                    )
                                }
                            },
                        )
                    }
                }

                // Step 5: Quick Setup (Required Permissions — clean, compact, no half-cut pills, blocks without battery/DND/alarm!)
                5 -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.onboard_perm_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.onboard_perm_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        OnboardPermRow(
                            title = stringResource(R.string.perm_dnd),
                            tag = stringResource(R.string.tag_required),
                            granted = perms.policy,
                            onGrant = { startSettingsSafe(ctx, Perms.dndSettingsIntent(ctx)) },
                        )

                        if (Build.VERSION.SDK_INT >= 31) {
                            OnboardPermRow(
                                title = stringResource(R.string.perm_alarm),
                                tag = stringResource(R.string.tag_required),
                                granted = perms.exactAlarm,
                                onGrant = { startSettingsSafe(ctx, Perms.exactAlarmSettingsIntent(ctx)) },
                            )
                        }

                        OnboardPermRow(
                            title = stringResource(R.string.perm_battery),
                            tag = stringResource(R.string.tag_required),
                            granted = perms.battery,
                            onGrant = { startSettingsSafe(ctx, Perms.batterySettingsIntent(ctx)) },
                        )

                        if (Build.VERSION.SDK_INT >= 33) {
                            OnboardPermRow(
                                title = stringResource(R.string.perm_notif),
                                tag = stringResource(R.string.tag_recommended),
                                granted = perms.notifications,
                                onGrant = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            )
                        }

                        val autostartIntent = remember { Perms.oemAutostartIntent(ctx) }
                        if (autostartIntent != null) {
                            OnboardPermRow(
                                title = stringResource(R.string.perm_autostart),
                                tag = stringResource(R.string.tag_recommended),
                                granted = false,
                                onGrant = { startSettingsSafe(ctx, autostartIntent) },
                            )
                        }

                        // Submenu dialog if user wants to read why each permission is needed
                        TextButton(onClick = { showWhyDialog = true }) {
                            Icon(Icons.Filled.Info, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.why_perms_needed))
                        }
                    }
                }

                // Step 5 (Last screen): Auto Prayer Times (Sync from internet vs Offline)
                else -> {
                    val today = remember { LocalDate.now() }
                    val zone = remember { ZoneId.systemDefault() }
                    val previewSettings = remember(settings, useInternet, cacheVer) {
                        settings.copy(useInternetPrayers = useInternet)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.onboard_prayers_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (hasCity) {
                                stringResource(
                                    R.string.onboard_prayers_sub,
                                    settings.locationName.ifBlank { "%.2f, %.2f".format(Locale.US, settings.lat!!, settings.lon!!) },
                                )
                            } else {
                                stringResource(R.string.prayer_set_city_first)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        if (hasCity) {
                            // Summary pill: City + School
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    "${stringResource(settings.effectiveFiqh.labelRes())} · ${stringResource(settings.method.labelRes())}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }

                            // Internet vs Offline source option cards
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SourceChoiceCard(
                                    title = stringResource(R.string.prayer_source_internet),
                                    desc = stringResource(R.string.prayer_source_internet_desc),
                                    selected = useInternet,
                                    onClick = { useInternet = true },
                                )
                                SourceChoiceCard(
                                    title = stringResource(R.string.prayer_source_offline),
                                    desc = stringResource(R.string.prayer_source_offline_desc),
                                    selected = !useInternet,
                                    onClick = { useInternet = false },
                                )
                            }

                            if (useInternet) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (syncingInternet) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Text(
                                            stringResource(R.string.prayer_syncing),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (syncStatus == true || OnlinePrayerSync.isSyncedForToday(previewSettings)) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            stringResource(R.string.prayer_synced_ok),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (syncStatus == false) {
                                        Text(
                                            stringResource(R.string.prayer_sync_failed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    syncingInternet = true
                                                    syncStatus = OnlinePrayerSync.sync(ctx, previewSettings)
                                                    syncingInternet = false
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Filled.Refresh, null, Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.prayer_sync_now))
                                        }
                                    }
                                }
                            }

                            // Compact Today preview card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Prayer.entries.forEachIndexed { idx, p ->
                                        val range = Engine.slotRange(
                                            Slot(prayer = p, before = beforeMin, after = afterMin),
                                            today,
                                            previewSettings,
                                            zone,
                                        )
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(p.labelRes()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                if (range != null) {
                                                    "${formatMinutes(range.first, locale)} – ${formatMinutes(range.second, locale)}"
                                                } else {
                                                    "—"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        if (idx < Prayer.entries.lastIndex) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        }
                                    }
                                }
                            }

                            // Quick duration + mode chips
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QUICK_DURATION_PRESETS.forEach { (b, a) ->
                                    FilterChip(
                                        selected = beforeMin == b && afterMin == a,
                                        onClick = { beforeMin = b; afterMin = a },
                                        label = { Text(if (b == 0) "+${a}m" else "−${b}m / +${a}m") },
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RingMode.entries.forEach { m ->
                                    FilterChip(
                                        selected = prayerMode == m,
                                        onClick = { prayerMode = m },
                                        label = { Text(stringResource(m.labelRes())) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Step 5 required permissions gate helper
            if (step == 5 && !perms.essentialsOk) {
                Text(
                    stringResource(R.string.perms_required_gate, perms.coreGrantedCount, 3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            // Primary bottom CTA
            when (step) {
                5 -> {
                    Button(
                        enabled = perms.essentialsOk,
                        onClick = { step = 6 },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.next_step), style = MaterialTheme.typography.titleMedium)
                    }
                }
                6 -> {
                    if (hasCity) {
                        Button(
                            onClick = {
                                Store.setSettings(ctx) { it.copy(useInternetPrayers = useInternet) }
                                val slots = Prayer.entries.map { p ->
                                    Slot(
                                        label = ctx.getString(p.labelRes()),
                                        mode = prayerMode,
                                        days = Slot.ALL_DAYS,
                                        prayer = p,
                                        before = beforeMin,
                                        after = afterMin,
                                    )
                                }
                                Store.setPrayerSlots(ctx, slots)
                                onContinue()
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                stringResource(R.string.enable_prayers_cta, Prayer.entries.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.skip_manual_setup))
                        }
                    } else {
                        Button(
                            onClick = onContinue,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(stringResource(R.string.start_app), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { step += 1 },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.next_step), style = MaterialTheme.typography.titleMedium)
                    }
                    if (step == 0) {
                        TextButton(
                            onClick = onOpenRestoreBackup,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.restore_backup))
                        }
                    }
                }
            }
        }
    }

    if (showWhyDialog) {
        AlertDialog(
            onDismissRequest = { showWhyDialog = false },
            title = { Text(stringResource(R.string.why_perms_needed), fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WhyPermItem(stringResource(R.string.perm_dnd), stringResource(R.string.onboard_perm_dnd_why))
                    WhyPermItem(stringResource(R.string.perm_alarm), stringResource(R.string.onboard_perm_alarm_why))
                    WhyPermItem(stringResource(R.string.perm_battery), stringResource(R.string.perm_battery_desc))
                    WhyPermItem(stringResource(R.string.perm_notif), stringResource(R.string.onboard_perm_notif_why))
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhyDialog = false }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun CityRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Compact permission row: clean full-width pill below title (never half-clipped!), no verbose body clutter. */
@Composable
private fun OnboardPermRow(
    title: String,
    tag: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (granted) 1.5.dp else 1.dp,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (granted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (granted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, stringResource(R.string.granted), tint = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.grant), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun WhyPermItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceChoiceCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CardStyleOptionPreview(
    title: String,
    desc: String,
    compact: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val locale = currentLocale()
    val sampleRange = remember(locale) {
        "${formatMinutes(12 * 60 + 25.0, locale)} – ${formatMinutes(12 * 60 + 45.0, locale)}"
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(24.dp),
                ) {
                    if (selected) {
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

            // Realistic live preview of how the schedule card looks in this style
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        Modifier.padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                sampleRange,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.prayer_dhuhr),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                ModePill(RingMode.VIBRATE)
                            }
                        }
                        Switch(checked = true, onCheckedChange = null)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    if (compact) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            Icon(painterResource(R.drawable.ic_copy), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            Icon(painterResource(R.drawable.ic_archive), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                        }
                    } else {
                        val divColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PreviewActionLabel(stringResource(R.string.edit), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) {
                                Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            Box(Modifier.width(1.dp).height(16.dp).background(divColor))
                            PreviewActionLabel(stringResource(R.string.copy_action), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) {
                                Icon(painterResource(R.drawable.ic_copy), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            Box(Modifier.width(1.dp).height(16.dp).background(divColor))
                            PreviewActionLabel(stringResource(R.string.archive), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) {
                                Icon(painterResource(R.drawable.ic_archive), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            Box(Modifier.width(1.dp).height(16.dp).background(divColor))
                            PreviewActionLabel(stringResource(R.string.delete), MaterialTheme.colorScheme.error, Modifier.weight(1f)) {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewActionLabel(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
