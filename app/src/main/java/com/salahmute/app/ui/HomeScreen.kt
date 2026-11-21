package com.salahmute.app.ui

import android.Manifest
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.sign
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.RingMode
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.LiveSnapshot
import com.salahmute.app.system.PermState
import com.salahmute.app.system.Perms
import com.salahmute.app.system.Scheduler
import com.salahmute.app.system.StateStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun rememberPerms(): PermState {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Perms.read(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { s = Perms.read(ctx) }
    return s
}

@Composable
fun ModePill(mode: RingMode) {
    val (bg, fg) = when (mode) {
        RingMode.VIBRATE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        RingMode.SILENT -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        RingMode.DND -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onEditGeo: (String) -> Unit,
    onAddGeoViaMap: () -> Unit,
    onSettings: () -> Unit,
    onQuickSetup: () -> Unit,
    onOpenAutoPrayers: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val locale = currentLocale()
    val data by Store.data.collectAsState()
    val live by StateStore.live.collectAsState()
    val cacheVer by OnlinePrayerSync.version.collectAsState()
    var perms by remember { mutableStateOf(Perms.read(ctx)) }
    val profile = data.active

    val snackbarHost = remember { SnackbarHostState() }
    var profileMenu by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var copyTargetIds by remember { mutableStateOf<Set<String>?>(null) } // null = copy whole day; non-null = specific slots
    var showCopyDialog by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showArchived by remember { mutableStateOf(false) }
    var lastLoc by remember { mutableStateOf<Location?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val selectionMode = selectedTab == 0 && selectedIds.isNotEmpty()
    LaunchedEffect(selectedDay, selectedTab, profile.id) { selectedIds = emptySet() }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    val daySlots = remember(profile.slots, selectedDay, data.settings, cacheVer) {
        profile.slots
            .filter { !it.archived && selectedDay in it.days }
            .sortedBy { s ->
                Engine.slotRange(s, nextDateOf(selectedDay), data.settings, ZoneId.systemDefault())?.first
                    ?: s.startMin.toDouble()
            }
    }
    val archivedDaySlots = remember(profile.slots, selectedDay, data.settings, cacheVer) {
        profile.slots
            .filter { it.archived && selectedDay in it.days }
            .sortedBy { s ->
                Engine.slotRange(s, nextDateOf(selectedDay), data.settings, ZoneId.systemDefault())?.first
                    ?: s.startMin.toDouble()
            }
    }

    fun showUndoSnackbar(msgRes: Int, prevSlots: List<Slot>) {
        val msg = ctx.getString(msgRes)
        val undoLabel = ctx.getString(R.string.undo)
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val res = snackbarHost.showSnackbar(
                message = msg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) {
                Store.restoreActiveSlots(ctx, prevSlots)
            }
        }
    }

    // Tick ONLY while the Activity is visible (STARTED) so background/screen-off UI CPU is 0%
    val lifecycleOwner = LocalLifecycleOwner.current
    val tickFast = data.enabled && live.active && live.untilMs > 0L
    LaunchedEffect(lifecycleOwner, tickFast) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            StateStore.refresh(ctx)
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(if (tickFast) 1_000L else 15_000L)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = Perms.read(ctx)
        nowMs = System.currentTimeMillis()
        Scheduler.reconcile(ctx)
        StateStore.refresh(ctx)
        if (perms.fineLocation && Store.value.geoEnabled && Store.value.geoZones.any { it.enabled }) {
            GeoController.refreshNow(ctx) { loc -> if (loc != null) lastLoc = loc }
        }
    }

    // Step 1: foreground (in-app) location launcher
    val inAppLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        perms = Perms.read(ctx)
        if (perms.fineLocation) {
            GeoController.sync(ctx)
            GeoController.refreshNow(ctx) { loc -> if (loc != null) lastLoc = loc }
        }
    }

    // Step 2: optional background location launcher
    val bgLocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        perms = Perms.read(ctx)
        GeoController.sync(ctx)
        Scheduler.reconcile(ctx)
        if (perms.backgroundLocation) {
            StateStore(ctx).bgLocPromptDismissed = false
            Toast.makeText(ctx, R.string.loc_bg_ready_toast, Toast.LENGTH_SHORT).show()
        }
    }

    val nextSlot = remember(data, nowMs / 15_000L, live.active, cacheVer) {
        if (!data.enabled) null
        else Engine.nextUpcoming(Engine.intervals(data, nowMs), nowMs)
    }

    // Wake up on the exact slot start/end boundary while the screen is open (zero polling!)
    val nextBoundaryMs = remember(data.enabled, live.active, live.untilMs, nextSlot?.startMs) {
        if (!data.enabled) null
        else listOfNotNull(
            live.untilMs.takeIf { live.active && it > 0L },
            nextSlot?.startMs,
        ).minOrNull()
    }
    LaunchedEffect(nextBoundaryMs) {
        val target = nextBoundaryMs ?: return@LaunchedEffect
        val waitMs = target - System.currentTimeMillis() + 150L
        if (waitMs > 0L) delay(waitMs)
        nowMs = System.currentTimeMillis()
        Scheduler.reconcile(ctx)
        StateStore.refresh(ctx)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (selectionMode) {
                val allSelected = daySlots.isNotEmpty() && selectedIds.size >= daySlots.size
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                        }
                    },
                    title = {
                        Text(
                            stringResource(R.string.selected_count, selectedIds.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) emptySet() else daySlots.map { it.id }.toSet()
                            },
                        ) {
                            Text(stringResource(R.string.select_all))
                        }
                    },
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        if (selectedTab == 0) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable { profileMenu = true },
                                ) {
                                    Row(
                                        Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            profile.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Icon(
                                            Icons.Filled.ArrowDropDown,
                                            contentDescription = stringResource(R.string.profile),
                                        )
                                    }
                                }
                                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                                    data.profiles.forEach { p ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(p.name, fontWeight = if (p.id == profile.id) FontWeight.SemiBold else FontWeight.Normal)
                                                    Text(
                                                        stringResource(R.string.profile_slots_count, p.slots.count { !it.archived }),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            leadingIcon = { if (p.id == profile.id) Icon(Icons.Filled.Check, null) },
                                            onClick = {
                                                Store.setActiveProfile(ctx, p.id)
                                                profileMenu = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.manage_profiles), color = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            profileMenu = false
                                            showProfiles = true
                                        },
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.tab_locations),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == 1 && data.geoZones.isNotEmpty()) {
                            IconButton(onClick = {
                                if (!perms.fineLocation) {
                                    inAppLocLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                } else {
                                    GeoController.refreshNow(ctx) { loc ->
                                        if (loc != null) {
                                            lastLoc = loc
                                            Toast.makeText(ctx, R.string.location_checked, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(ctx, R.string.loc_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.check_now))
                            }
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                    },
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyTargetIds = selectedIds
                                showCopyDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.copy_action), maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                val prev = profile.slots
                                Store.archiveSlotsForDay(ctx, selectedIds, selectedDay)
                                selectedIds = emptySet()
                                showUndoSnackbar(R.string.slot_archived_msg, prev)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.archive), maxLines = 1, softWrap = false)
                        }
                        Button(
                            onClick = {
                                val prev = profile.slots
                                Store.deleteSlotsForDay(ctx, selectedIds, selectedDay, allDays = false)
                                selectedIds = emptySet()
                                showUndoSnackbar(R.string.slot_deleted_msg, prev)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.delete), maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { onSelectTab(0) },
                        icon = { Icon(Icons.Filled.DateRange, null) },
                        label = { Text(stringResource(R.string.tab_schedule)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { onSelectTab(1) },
                        icon = { Icon(Icons.Filled.LocationOn, null) },
                        label = { Text(stringResource(R.string.tab_locations)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectionMode) {
                // Hidden during multi-select so contextual bottom bar stays unobstructed
            } else if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_slot))
                }
            } else if (data.geoZones.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddGeoViaMap,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_location))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Only show status card when at least one enabled slot/location exists (or app is paused/muting)
            val activeGeoCount = data.geoZones.count { it.enabled }
            val hasAnyRule = profile.slots.isNotEmpty() || data.geoZones.isNotEmpty()
            val showHero = live.active || nextSlot != null || activeGeoCount > 0 || (!data.enabled && hasAnyRule)
            if (showHero) {
                HeroStatusCard(
                    enabled = data.enabled,
                    live = live,
                    nextSlot = nextSlot,
                    activeGeoCount = activeGeoCount,
                    nowMs = nowMs,
                    locale = locale,
                    onToggle = { Store.setEnabled(ctx, it) },
                    onEndNow = { Scheduler.endNow(ctx) },
                )
            }

            // Calm 2-step quick setup card (only core DND/Alarm — never mentions location)
            if (!perms.essentialsOk) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable(onClick = onQuickSetup),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.setup_essential_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                stringResource(R.string.setup_essential_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                val todayDow = remember { LocalDate.now().dayOfWeek.value }
                DayPillStrip(
                    selectedDay = selectedDay,
                    todayDow = todayDow,
                    locale = locale,
                    countForDay = { d -> profile.slots.count { !it.archived && it.enabled && d in it.days } },
                    onSelectDay = onSelectDay,
                )

                val fullDayName = DayOfWeek.of(selectedDay).getDisplayName(TextStyle.FULL, locale)
                if (daySlots.isEmpty() && archivedDaySlots.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.empty_day_title, fullDayName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.empty_day_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp, 4.dp, 10.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (daySlots.isNotEmpty()) {
                            item(key = "day_header") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$fullDayName · ${daySlots.size}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!selectionMode) {
                                        TextButton(onClick = {
                                            copyTargetIds = null
                                            showCopyDialog = true
                                        }) {
                                            Text(stringResource(R.string.copy_all))
                                        }
                                    }
                                }
                            }
                            items(daySlots, key = { "${it.id}_${it.archived}_$selectedDay" }) { slot ->
                                val isSelected = slot.id in selectedIds
                                SwipeableSlotRow(
                                    enabled = !selectionMode,
                                    onSwipeRight = {
                                        val prev = profile.slots
                                        Store.setSlotArchivedForDay(ctx, slot.id, selectedDay, archived = true)
                                        showUndoSnackbar(R.string.slot_archived_msg, prev)
                                    },
                                    onSwipeLeft = {
                                        val prev = profile.slots
                                        Store.deleteSlotForDay(ctx, slot.id, selectedDay, allDays = false)
                                        showUndoSnackbar(R.string.slot_deleted_msg, prev)
                                    },
                                ) {
                                    SlotCard(
                                        slot = slot,
                                        dow = selectedDay,
                                        settings = data.settings,
                                        selectionMode = selectionMode,
                                        selected = isSelected,
                                        onCardClick = {
                                            if (selectionMode) {
                                                selectedIds = if (isSelected) selectedIds - slot.id else selectedIds + slot.id
                                            } else if (data.settings.tapCardToEdit) {
                                                onEdit(slot.id)
                                            }
                                        },
                                        onEditClick = { onEdit(slot.id) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedIds = if (isSelected) selectedIds - slot.id else selectedIds + slot.id
                                        },
                                        onToggle = { Store.setSlotEnabledForDay(ctx, slot.id, selectedDay, it) },
                                        onCopySingle = {
                                            copyTargetIds = setOf(slot.id)
                                            showCopyDialog = true
                                        },
                                        onArchive = {
                                            val prev = profile.slots
                                            Store.setSlotArchivedForDay(ctx, slot.id, selectedDay, archived = true)
                                            showUndoSnackbar(R.string.slot_archived_msg, prev)
                                        },
                                        onDelete = {
                                            val prev = profile.slots
                                            Store.deleteSlotForDay(ctx, slot.id, selectedDay, allDays = false)
                                            showUndoSnackbar(R.string.slot_deleted_msg, prev)
                                        },
                                    )
                                }
                            }
                        } else {
                            item(key = "empty_active_hint") {
                                Text(
                                    stringResource(R.string.empty_day_title, fullDayName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                )
                            }
                        }

                        // Collapsible Archived section at bottom of the day
                        if (archivedDaySlots.isNotEmpty()) {
                            item(key = "archived_header") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    onClick = { showArchived = !showArchived },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.archived_section, archivedDaySlots.size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            Icons.Filled.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (showArchived) {
                                items(archivedDaySlots, key = { "arch_${it.id}_$selectedDay" }) { slot ->
                                    ArchivedSlotCard(
                                        slot = slot,
                                        dow = selectedDay,
                                        settings = data.settings,
                                        onRestore = { Store.setSlotArchivedForDay(ctx, slot.id, selectedDay, archived = false) },
                                        onDelete = {
                                            val prev = profile.slots
                                            Store.deleteSlotForDay(ctx, slot.id, selectedDay, allDays = false)
                                            showUndoSnackbar(R.string.slot_deleted_msg, prev)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Locations tab: clean, zero redundant toggles, progressive in-app -> background permissions
                if (data.geoZones.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Text(
                                    stringResource(R.string.loc_intro_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    stringResource(R.string.loc_intro_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    stringResource(R.string.loc_offline_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = onAddGeoViaMap,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                ) {
                                    Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.pick_on_map))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp, 8.dp, 10.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Progressive Step 1 (in-app first) -> Step 2 (clear "Works only while app is open" bar)
                        if (data.enabled && data.geoZones.any { it.enabled }) {
                            if (!perms.fineLocation) {
                                item(key = "perm_in_app") {
                                    ProgressivePermCard(
                                        title = stringResource(R.string.loc_in_app_title),
                                        desc = stringResource(R.string.loc_in_app_desc),
                                        actionLabel = stringResource(R.string.grant),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        onAction = {
                                            inAppLocLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                ),
                                            )
                                        },
                                    )
                                }
                            } else if (Build.VERSION.SDK_INT >= 29 && !perms.backgroundLocation) {
                                item(key = "perm_bg") {
                                    ProgressivePermCard(
                                        title = stringResource(R.string.loc_only_open_title),
                                        desc = stringResource(R.string.loc_only_open_desc),
                                        actionLabel = stringResource(R.string.turn_on),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        onAction = {
                                            bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                        },
                                    )
                                }
                            }
                        }

                        items(data.geoZones, key = { it.id }) { zone ->
                            GeoZoneCard(
                                zone = zone,
                                isInside = data.enabled && zone.enabled && zone.id in live.insideGeoIds,
                                lastLoc = lastLoc,
                                onClick = { onEditGeo(zone.id) },
                                onToggle = {
                                    if (!data.geoEnabled && it) Store.setGeoEnabled(ctx, true)
                                    Store.setGeoZoneEnabled(ctx, zone.id, it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AddOptionRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.auto_prayers_title),
                    desc = stringResource(R.string.add_prayers_desc),
                    onClick = {
                        showAddSheet = false
                        onOpenAutoPrayers()
                    },
                )
                AddOptionRow(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.custom_time_title),
                    desc = stringResource(R.string.add_slot_desc),
                    onClick = {
                        showAddSheet = false
                        onAdd()
                    },
                )
            }
        }
    }

    if (showProfiles) ProfilesDialog(onDismiss = { showProfiles = false })
    if (showCopyDialog) {
        val targetIds = copyTargetIds
        CopyDayDialog(
            fromDay = selectedDay,
            selectedCount = targetIds?.size,
            locale = locale,
            onDismiss = {
                showCopyDialog = false
                copyTargetIds = null
            },
            onCopy = { targets ->
                if (targetIds == null) {
                    Store.copyDayTo(ctx, selectedDay, targets)
                } else {
                    Store.copySlotsTo(ctx, targetIds, targets)
                    selectedIds = emptySet()
                }
                showCopyDialog = false
                copyTargetIds = null
                Toast.makeText(ctx, R.string.copied_ok, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun CopyDayDialog(
    fromDay: Int,
    selectedCount: Int?,
    locale: Locale,
    onDismiss: () -> Unit,
    onCopy: (Set<Int>) -> Unit,
) {
    val fromName = remember(fromDay, locale) { DayOfWeek.of(fromDay).getDisplayName(TextStyle.FULL, locale) }
    val otherDays = remember(fromDay) { (1..7).filter { it != fromDay } }
    var chosen by remember(fromDay, selectedCount) { mutableStateOf(emptySet<Int>()) }
    val allSelected = chosen.size == otherDays.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedCount != null) stringResource(R.string.copy_selected_title, selectedCount)
                else stringResource(R.string.copy_day_title, fromName),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (selectedCount != null) stringResource(R.string.copy_selected_note)
                    else stringResource(R.string.copy_day_note, fromName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { chosen = if (allSelected) emptySet() else otherDays.toSet() },
                    ) {
                        Text(stringResource(R.string.select_all))
                    }
                }
                otherDays.forEach { d ->
                    val checked = d in chosen
                    val name = DayOfWeek.of(d).getDisplayName(TextStyle.FULL, locale)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { chosen = if (checked) chosen - d else chosen + d }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { chosen = if (it) chosen + d else chosen - d },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = chosen.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                onClick = { onCopy(chosen) },
            ) {
                Text(stringResource(R.string.copy_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun HeroStatusCard(
    enabled: Boolean,
    live: LiveSnapshot,
    nextSlot: Engine.NextSlot?,
    activeGeoCount: Int,
    nowMs: Long,
    locale: Locale,
    onToggle: (Boolean) -> Unit,
    onEndNow: () -> Unit,
) {
    val isMutingNow = live.active && enabled
    val timeFmt = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }

    if (isMutingNow) {
        val modeStr = stringResource(live.mode.labelRes())
        val timeStr = if (live.untilMs > 0L) {
            timeFmt.format(Instant.ofEpochMilli(live.untilMs).atZone(ZoneId.systemDefault()))
        } else {
            null
        }
        val placeStr = live.placeLabel?.let { it.ifBlank { stringResource(R.string.location) } }
        val activeName = live.slotLabel?.takeIf { it.isNotBlank() } ?: placeStr
        val remainingMs = (live.untilMs - nowMs).coerceAtLeast(0L)
        val totalSec = remainingMs / 1000L
        val hrs = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        val countdownStr = if (live.untilMs > nowMs) {
            if (hrs > 0L) "%d:%02d:%02d".format(Locale.US, hrs, mins, secs)
            else "%02d:%02d".format(Locale.US, mins, secs)
        } else {
            null
        }
        val progress = if (live.startMs > 0L && live.untilMs > live.startMs) {
            ((nowMs - live.startMs).toFloat() / (live.untilMs - live.startMs).toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
        val restoreLine = when {
            timeStr != null && placeStr != null -> stringResource(R.string.notif_restores_place_time, placeStr, timeStr)
            timeStr != null -> stringResource(R.string.notif_restores_at, timeStr)
            placeStr != null -> stringResource(R.string.notif_restores_leave, placeStr)
            else -> modeStr
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary),
                            )
                            Text(
                                "${stringResource(R.string.active_now_tag)} · $modeStr",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onToggle)
                }

                // Prominent big remaining timer (or active place name for location-only silence)
                if (countdownStr != null) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            countdownStr,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(R.string.remaining_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                        )
                    }
                } else if (activeName != null) {
                    Text(
                        activeName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (activeName != null && countdownStr != null) {
                            Text(
                                activeName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            restoreLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onEndNow,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.restore_now))
                    }
                }
            }
        }
        return
    }

    // Idle or paused slim bar
    val bg = if (enabled) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer
    val fg = MaterialTheme.colorScheme.onSurface
    val subFg = MaterialTheme.colorScheme.onSurfaceVariant

    val title: String
    val subtitle: String
    var badgeText: String? = null
    var badgeUrgent = false

    when {
        !enabled -> {
            title = stringResource(R.string.status_paused_title)
            subtitle = stringResource(R.string.status_paused_desc)
        }
        nextSlot != null -> {
            val label = nextSlot.label.ifBlank { stringResource(nextSlot.mode.labelRes()) }
            val startStr = timeFmt.format(Instant.ofEpochMilli(nextSlot.startMs).atZone(ZoneId.systemDefault()))
            val endStr = timeFmt.format(Instant.ofEpochMilli(nextSlot.endMs).atZone(ZoneId.systemDefault()))
            val diffMin = ((nextSlot.startMs - nowMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            title = stringResource(R.string.next_up_title, label)
            subtitle = "$startStr – $endStr · ${stringResource(nextSlot.mode.labelRes())}"
            if (diffMin < 24 * 60L) {
                badgeText = if (diffMin < 60L) {
                    stringResource(R.string.badge_in_min, diffMin.toInt())
                } else {
                    stringResource(R.string.badge_in_hr, (diffMin / 60L).toInt(), (diffMin % 60L).toInt())
                }
                badgeUrgent = diffMin <= 15L
            }
        }
        else -> {
            title = stringResource(R.string.status_active)
            subtitle = if (activeGeoCount > 0) {
                stringResource(R.string.settings_loc_count, activeGeoCount)
            } else {
                stringResource(R.string.status_ready)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            badgeUrgent -> MaterialTheme.colorScheme.primary
                            enabled -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (badgeUrgent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (badgeUrgent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DayPillStrip(
    selectedDay: Int,
    todayDow: Int,
    locale: Locale,
    countForDay: (Int) -> Int,
    onSelectDay: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (d in 1..7) {
                val selected = d == selectedDay
                val isToday = d == todayDow
                val count = countForDay(d)
                val bg by animateColorAsState(
                    targetValue = when {
                        selected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.surfaceContainerLowest
                        else -> Color.Transparent
                    },
                    label = "dayBg",
                )
                val fg by animateColorAsState(
                    targetValue = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    label = "dayFg",
                )
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = bg,
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    border = if (!selected && isToday) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    } else {
                        null
                    },
                    onClick = { onSelectDay(d) },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isToday && selected -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                ),
                        )
                        Text(
                            dayShort(d, locale),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (count > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        } else {
                            Box(
                                Modifier
                                    .padding(vertical = 6.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressivePermCard(
    title: String,
    desc: String,
    actionLabel: String,
    containerColor: Color,
    contentColor: Color,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.85f))
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AddOptionRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Custom swipe container with a generous 28 dp horizontal dead-zone + horizontal angle lock
 * so pressing/holding a card (for long-press multi-select or Switch clicks) or scrolling vertically
 * NEVER nudges the card. Requires a deliberate 42% drag past the dead-zone to trigger Archive/Delete.
 */
@Composable
private fun SwipeableSlotRow(
    enabled: Boolean,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val deadZonePx = with(density) { 28.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    val cur = offsetX.value
    val triggerPx = (widthPx * 0.42f).coerceAtLeast(with(density) { 120.dp.toPx() })
    val armed = abs(cur) >= triggerPx && widthPx > 0f

    LaunchedEffect(armed) {
        if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var dragging = false
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y
                        if (!dragging) {
                            if (abs(totalDy) > deadZonePx * 0.85f) {
                                // Vertical scroll takes priority — leave card completely untouched
                                cancelled = true
                                break
                            }
                            if (abs(totalDx) >= deadZonePx && abs(totalDx) > abs(totalDy) * 1.6f) {
                                dragging = true
                            }
                        }
                        if (dragging) {
                            change.consume()
                            val maxSlide = (widthPx * 0.65f).coerceAtLeast(deadZonePx * 4f)
                            val shifted = (totalDx - sign(totalDx) * deadZonePx).coerceIn(-maxSlide, maxSlide)
                            scope.launch { offsetX.snapTo(shifted) }
                        }
                    }
                    if (dragging && !cancelled) {
                        val finalOffset = offsetX.value
                        val thresh = (widthPx * 0.42f).coerceAtLeast(deadZonePx * 4f)
                        when {
                            finalOffset >= thresh -> {
                                onSwipeRight()
                                scope.launch { offsetX.snapTo(0f) }
                            }
                            finalOffset <= -thresh -> {
                                onSwipeLeft()
                                scope.launch { offsetX.snapTo(0f) }
                            }
                            else -> scope.launch { offsetX.animateTo(0f, spring(stiffness = 500f)) }
                        }
                    } else if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = 500f)) }
                    }
                }
            },
    ) {
        if (abs(cur) > 1f) {
            val right = cur > 0f
            val bg = if (right) {
                if (armed) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                if (armed) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            }
            val fg = if (right) {
                if (armed) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
            } else {
                if (armed) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.65f)
            }
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (right) {
                        Icon(painterResource(R.drawable.ic_archive), null, tint = fg, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.archive),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (armed) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                        )
                    } else {
                        Text(
                            stringResource(R.string.delete),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (armed) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                        )
                        Icon(Icons.Filled.Delete, null, tint = fg, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotCard(
    slot: Slot,
    dow: Int,
    settings: com.salahmute.app.model.Settings,
    selectionMode: Boolean,
    selected: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCopySingle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = currentLocale()
    val interactionSource = remember { MutableInteractionSource() }
    val showTapRipple = selectionMode || settings.tapCardToEdit
    val title = slot.label.ifBlank {
        slot.prayer?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.slot_untitled)
    }
    val cardBg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        label = "slotBg",
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = if (showTapRipple) LocalIndication.current else null,
                onClick = onCardClick,
                onLongClick = onLongClick,
            ),
    ) {
        Column {
            Row(
                Modifier
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
                    .alpha(if (slot.enabled) 1f else 0.55f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.8.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(22.dp),
                        ) {}
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        slotRangeText(slot, dow, settings, locale),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ModePill(slot.mode)
                    }
                    val prayer = slot.prayer
                    if (prayer != null) {
                        Text(
                            stringResource(R.string.slot_prayer_desc, stringResource(prayer.labelRes()), slot.before, slot.after),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (!selectionMode) {
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = slot.enabled, onCheckedChange = onToggle)
                }
            }

            // Direct 1-tap action strip outside (Edit, Copy, Archive, Delete) — hidden during multi-select
            if (!selectionMode) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                if (settings.compactCardActions) {
                    // Option B (default): sleek compact icons-only strip
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onCopySingle, modifier = Modifier.size(38.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_copy),
                                contentDescription = stringResource(R.string.copy_action),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onArchive, modifier = Modifier.size(38.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_archive),
                                contentDescription = stringResource(R.string.archive),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    // Option A (optional in Settings -> Appearance): icon + short label strip
                    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardActionCell(
                            label = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onEditClick,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                        Box(Modifier.width(1.dp).height(20.dp).background(dividerColor))
                        CardActionCell(
                            label = stringResource(R.string.copy_action),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onCopySingle,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.ic_copy), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                        Box(Modifier.width(1.dp).height(20.dp).background(dividerColor))
                        CardActionCell(
                            label = stringResource(R.string.archive),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onArchive,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(painterResource(R.drawable.ic_archive), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                        Box(Modifier.width(1.dp).height(20.dp).background(dividerColor))
                        CardActionCell(
                            label = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardActionCell(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
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

@Composable
private fun ArchivedSlotCard(
    slot: Slot,
    dow: Int,
    settings: com.salahmute.app.model.Settings,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = currentLocale()
    val title = slot.label.ifBlank {
        slot.prayer?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.slot_untitled)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).alpha(0.7f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    slotRangeText(slot, dow, settings, locale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.unarchive))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun GeoZoneCard(
    zone: GeoZone,
    isInside: Boolean,
    lastLoc: Location?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val title = zone.label.ifBlank { stringResource(R.string.location) }
    val distMeters = lastLoc?.let { Engine.distanceMeters(it.latitude, it.longitude, zone.lat, zone.lon) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (isInside) 1.5.dp else 1.dp,
            color = if (isInside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp).alpha(if (zone.enabled) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isInside) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = if (isInside) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ModePill(zone.mode)
                }
                val statusText = when {
                    isInside -> stringResource(R.string.geo_here_now)
                    distMeters != null -> {
                        val distLabel = if (distMeters < 1000.0) {
                            stringResource(R.string.distance_m, distMeters.roundToInt())
                        } else {
                            stringResource(R.string.distance_km, distMeters / 1000.0)
                        }
                        stringResource(R.string.geo_away_radius, distLabel, zone.radiusMeters)
                    }
                    else -> stringResource(R.string.geo_radius_desc, zone.radiusMeters)
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isInside) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isInside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = zone.enabled, onCheckedChange = onToggle)
        }
    }
}
