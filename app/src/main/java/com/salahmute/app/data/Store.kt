package com.salahmute.app.data

import android.content.Context
import com.salahmute.app.model.AppData
import com.salahmute.app.model.AppJson
import com.salahmute.app.model.GeoZone
import com.salahmute.app.model.Profile
import com.salahmute.app.model.Settings
import com.salahmute.app.model.Slot
import com.salahmute.app.prayer.OnlinePrayerSync
import com.salahmute.app.share.Bundle
import com.salahmute.app.system.GeoController
import com.salahmute.app.system.Scheduler
import com.salahmute.app.system.StateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/** Single source of truth. Persisted as JSON in the app's private files dir. */
object Store {
    private var file: File? = null
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> get() = _data
    val value: AppData get() = _data.value

    @Synchronized
    fun init(ctx: Context) {
        OnlinePrayerSync.init(ctx)
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "data.json")
        file = f
        _data.value = try {
            if (f.exists()) AppJson.decodeFromString(AppData.serializer(), f.readText()) else AppData()
        } catch (e: Exception) {
            AppData()
        }
    }

    @Synchronized
    fun update(ctx: Context, block: (AppData) -> AppData) {
        init(ctx)
        val prev = _data.value
        val next = block(prev)
        _data.value = next
        try {
            val target = file!!
            val encoded = AppJson.encodeToString(AppData.serializer(), next)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(encoded)
            if (!tmp.renameTo(target)) {
                target.writeText(encoded)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
        val appCtx = ctx.applicationContext
        if (prev.enabled != next.enabled || prev.geoEnabled != next.geoEnabled || prev.geoZones != next.geoZones) {
            GeoController.sync(appCtx)
        }
        Scheduler.reconcile(appCtx)
        if (next.settings.onboarded) {
            BackupManager.autoBackupIfEnabled(appCtx, next)
        }
    }

    /** Restores a full [AppData] backup cleanly (marks onboarded, syncs geofences & alarms). */
    fun restoreFullBackup(ctx: Context, restored: AppData) = update(ctx) {
        val safeProfiles = restored.profiles.ifEmpty { listOf(Profile(id = "default", name = "Default")) }
        val safeActiveId = if (safeProfiles.any { it.id == restored.activeProfileId }) {
            restored.activeProfileId
        } else {
            safeProfiles.first().id
        }
        restored.copy(
            profiles = safeProfiles,
            activeProfileId = safeActiveId,
            settings = restored.settings.copy(onboarded = true),
        )
    }

    /** Wipes current app data back to a clean fresh install state (used by "Delete backup & start fresh"). */
    fun resetAll(ctx: Context) = update(ctx) { AppData() }

    private fun AppData.mapActive(f: (Profile) -> Profile): AppData {
        val id = active.id
        return copy(profiles = profiles.map { if (it.id == id) f(it) else it })
    }

    // ---- master / settings -------------------------------------------------

    fun setEnabled(ctx: Context, enabled: Boolean) {
        if (enabled) StateStore(ctx).skippedSlotInstances = emptySet()
        update(ctx) { it.copy(enabled = enabled) }
    }

    fun setGeoEnabled(ctx: Context, enabled: Boolean) = update(ctx) { it.copy(geoEnabled = enabled) }

    fun setSettings(ctx: Context, f: (Settings) -> Settings) = update(ctx) { it.copy(settings = f(it.settings)) }

    // ---- slots -------------------------------------------------------------

    fun upsertSlot(ctx: Context, slot: Slot) {
        StateStore(ctx).clearSkippedSlot(slot.id)
        update(ctx) { d ->
            d.mapActive { p ->
                if (p.slots.any { it.id == slot.id }) p.copy(slots = p.slots.map { if (it.id == slot.id) slot else it })
                else p.copy(slots = p.slots + slot)
            }
        }
    }

    fun addSlots(ctx: Context, slots: List<Slot>) = update(ctx) { d -> d.mapActive { it.copy(slots = it.slots + slots) } }

    /** Replaces existing prayer-based slots with [slots], keeping all custom fixed-time slots intact. */
    fun setPrayerSlots(ctx: Context, slots: List<Slot>) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.setPrayerSlots(p.slots, slots)) }
    }

    /** Edits a slot cleanly for either just [day] (splitting off from shared days) or all its days. */
    fun upsertSlotForDay(ctx: Context, originalId: String?, edited: Slot, day: Int, applyAllDays: Boolean) {
        val st = StateStore(ctx)
        if (originalId != null) st.clearSkippedSlot(originalId)
        st.clearSkippedSlot(edited.id)
        update(ctx) { d ->
            d.mapActive { p -> p.copy(slots = ScheduleOps.upsertForDay(p.slots, originalId, edited, day, applyAllDays)) }
        }
    }

    fun deleteSlot(ctx: Context, id: String) = update(ctx) { d -> d.mapActive { p -> p.copy(slots = p.slots.filter { it.id != id }) } }

    /** Deletes a slot for just [day] (splitting it off if shared) or for all days. */
    fun deleteSlotForDay(ctx: Context, id: String, day: Int, allDays: Boolean) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.deleteForDay(p.slots, id, day, allDays)) }
    }

    fun setSlotEnabled(ctx: Context, id: String, enabled: Boolean) {
        if (enabled) StateStore(ctx).clearSkippedSlot(id)
        update(ctx) { d ->
            d.mapActive { p -> p.copy(slots = p.slots.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        }
    }

    /** Toggles a slot for [day] only; if shared across multiple days, splits [day] off independently. */
    fun setSlotEnabledForDay(ctx: Context, id: String, day: Int, enabled: Boolean) {
        if (enabled) StateStore(ctx).clearSkippedSlot(id)
        update(ctx) { d ->
            d.mapActive { p -> p.copy(slots = ScheduleOps.setEnabledForDay(p.slots, id, day, enabled)) }
        }
    }

    /** Archives or restores a slot for [day] only (splits off if shared across multiple days). */
    fun setSlotArchivedForDay(ctx: Context, id: String, day: Int, archived: Boolean) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.setArchivedForDay(p.slots, id, day, archived)) }
    }

    /** Archives multiple selected slots for [day] only. */
    fun archiveSlotsForDay(ctx: Context, ids: Set<String>, day: Int) = update(ctx) { d ->
        d.mapActive { p ->
            var cur = p.slots
            for (id in ids) cur = ScheduleOps.setArchivedForDay(cur, id, day, archived = true)
            p.copy(slots = cur)
        }
    }

    /** Deletes multiple selected slots for [day] only (or all days). */
    fun deleteSlotsForDay(ctx: Context, ids: Set<String>, day: Int, allDays: Boolean = false) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.deleteManyForDay(p.slots, ids, day, allDays)) }
    }

    /** Copies specific selected slots onto [targetDays] without touching other slots on those days. */
    fun copySlotsTo(ctx: Context, slotIds: Set<String>, targetDays: Set<Int>) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.copySlotsTo(p.slots, slotIds, targetDays)) }
    }

    /** Copies [fromDay]'s full active schedule onto each day in [targetDays] cleanly without bloating slots. */
    fun copyDayTo(ctx: Context, fromDay: Int, targetDays: Set<Int>) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = ScheduleOps.copyDayTo(p.slots, fromDay, targetDays)) }
    }

    /** Restores an exact previous slots snapshot (used for instant 1-tap Undo). */
    fun restoreActiveSlots(ctx: Context, snapshot: List<Slot>) = update(ctx) { d ->
        d.mapActive { p -> p.copy(slots = snapshot) }
    }

    // ---- geo zones ---------------------------------------------------------

    fun upsertGeoZone(ctx: Context, zone: GeoZone) = update(ctx) { d ->
        if (d.geoZones.any { it.id == zone.id }) {
            d.copy(geoZones = d.geoZones.map { if (it.id == zone.id) zone else it })
        } else {
            d.copy(geoZones = d.geoZones + zone)
        }
    }

    fun deleteGeoZone(ctx: Context, id: String) = update(ctx) { d ->
        d.copy(geoZones = d.geoZones.filter { it.id != id })
    }

    fun setGeoZoneEnabled(ctx: Context, id: String, enabled: Boolean) = update(ctx) { d ->
        d.copy(geoZones = d.geoZones.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    // ---- profiles ----------------------------------------------------------

    fun setActiveProfile(ctx: Context, id: String) = update(ctx) { d ->
        if (d.profiles.any { it.id == id }) d.copy(activeProfileId = id) else d
    }

    fun addProfile(ctx: Context, name: String) = update(ctx) { d ->
        val p = Profile(name = name.trim().take(40).ifBlank { "Profile" })
        d.copy(profiles = d.profiles + p, activeProfileId = p.id)
    }

    fun renameProfile(ctx: Context, id: String, name: String) = update(ctx) { d ->
        d.copy(profiles = d.profiles.map { if (it.id == id) it.copy(name = name.trim().take(40).ifBlank { it.name }) else it })
    }

    fun duplicateProfile(ctx: Context, id: String) = update(ctx) { d ->
        val src = d.profiles.firstOrNull { it.id == id } ?: return@update d
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = src.name.take(34) + " (2)",
            slots = src.slots.map { it.copy(id = UUID.randomUUID().toString()) },
        )
        d.copy(profiles = d.profiles + copy, activeProfileId = copy.id)
    }

    fun deleteProfile(ctx: Context, id: String) = update(ctx) { d ->
        if (d.profiles.size <= 1) return@update d
        val rest = d.profiles.filter { it.id != id }
        d.copy(profiles = rest, activeProfileId = if (d.activeProfileId == id) rest.first().id else d.activeProfileId)
    }

    // ---- import ------------------------------------------------------------

    private fun mergeGeoZones(current: List<GeoZone>, incoming: List<GeoZone>): List<GeoZone> {
        if (incoming.isEmpty()) return current
        val existing = current.map { it.copy(id = "") }.toSet()
        val extra = incoming.filter { it.copy(id = "") !in existing }
        return current + extra
    }

    fun importReplace(ctx: Context, b: Bundle) = update(ctx) { d ->
        d.copy(
            profiles = b.profiles,
            activeProfileId = b.profiles[b.activeProfile.coerceIn(0, b.profiles.lastIndex)].id,
            settings = d.settings.copy(method = b.method, hanafi = b.hanafi, fiqh = b.fiqh ?: d.settings.fiqh),
            geoZones = if (b.geoZones.isNotEmpty()) b.geoZones else d.geoZones,
        )
    }

    fun importAsProfiles(ctx: Context, b: Bundle) = update(ctx) { d ->
        val names = d.profiles.map { it.name }.toMutableSet()
        val added = b.profiles.map { p ->
            var name = p.name
            var n = 2
            while (name in names) name = "${p.name} ($n)".also { n++ }
            names += name
            p.copy(name = name)
        }
        d.copy(
            profiles = d.profiles + added,
            activeProfileId = added.first().id,
            geoZones = mergeGeoZones(d.geoZones, b.geoZones),
        )
    }

    fun importMerge(ctx: Context, b: Bundle) = update(ctx) { d ->
        val merged = d.mapActive { p ->
            val existing = p.slots.map { it.copy(id = "") }.toSet()
            val extra = b.profiles.flatMap { it.slots }.filter { it.copy(id = "") !in existing }
            p.copy(slots = p.slots + extra)
        }
        merged.copy(geoZones = mergeGeoZones(merged.geoZones, b.geoZones))
    }
}

/** Pure, JVM-testable per-day schedule transformations used by [Store]. */
object ScheduleOps {
    fun setPrayerSlots(slots: List<Slot>, newPrayers: List<Slot>): List<Slot> =
        slots.filter { it.prayer == null } + newPrayers

    fun upsertForDay(
        slots: List<Slot>,
        originalId: String?,
        edited: Slot,
        day: Int,
        applyAllDays: Boolean,
    ): List<Slot> {
        val orig = originalId?.let { id -> slots.firstOrNull { it.id == id } }
        return when {
            orig == null -> slots + edited.copy(days = listOf(day))
            applyAllDays || orig.days.size <= 1 ->
                slots.map { if (it.id == orig.id) edited.copy(id = orig.id, days = orig.days) else it }
            else -> {
                val remaining = orig.days.filter { it != day }
                val updated = slots.mapNotNull { s ->
                    if (s.id == orig.id) remaining.takeIf { it.isNotEmpty() }?.let { s.copy(days = it) } else s
                }
                updated + edited.copy(id = UUID.randomUUID().toString(), days = listOf(day))
            }
        }
    }

    fun deleteForDay(slots: List<Slot>, id: String, day: Int, allDays: Boolean): List<Slot> =
        slots.mapNotNull { s ->
            when {
                s.id != id -> s
                allDays || s.days.size <= 1 -> null
                else -> s.days.filter { it != day }.takeIf { it.isNotEmpty() }?.let { s.copy(days = it) }
            }
        }

    fun setEnabledForDay(slots: List<Slot>, id: String, day: Int, enabled: Boolean): List<Slot> {
        val orig = slots.firstOrNull { it.id == id } ?: return slots
        if (orig.enabled == enabled) return slots
        if (orig.days.size <= 1) {
            return slots.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
        val remaining = orig.days.filter { it != day }
        val base = slots.mapNotNull { s ->
            if (s.id == id) remaining.takeIf { it.isNotEmpty() }?.let { s.copy(days = it) } else s
        }
        return base + orig.copy(id = UUID.randomUUID().toString(), days = listOf(day), enabled = enabled)
    }

    fun setArchivedForDay(slots: List<Slot>, id: String, day: Int, archived: Boolean): List<Slot> {
        val orig = slots.firstOrNull { it.id == id } ?: return slots
        if (orig.archived == archived) return slots
        if (orig.days.size <= 1) {
            return slots.map { if (it.id == id) it.copy(archived = archived) else it }
        }
        val remaining = orig.days.filter { it != day }
        val base = slots.mapNotNull { s ->
            if (s.id == id) remaining.takeIf { it.isNotEmpty() }?.let { s.copy(days = it) } else s
        }
        return base + orig.copy(id = UUID.randomUUID().toString(), days = listOf(day), archived = archived)
    }

    fun deleteManyForDay(slots: List<Slot>, ids: Set<String>, day: Int, allDays: Boolean): List<Slot> =
        slots.mapNotNull { s ->
            when {
                s.id !in ids -> s
                allDays || s.days.size <= 1 -> null
                else -> s.days.filter { it != day }.takeIf { it.isNotEmpty() }?.let { s.copy(days = it) }
            }
        }

    fun copySlotsTo(slots: List<Slot>, slotIds: Set<String>, targetDays: Set<Int>): List<Slot> {
        val targets = targetDays.filter { it in 1..7 }.toSet()
        if (targets.isEmpty() || slotIds.isEmpty()) return slots
        return slots.map { s ->
            if (s.id in slotIds && !s.archived) {
                s.copy(days = (s.days + targets).distinct().sorted())
            } else {
                s
            }
        }
    }

    fun copyDayTo(slots: List<Slot>, fromDay: Int, targetDays: Set<Int>): List<Slot> {
        val targets = targetDays.filter { it in 1..7 && it != fromDay }.toSet()
        if (targets.isEmpty() || slots.none { !it.archived && fromDay in it.days }) return slots
        return slots.mapNotNull { s ->
            if (s.archived) {
                s
            } else if (fromDay in s.days) {
                s.copy(days = (s.days + targets).distinct().sorted())
            } else {
                val remaining = s.days.filter { it !in targets }
                if (remaining.isEmpty()) null else s.copy(days = remaining)
            }
        }
    }
}
