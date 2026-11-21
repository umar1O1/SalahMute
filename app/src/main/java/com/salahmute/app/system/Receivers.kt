package com.salahmute.app.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

internal inline fun withBriefWakeLock(ctx: Context, tag: String, block: () -> Unit) {
    val wl = try {
        ctx.applicationContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SalahMute:$tag")
            ?.apply { acquire(5_000L) }
    } catch (_: Exception) {
        null
    }
    try {
        block()
    } finally {
        try {
            if (wl != null && wl.isHeld) wl.release()
        } catch (_: Exception) {
        }
    }
}

/** Internal (non-exported) receiver: alarm ticks, recovery pings, proximity alerts, and "Unmute" action. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        withBriefWakeLock(context, "Action") {
            when (intent.action) {
                Scheduler.ACTION_END_NOW -> Scheduler.endNow(context)
                GeoController.ACTION_PROXIMITY -> GeoController.onProximityEvent(context, intent)
                Scheduler.ACTION_RECOVERY -> {
                    GeoController.sync(context)
                    Scheduler.reconcile(context, source = "recovery")
                }
                else -> Scheduler.reconcile(context, source = "alarm")
            }
        }
    }
}

/** System events (boot, clock/time-zone change, app update, exact-alarm/DND grant): rebuild schedule and geofences. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALLOWED_ACTIONS) return
        withBriefWakeLock(context, "System") {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                Intent.ACTION_MY_PACKAGE_REPLACED,
                -> GeoController.sync(context)
            }
            val src = when (action) {
                Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> "boot"
                NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED -> "dnd_grant"
                else -> "system"
            }
            Scheduler.reconcile(context, source = src)
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED,
        )
    }
}
