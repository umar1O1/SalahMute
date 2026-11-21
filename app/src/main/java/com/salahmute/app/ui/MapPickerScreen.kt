package com.salahmute.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.salahmute.app.BuildConfig
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.map.GoogleMapsLink
import com.salahmute.app.map.ParsedMapLocation
import com.salahmute.app.map.PlaceResult
import com.salahmute.app.map.PlaceSearch
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.Perms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.cos

private val MAP_RADIUS_PRESETS = listOf(50, 100, 200, 500)

private enum class MapStyle { DETAILED, SATELLITE }

/** Aerial satellite imagery (ArcGIS World Imagery, {z}/{y}/{x} order). */
private val ARCGIS_SATELLITE: ITileSource = object : OnlineTileSourceBase(
    "ArcGISWorldImagery",
    0,
    19,
    256,
    ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
}

private fun applyMapStyle(mv: MapView, style: MapStyle) {
    mv.isTilesScaledToDpi = true
    when (style) {
        MapStyle.DETAILED -> mv.setTileSource(TileSourceFactory.MAPNIK)
        MapStyle.SATELLITE -> mv.setTileSource(ARCGIS_SATELLITE)
    }
    mv.invalidate()
}

/** Lightweight, crash-proof Canvas circle overlay anchored at the map center pin. */
private class RadiusCircleOverlay(primaryArgb: Int, initialMeters: Float) : Overlay() {
    @Volatile
    var radiusMeters: Float = initialMeters
    private val screenPt = Point()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb(
            48,
            AndroidColor.red(primaryArgb),
            AndroidColor.green(primaryArgb),
            AndroidColor.blue(primaryArgb),
        )
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = AndroidColor.argb(
            225,
            AndroidColor.red(primaryArgb),
            AndroidColor.green(primaryArgb),
            AndroidColor.blue(primaryArgb),
        )
    }

    override fun draw(canvas: Canvas, pj: Projection) {
        val center = pj.currentCenter ?: return
        pj.toPixels(center, screenPt)
        val cosLat = cos(Math.toRadians(center.latitude.coerceIn(-85.0, 85.0))).coerceAtLeast(0.05)
        val rPx = (pj.metersToEquatorPixels(radiusMeters) / cosLat).toFloat()
        if (rPx.isFinite() && rPx > 1f) {
            canvas.drawCircle(screenPt.x.toFloat(), screenPt.y.toFloat(), rPx, fillPaint)
            canvas.drawCircle(screenPt.x.toFloat(), screenPt.y.toFloat(), rPx, strokePaint)
        }
    }
}

/** Classic Google-Maps-style blue "You are here" dot overlay with translucent halo & white ring. */
private class UserLocationDotOverlay(density: Float) : Overlay() {
    @Volatile
    var point: GeoPoint? = null
    private val screenPt = Point()
    private val haloR = 16f * density
    private val ringR = 8.5f * density
    private val dotR = 6f * density
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb(56, 66, 133, 244) // Google Blue #4285F4
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.WHITE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.rgb(66, 133, 244)
    }

    override fun draw(canvas: Canvas, pj: Projection) {
        val pt = point ?: return
        pj.toPixels(pt, screenPt)
        val x = screenPt.x.toFloat()
        val y = screenPt.y.toFloat()
        canvas.drawCircle(x, y, haloR, haloPaint)
        canvas.drawCircle(x, y, ringR, ringPaint)
        canvas.drawCircle(x, y, dotR, dotPaint)
    }
}

/**
 * Google-Maps-style interactive map picker:
 * - Clean high-DPI Retina road basemap (Voyager @2x) + 1-tap Satellite & Detailed layer switcher
 * - Automatically starts at the user's current location with a blue "You are here" dot
 * - Floating top search bar with live autocomplete biased to the current map center
 * - Fixed Google-red center pin with smooth 60fps translucent radius circle overlay
 * - Bottom confirmation sheet with reverse-geocoded place name & radius controls
 */
@Composable
fun MapPickerScreen(
    initialLat: Double?,
    initialLon: Double?,
    initialRadius: Int?,
    onCancel: () -> Unit,
    onConfirm: (lat: Double, lon: Double, radiusMeters: Int?, suggestedLabel: String?) -> Unit,
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = ctx.resources.displayMetrics.density

    // Configure osmdroid to store tiles in private cacheDir (no storage permission needed)
    remember {
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
        cfg.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        true
    }

    val initialUserLoc = remember {
        if (Perms.read(ctx).fineLocation) GeoController.bestLastKnownLocation(ctx) else null
    }

    val startPoint = remember(initialLat, initialLon) {
        when {
            initialLat != null && initialLon != null -> Triple(initialLat, initialLon, 17.0)
            initialUserLoc != null -> Triple(initialUserLoc.latitude, initialUserLoc.longitude, 17.0)
            Store.value.settings.lat != null && Store.value.settings.lon != null ->
                Triple(Store.value.settings.lat!!, Store.value.settings.lon!!, 14.0)
            else -> Triple(21.4225, 39.8262, 15.0)
        }
    }

    var mapStyle by rememberSaveable { mutableStateOf(MapStyle.DETAILED) }
    var centerLat by remember { mutableDoubleStateOf(startPoint.first) }
    var centerLon by remember { mutableDoubleStateOf(startPoint.second) }
    var radius by remember { mutableIntStateOf((initialRadius ?: 100).coerceIn(50, 1000)) }
    var isDragging by remember { mutableStateOf(false) }
    var showGoogleMapsSheet by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searchAttempted by remember { mutableStateOf(false) }

    var addressLabel by remember { mutableStateOf<String?>(null) }
    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var circleOverlay by remember { mutableStateOf<RadiusCircleOverlay?>(null) }
    var userDotOverlay by remember { mutableStateOf<UserLocationDotOverlay?>(null) }

    fun moveMapTo(lat: Double, lon: Double, zoom: Double = 17.0) {
        centerLat = lat
        centerLon = lon
        mapViewRef?.let { mv ->
            mv.controller.animateTo(GeoPoint(lat, lon), zoom, 450L)
            mv.invalidate()
        }
    }

    fun fetchAndCenter(showErrorToast: Boolean) {
        locating = true
        val targetZoom = if (initialRadius != null) 17.5 else 13.5
        GeoController.fetchCurrentLocation(ctx) { loc ->
            locating = false
            if (loc != null) {
                userDotOverlay?.point = GeoPoint(loc.latitude, loc.longitude)
                selectedPlaceName = null
                moveMapTo(loc.latitude, loc.longitude, targetZoom)
            } else if (showErrorToast) {
                Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Silent auto-locate launcher on initial open (no error toast if user declines)
    val autoLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchAndCenter(showErrorToast = false)
        }
    }

    // Explicit "My location" button launcher (foreground in-app permission only)
    val myLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        fetchAndCenter(showErrorToast = true)
    }

    // Automatically jump straight to current location when adding a new location (no initial coords)
    LaunchedEffect(initialLat, initialLon) {
        if (initialLat == null && initialLon == null) {
            if (Perms.read(ctx).fineLocation) {
                fetchAndCenter(showErrorToast = false)
            } else {
                autoLocLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }

    // Debounced autocomplete search biased to current map center (also auto-resolves pasted Google Maps links!)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searching = false
            searchAttempted = false
            return@LaunchedEffect
        }
        searching = true
        delay(360)
        if (GoogleMapsLink.looksLikeMapLinkOrCoords(q)) {
            val parsed = GoogleMapsLink.resolve(q, biasLat = centerLat, biasLon = centerLon)
            searching = false
            if (parsed != null) {
                focusManager.clearFocus()
                query = ""
                results = emptyList()
                searchAttempted = false
                selectedPlaceName = parsed.label
                if (!parsed.label.isNullOrBlank()) addressLabel = parsed.label
                moveMapTo(parsed.lat, parsed.lon, if (initialRadius != null) 17.5 else 13.5)
            } else {
                results = emptyList()
                searchAttempted = true
            }
            return@LaunchedEffect
        }
        results = PlaceSearch.search(q, centerLat, centerLon)
        searching = false
        searchAttempted = true
    }

    // Debounced reverse geocoding when the map stops moving
    LaunchedEffect(centerLat, centerLon) {
        isDragging = true
        delay(220)
        isDragging = false
        delay(500)
        val rev = PlaceSearch.reverseGeocode(centerLat, centerLon)
        if (!rev.isNullOrBlank()) {
            addressLabel = rev
        }
    }

    // Smoothly redraw the circle overlay whenever radius changes
    LaunchedEffect(radius, circleOverlay, mapViewRef) {
        circleOverlay?.radiusMeters = radius.toFloat()
        mapViewRef?.invalidate()
    }

    // Apply map layer changes (Map / Satellite / Detailed)
    LaunchedEffect(mapStyle, mapViewRef) {
        mapViewRef?.let { applyMapStyle(it, mapStyle) }
    }

    // Lifecycle resume/pause ONLY (AndroidView onRelease handles onDetach!)
    val currentMv = mapViewRef
    DisposableEffect(lifecycleOwner, currentMv) {
        if (currentMv == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentMv.onResume()
                Lifecycle.Event.ON_PAUSE -> currentMv.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pinLift by animateDpAsState(targetValue = if (isDragging) (-10).dp else 0.dp, label = "pinLift")
    val circleColorArgb = Color(0xFF4285F4).toArgb() // Classic Google Maps blue radius circle

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top interactive map area: geometric center == visual center == pin tip
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        applyMapStyle(this, mapStyle)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        minZoomLevel = 3.0
                        maxZoomLevel = 20.0
                        val startGeo = GeoPoint(startPoint.first, startPoint.second)
                        controller.setZoom(startPoint.third)
                        controller.setCenter(startGeo)

                        if (initialRadius != null) {
                            val overlay = RadiusCircleOverlay(circleColorArgb, radius.toFloat())
                            overlays.add(overlay)
                            circleOverlay = overlay
                        }

                        val dot = UserLocationDotOverlay(density).apply {
                            if (initialUserLoc != null) {
                                point = GeoPoint(initialUserLoc.latitude, initialUserLoc.longitude)
                            }
                        }
                        overlays.add(dot)
                        userDotOverlay = dot

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val c = mapCenter
                                centerLat = c.latitude
                                centerLon = c.longitude
                                selectedPlaceName = null
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                val c = mapCenter
                                centerLat = c.latitude
                                centerLon = c.longitude
                                return false
                            }
                        })
                        onResume()
                        mapViewRef = this
                    }
                },
                onRelease = { mv ->
                    mv.onPause()
                    mv.onDetach()
                },
            )

            // Google-Maps-style red center pin with ground shadow
            Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = if (isDragging) 14.dp else 10.dp, height = if (isDragging) 6.dp else 5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFEA4335),
                    modifier = Modifier
                        .size(48.dp)
                        .offset(y = (-23).dp + pinLift),
                )
            }

            // Floating search bar + layer pills + autocomplete dropdown
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.search_map_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            )
                        }
                        if (searching) {
                            CircularProgressIndicator(
                                Modifier.padding(horizontal = 12.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                results = emptyList()
                                searchAttempted = false
                            }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                            }
                        }
                    }
                }

                val showDropdown = results.isNotEmpty() || (searchAttempted && !searching && query.trim().length >= 2)
                if (!showDropdown) {
                    // Clean layer switcher (Detailed first, Satellite second) + Pick with Google Maps
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            MapStyle.DETAILED to R.string.map_style_detailed,
                            MapStyle.SATELLITE to R.string.map_style_satellite,
                        ).forEach { (style, labelRes) ->
                            val selected = mapStyle == style
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                                shadowElevation = 4.dp,
                                onClick = { mapStyle = style },
                            ) {
                                Text(
                                    stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shadowElevation = 4.dp,
                            onClick = { showGoogleMapsSheet = true },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    stringResource(R.string.google_maps_btn),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                } else {
                    // Autocomplete dropdown results
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    ) {
                        if (results.isEmpty()) {
                            Text(
                                stringResource(R.string.no_search_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            LazyColumn {
                                items(results) { place ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focusManager.clearFocus()
                                                query = ""
                                                results = emptyList()
                                                searchAttempted = false
                                                selectedPlaceName = place.name
                                                addressLabel = listOf(place.name, place.subtitle)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(", ")
                                                moveMapTo(place.lat, place.lon, if (initialRadius != null) 17.5 else 13.5)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Filled.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                place.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (place.subtitle.isNotBlank()) {
                                                Text(
                                                    place.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Google-Maps-style "My location" button (in-app foreground permission only)
            SmallFloatingActionButton(
                onClick = {
                    if (!locating) {
                        myLocLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = Color(0xFF4285F4),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp),
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.LocationOn, stringResource(R.string.my_location))
                }
            }
        }

        // Bottom Confirmation Sheet
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 12.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            addressLabel ?: stringResource(R.string.drag_map_hint),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (addressLabel != null) stringResource(R.string.location_selected)
                            else stringResource(R.string.selected_place),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (initialRadius != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.silence_radius),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.radius_fmt, radius),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        RadiusSlider(
                            radiusMeters = radius,
                            onRadiusChange = { radius = it },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MAP_RADIUS_PRESETS.forEach { preset ->
                                val selected = radius == preset
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (selected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    },
                                    onClick = {
                                        radius = preset
                                        val fitZoom = when {
                                            preset <= 60 -> 18.2
                                            preset <= 120 -> 17.4
                                            preset <= 250 -> 16.4
                                            else -> 15.2
                                        }
                                        mapViewRef?.controller?.animateTo(GeoPoint(centerLat, centerLon), fitZoom, 350L)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.radius_fmt, preset),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
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

                Button(
                    onClick = {
                        val label = selectedPlaceName
                            ?: addressLabel?.substringBefore(",")?.trim()?.ifBlank { null }
                        onConfirm(centerLat, centerLon, if (initialRadius != null) radius else null, label)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        stringResource(R.string.confirm_location),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (showGoogleMapsSheet) {
        GoogleMapsPickerSheet(
            centerLat = centerLat,
            centerLon = centerLon,
            onDismiss = { showGoogleMapsSheet = false },
            onLocationResolved = { parsed ->
                showGoogleMapsSheet = false
                selectedPlaceName = parsed.label
                if (!parsed.label.isNullOrBlank()) addressLabel = parsed.label
                moveMapTo(parsed.lat, parsed.lon, if (initialRadius != null) 17.5 else 13.5)
            },
        )
    }
}

/**
 * Clean bottom sheet for picking a place via Google Maps (Open & Share -> SalahMute, or Paste link).
 * Shared between MapPickerScreen and GeoZoneEditorScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleMapsPickerSheet(
    centerLat: Double?,
    centerLon: Double?,
    onDismiss: () -> Unit,
    onLocationResolved: (ParsedMapLocation) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }

    fun importLink(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        resolving = true
        linkError = null
        scope.launch {
            val parsed = GoogleMapsLink.resolve(text, biasLat = centerLat, biasLon = centerLon)
            resolving = false
            if (parsed != null) {
                onLocationResolved(parsed)
            } else {
                linkError = ctx.getString(R.string.link_not_found)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.pick_google_maps_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Option 1: Open Google Maps -> Share -> SalahMute
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.google_maps_open_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.google_maps_open_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            try {
                                ctx.startActivity(GoogleMapsLink.openGoogleMapsIntent(ctx, centerLat, centerLon))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Place, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.open_google_maps_btn))
                    }
                }
            }

            // Option 2: Copy link from Google Maps -> Paste in SalahMute
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.google_maps_paste_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = {
                            linkText = it
                            linkError = null
                        },
                        placeholder = { Text(stringResource(R.string.google_maps_paste_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    linkError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            enabled = !resolving,
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                val cm = ctx.getSystemService(ClipboardManager::class.java)
                                val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty().trim()
                                if (pasted.isNotEmpty()) {
                                    linkText = pasted
                                    importLink(pasted)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.paste_btn))
                        }
                        FilledTonalButton(
                            enabled = !resolving && linkText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { importLink(linkText) },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (resolving) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.resolving_link), maxLines = 1)
                            } else {
                                Text(stringResource(R.string.use_link_btn))
                            }
                        }
                    }
                }
            }
        }
    }
}
