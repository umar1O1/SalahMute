package com.salahmute.app.system

import android.content.Context
import com.salahmute.app.model.RingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LiveSnapshot(
    val active: Boolean = false,
    val mode: RingMode = RingMode.VIBRATE,
    val startMs: Long = 0L,
    val untilMs: Long = 0L,
    val slotLabel: String? = null,
    val placeLabel: String? = null,
    val insideGeoIds: Set<String> = emptySet(),
    val nextAlarmAt: Long = 0L,
    val nextAlarmExact: Boolean = false,
    val lastBgTickAt: Long = 0L,
)

/** Small persisted state describing the currently applied mute and active geofences. */
class StateStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("salah_state", Context.MODE_PRIVATE)

    var active: Boolean
        get() = p.getBoolean("active", false)
        set(v) { p.edit().putBoolean("active", v).commit() }

    var activeMode: RingMode
        get() = runCatching { RingMode.valueOf(p.getString("mode", "") ?: "") }.getOrDefault(RingMode.VIBRATE)
        set(v) { p.edit().putString("mode", v.name).commit() }

    /** Epoch millis when the active time-slot chain started, or 0L if muted only by location. */
    var activeStart: Long
        get() = p.getLong("start", 0L)
        set(v) { p.edit().putLong("start", v).commit() }

    /** Epoch millis when the active time-slot chain ends, or 0L if muted only by location. */
    var activeUntil: Long
        get() = p.getLong("until", 0L)
        set(v) { p.edit().putLong("until", v).commit() }

    /** Label of the active time slot, if any. */
    var activeLabel: String?
        get() = p.getString("slotLabel", null)
        set(v) {
            p.edit().apply {
                if (v == null) remove("slotLabel") else putString("slotLabel", v)
            }.commit()
        }

    /** Non-null when muted by a geo-zone (empty string means unnamed location). */
    var activePlace: String?
        get() = p.getString("place", null)
        set(v) {
            p.edit().apply {
                if (v == null) remove("place") else putString("place", v)
            }.commit()
        }

    var origRinger: Int
        get() = p.getInt("origRinger", 2)
        set(v) { p.edit().putInt("origRinger", v).commit() }

    var origFilter: Int
        get() = p.getInt("origFilter", 1)
        set(v) { p.edit().putInt("origFilter", v).commit() }

    var appliedRinger: Int
        get() = p.getInt("appliedRinger", -1)
        set(v) { p.edit().putInt("appliedRinger", v).commit() }

    var appliedFilter: Int
        get() = p.getInt("appliedFilter", -1)
        set(v) { p.edit().putInt("appliedFilter", v).commit() }

    /** True once the user manually changes ringer/DND during an active mute session (respected until exit). */
    var userOverrode: Boolean
        get() = p.getBoolean("userOverrode", false)
        set(v) { p.edit().putBoolean("userOverrode", v).commit() }

    /** Legacy global skip timestamp (kept for backwards compatibility; prefer [skippedSlotInstances]). */
    var skipUntil: Long
        get() = p.getLong("skipUntil", 0L)
        set(v) { p.edit().putLong("skipUntil", v).commit() }

    /** Specific slot instances (`slotId@startMs`) dismissed via "End now" so other/upcoming slots still fire. */
    var skippedSlotInstances: Set<String>
        get() = p.getStringSet("skippedSlotInstances", emptySet())?.toSet() ?: emptySet()
        set(v) { p.edit().putStringSet("skippedSlotInstances", HashSet(v)).commit() }

    /** Clears dismissed instances belonging to [slotId] (e.g. when the user explicitly edits or re-enables it). */
    fun clearSkippedSlot(slotId: String) {
        val cur = skippedSlotInstances
        if (cur.isEmpty()) return
        val prefix = "$slotId@"
        val remaining = cur.filterNot { it.startsWith(prefix) }.toSet()
        if (remaining.size != cur.size) skippedSlotInstances = remaining
    }

    /** GeoZone IDs that currently have proximity alerts registered with LocationManager. */
    var registeredGeoIds: Set<String>
        get() = p.getStringSet("registeredGeoIds", emptySet())?.toSet() ?: emptySet()
        set(v) { p.edit().putStringSet("registeredGeoIds", HashSet(v)).commit() }

    /** GeoZone IDs the device is currently inside. */
    var insideGeoIds: Set<String>
        get() = p.getStringSet("insideGeoIds", emptySet())?.toSet() ?: emptySet()
        set(v) { p.edit().putStringSet("insideGeoIds", HashSet(v)).commit() }

    /** GeoZone IDs dismissed via "End now" until the device leaves them. */
    var skippedGeoIds: Set<String>
        get() = p.getStringSet("skippedGeoIds", emptySet())?.toSet() ?: emptySet()
        set(v) { p.edit().putStringSet("skippedGeoIds", HashSet(v)).commit() }

    /** Epoch millis of the next currently armed alarm (0L if none). */
    var nextAlarmAt: Long
        get() = p.getLong("nextAlarmAt", 0L)
        set(v) { p.edit().putLong("nextAlarmAt", v).commit() }

    /** Whether the next armed alarm used exact scheduling (AlarmClock / ExactAndAllowWhileIdle). */
    var nextAlarmExact: Boolean
        get() = p.getBoolean("nextAlarmExact", false)
        set(v) { p.edit().putBoolean("nextAlarmExact", v).commit() }

    /** Epoch millis of the most recent background wakeup (alarm, job, boot, task-removed). */
    var lastBgTickAt: Long
        get() = p.getLong("lastBgTickAt", 0L)
        set(v) { p.edit().putLong("lastBgTickAt", v).commit() }

    /** Short source tag of the most recent background wakeup. */
    var lastBgSource: String
        get() = p.getString("lastBgSource", "") ?: ""
        set(v) { p.edit().putString("lastBgSource", v).commit() }

    /** True once the user explicitly chose "Save (in-app only)" so adding more places doesn't nag repeatedly. */
    var bgLocPromptDismissed: Boolean
        get() = p.getBoolean("bgLocPromptDismissed", false)
        set(v) { p.edit().putBoolean("bgLocPromptDismissed", v).commit() }

    fun snapshot(): LiveSnapshot = LiveSnapshot(
        active = active,
        mode = activeMode,
        startMs = activeStart,
        untilMs = activeUntil,
        slotLabel = activeLabel,
        placeLabel = activePlace,
        insideGeoIds = insideGeoIds,
        nextAlarmAt = nextAlarmAt,
        nextAlarmExact = nextAlarmExact,
        lastBgTickAt = lastBgTickAt,
    )

    fun publish() {
        _live.value = snapshot()
    }

    companion object {
        private val _live = MutableStateFlow(LiveSnapshot())
        val live: StateFlow<LiveSnapshot> get() = _live

        fun refresh(ctx: Context) {
            StateStore(ctx).publish()
        }
    }
}
