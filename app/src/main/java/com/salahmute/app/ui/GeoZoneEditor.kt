package com.salahmute.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.RingMode
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.Perms
import com.salahmute.app.system.Scheduler
import com.salahmute.app.system.StateStore
import java.util.Locale
import java.util.UUID

private val RADIUS_PRESETS = listOf(50, 100, 200, 500)

data class GeoDraft(
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int,
    val suggestedLabel: String?,
    val mode: RingMode? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoZoneEditorScreen(
    zoneId: String?,
    mapSelection: GeoDraft?,
    onMapSelectionConsumed: () -> Unit,
    onOpenMap: (lat: Double?, lon: Double?, radius: Int, currentLabel: String, currentMode: RingMode) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val data by Store.data.collectAsState()
    val existing = remember(zoneId) { data.geoZones.firstOrNull { it.id == zoneId } }

    var label by rememberSaveable(zoneId) { mutableStateOf(existing?.label ?: "") }
    var latText by rememberSaveable(zoneId) { mutableStateOf(existing?.let { "%.6f".format(Locale.US, it.lat) } ?: "") }
    var lonText by rememberSaveable(zoneId) { mutableStateOf(existing?.let { "%.6f".format(Locale.US, it.lon) } ?: "") }
    var radiusText by rememberSaveable(zoneId) { mutableStateOf((existing?.radiusMeters ?: 100).toString()) }
    var mode by rememberSaveable(zoneId) { mutableStateOf(existing?.mode ?: RingMode.VIBRATE) }
    var showGoogleMapsSheet by remember { mutableStateOf(false) }
    var fetchingLoc by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 0 = closed, 1 = ask in-app location + explain privacy, 2 = ask background ("Allow all the time")
    var locPermDialogStep by remember { mutableStateOf(0) }

    val finishFineLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val nowPerms = Perms.read(ctx)
        GeoController.sync(ctx)
        Scheduler.reconcile(ctx)
        if (nowPerms.fineLocation && Build.VERSION.SDK_INT >= 29 && !nowPerms.backgroundLocation && !StateStore(ctx).bgLocPromptDismissed) {
            locPermDialogStep = 2
        } else {
            locPermDialogStep = 0
            onClose()
        }
    }

    val finishBgLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val nowPerms = Perms.read(ctx)
        GeoController.sync(ctx)
        Scheduler.reconcile(ctx)
        if (nowPerms.backgroundLocation) {
            StateStore(ctx).bgLocPromptDismissed = false
            Toast.makeText(ctx, R.string.loc_bg_ready_toast, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, R.string.loc_in_app_saved_toast, Toast.LENGTH_LONG).show()
        }
        locPermDialogStep = 0
        onClose()
    }

    LaunchedEffect(mapSelection) {
        if (mapSelection != null) {
            latText = "%.6f".format(Locale.US, mapSelection.lat)
            lonText = "%.6f".format(Locale.US, mapSelection.lon)
            radiusText = mapSelection.radiusMeters.toString()
            if (!mapSelection.suggestedLabel.isNullOrBlank()) {
                label = mapSelection.suggestedLabel.take(40)
            }
            if (mapSelection.mode != null) {
                mode = mapSelection.mode
            }
            error = null
            onMapSelectionConsumed()
        }
    }

    val errCoords = stringResource(R.string.err_bad_coords)
    val errRadius = stringResource(R.string.err_bad_radius)

    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        fetchingLoc = true
        GeoController.fetchCurrentLocation(ctx) { loc ->
            fetchingLoc = false
            if (loc != null) {
                latText = "%.6f".format(Locale.US, loc.latitude)
                lonText = "%.6f".format(Locale.US, loc.longitude)
                error = null
            } else {
                Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    val parsedLat = latText.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val parsedLon = lonText.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val parsedRadius = radiusText.toIntOrNull()?.coerceIn(30, 5000) ?: 100

    fun saveLocation() {
        val lat = parsedLat
        val lon = parsedLon
        val rad = radiusText.toIntOrNull()
        when {
            lat == null || lon == null -> error = errCoords
            rad == null || rad !in 30..5000 -> error = errRadius
            else -> {
                val savedEnabled = existing?.enabled ?: true
                if (!data.geoEnabled && savedEnabled) Store.setGeoEnabled(ctx, true)
                Store.upsertGeoZone(
                    ctx,
                    GeoZone(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        label = label.trim(),
                        lat = lat,
                        lon = lon,
                        radiusMeters = rad,
                        mode = mode,
                        enabled = savedEnabled,
                    ),
                )
                val nowPerms = Perms.read(ctx)
                when {
                    !savedEnabled -> onClose()
                    !nowPerms.fineLocation -> locPermDialogStep = 1
                    Build.VERSION.SDK_INT >= 29 && !nowPerms.backgroundLocation && !StateStore(ctx).bgLocPromptDismissed ->
                        locPermDialogStep = 2
                    else -> onClose()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.add_location else R.string.edit_location), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Pinned Save button — always immediately visible without scrolling!
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        onClick = { saveLocation() },
                    ) {
                        Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Compact Place Card: Place summary + single 3-action row (Map | My location | Google Maps)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (parsedLat != null && parsedLon != null) {
                                    label.ifBlank { stringResource(R.string.location_selected) }
                                } else {
                                    stringResource(R.string.drag_map_hint)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.selected_place),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { onOpenMap(parsedLat, parsedLon, parsedRadius, label, mode) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.map_style_standard), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            enabled = !fetchingLoc,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            onClick = {
                                locLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (fetchingLoc) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.my_location), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = { showGoogleMapsSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.2f),
                        ) {
                            Text(stringResource(R.string.google_maps_btn), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Name field
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text(stringResource(R.string.label)) },
                placeholder = { Text(stringResource(R.string.location_label_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Compact Radius Card (slider + 4 single-row presets)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.silence_radius),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.radius_fmt, parsedRadius),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    RadiusSlider(
                        radiusMeters = parsedRadius,
                        onRadiusChange = {
                            radiusText = it.toString()
                            error = null
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RADIUS_PRESETS.forEach { r ->
                            val selected = parsedRadius == r
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                onClick = {
                                    radiusText = r.toString()
                                    error = null
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.radius_fmt, r),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Compact Mode Card (single equal-width row, zero extra clutter)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RingMode.entries.forEach { m ->
                            val selected = mode == m
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                onClick = { mode = m },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(m.labelRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (locPermDialogStep > 0) {
        val isBgStep = locPermDialogStep == 2
        AlertDialog(
            onDismissRequest = {
                locPermDialogStep = 0
                onClose()
            },
            icon = {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isBgStep) Icons.Filled.LocationOn else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            title = {
                Text(
                    stringResource(if (isBgStep) R.string.loc_perm_bg_title else R.string.loc_perm_dialog_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isBgStep) {
                        Text(
                            stringResource(R.string.loc_perm_bg_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Visual guide showing the exact radio option to tap on the next Android Settings screen
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onPrimary),
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.loc_allow_all_time_opt),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = androidx.compose.ui.graphics.Color.Transparent,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(16.dp),
                                    ) {}
                                    Text(
                                        stringResource(R.string.loc_allow_while_using_opt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.loc_perm_why_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.loc_perm_why_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.loc_perm_privacy_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.loc_perm_privacy_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        if (isBgStep) {
                            finishBgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            finishFineLocLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                ) {
                    Text(stringResource(if (isBgStep) R.string.loc_allow_all_time_opt else R.string.allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isBgStep) {
                            StateStore(ctx).bgLocPromptDismissed = true
                            Toast.makeText(ctx, R.string.loc_in_app_saved_toast, Toast.LENGTH_LONG).show()
                        }
                        locPermDialogStep = 0
                        onClose()
                    },
                ) {
                    Text(stringResource(if (isBgStep) R.string.loc_save_in_app_only else R.string.not_now))
                }
            },
        )
    }

    if (showGoogleMapsSheet) {
        GoogleMapsPickerSheet(
            centerLat = parsedLat,
            centerLon = parsedLon,
            onDismiss = { showGoogleMapsSheet = false },
            onLocationResolved = { parsed ->
                showGoogleMapsSheet = false
                latText = "%.6f".format(Locale.US, parsed.lat)
                lonText = "%.6f".format(Locale.US, parsed.lon)
                if (!parsed.label.isNullOrBlank()) {
                    label = parsed.label.take(40)
                }
                error = null
            },
        )
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            stringResource(R.string.delete_confirm, existing.label.ifBlank { stringResource(R.string.location) }),
            { confirmDelete = false },
        ) {
            Store.deleteGeoZone(ctx, existing.id)
            confirmDelete = false
            onClose()
        }
    }
}
