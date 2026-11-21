package com.salahmute.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salahmute.app.MainActivity
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine

object Scheduler {
    const val ACTION_TICK = "com.salahmute.app.TICK"
    const val ACTION_END_NOW = "com.salahmute.app.END_NOW"
    const val ACTION_RECOVERY = "com.salahmute.app.RECOVERY"

    /**
     * Brings the phone's ringer in line with both time slots and active geo-zones right now,
     * arms AlarmManager + JobScheduler for the next boundary, and syncs KeepAliveService.
     * Safe to call any time (idempotent).
     */
    @Synchronized
    fun reconcile(context: Context, source: String? = null) {
        val ctx = context.applicationContext
        Store.init(ctx)
        val now = System.currentTimeMillis()
        val st = StateStore(ctx)
        if (source != null) {
            st.lastBgTickAt = now
            st.lastBgSource = source
        }
        val data = Store.value
        val intervals = Engine.intervals(data, now)

        var skip = st.skipUntil
        if (skip != 0L && skip <= now) {
            skip = 0L
            st.skipUntil = 0L
        }

        // Prune expired skipped slot instances whose intervals have already ended or been removed.
        val rawSkipped = st.skippedSlotInstances
        val liveKeys = intervals.filter { it.endMs > now }.mapTo(HashSet()) { it.instanceKey }
        val skippedInstances = rawSkipped.intersect(liveKeys)
        if (skippedInstances.size != rawSkipped.size) {
            st.skippedSlotInstances = skippedInstances
        }

        val effective = Engine.resolveActive(
            data = data,
            intervals = intervals,
            nowMs = now,
            skipUntil = skip,
            insideGeoIds = st.insideGeoIds,
            skippedGeoIds = st.skippedGeoIds,
            skippedIntervals = skippedInstances,
        )
        if (effective == null) {
            RingerController.exit(ctx, st)
        } else {
            RingerController.enter(
                ctx = ctx,
                st = st,
                mode = effective.mode,
                startMs = effective.startMs,
                until = effective.untilMs,
                slotLabel = effective.slotLabel,
                placeLabel = effective.placeLabel,
            )
        }

        val usableIntervals = if (skippedInstances.isEmpty()) {
            intervals
        } else {
            intervals.filter { it.instanceKey !in skippedInstances }
        }
        val next = listOfNotNull(Engine.nextBoundary(usableIntervals, now), skip.takeIf { it > now }).minOrNull()
        // Small +50ms buffer prevents early RTC wakeups without causing visible countdown overshoot
        val alarmAt = next?.let { it + 50L }
        val exactUsed = setAlarm(ctx, alarmAt)
        st.nextAlarmAt = alarmAt ?: 0L
        st.nextAlarmExact = exactUsed
        st.publish()

        val hasWork = data.enabled && (
            data.active.slots.any { it.enabled && !it.archived } ||
                (data.geoEnabled && data.geoZones.any { it.enabled })
            )
        ReconcileJobService.schedule(ctx, active = hasWork, nextBoundaryAtMs = alarmAt)
        KeepAliveService.sync(ctx)
    }

    /**
     * "End now": restore original mode and dismiss ONLY the currently active slot instance(s)
     * and currently occupied geo-zones — so upcoming inner/new/other-profile slots still fire on time.
     */
    @Synchronized
    fun endNow(context: Context) {
        val ctx = context.applicationContext
        Store.init(ctx)
        val now = System.currentTimeMillis()
        val st = StateStore(ctx)
        val activeNow = Engine.activeAt(Engine.intervals(Store.value, now), now)
        if (activeNow.isNotEmpty()) {
            st.skippedSlotInstances = st.skippedSlotInstances + activeNow.map { it.instanceKey }
        }
        val inside = st.insideGeoIds
        if (inside.isNotEmpty()) {
            st.skippedGeoIds = st.skippedGeoIds + inside
        }
        reconcile(ctx, source = "end_now")
    }

    /** Schedules a short recovery ping right after task swipe so aggressive OEMs can't leave the shield dead. */
    fun scheduleRecoveryPing(context: Context, delayMs: Long = 1_500L) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx,
            3,
            Intent(ctx, ActionReceiver::class.java).setAction(ACTION_RECOVERY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + delayMs
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
            }
        }
    }

    private fun setAlarm(ctx: Context, at: Long?): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            am.cancel(pi)
            return false
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        return try {
            if (exact) {
                // setAlarmClock is Doze-exempt and wakes the app reliably even in deep sleep
                val showIntent = PendingIntent.getActivity(
                    ctx,
                    10,
                    Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent), pi)
                true
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                false
            }
        } catch (_: SecurityException) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                true
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                false
            }
        }
    }
}
