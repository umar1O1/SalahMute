package com.salahmute.app.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.salahmute.app.data.Store
import com.salahmute.app.model.AppData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight 0% CPU foreground shield service that prevents Android / OEM task killers
 * (Xiaomi MIUI/HyperOS, Oppo/Realme ColorOS, Vivo, Tecno/Infinix, Samsung, OnePlus)
 * from wiping scheduled alarms and geofences when the user clears the app from Recents.
 *
 * Uses NO background polling loop — holds foreground protection with a collapsed,
 * silent IMPORTANCE_MIN notification (seamlessly promoted to active countdown while muted)
 * and schedules an exact one-shot end callback so the countdown never ticks past 00:00.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        promoteForeground()
        armExactEndTimer(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _running.value = true
        promoteForeground()
        val ctx = applicationContext
        Store.init(ctx)
        if (!shouldRun(Store.value)) {
            clearExactEndTimer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        armExactEndTimer(ctx)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val ctx = applicationContext
        Store.init(ctx)
        if (shouldRun(Store.value)) {
            GeoController.sync(ctx)
            Scheduler.reconcile(ctx, source = "task_removed")
            // Schedule a short recovery alarm in case an aggressive OEM terminates the process right after swipe
            Scheduler.scheduleRecoveryPing(ctx, delayMs = 1_500L)
        }
    }

    override fun onDestroy() {
        clearExactEndTimer()
        _running.value = false
        super.onDestroy()
    }

    private fun promoteForeground() {
        val notif = RingerController.buildCurrentNotification(applicationContext)
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, RingerController.NOTIF_ID, notif, type)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> get() = _running
        val isRunning: Boolean get() = _running.value

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingEndRunnable: Runnable? = null

        private fun clearExactEndTimer() {
            pendingEndRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingEndRunnable = null
        }

        /** Arms a zero-polling one-shot callback right at active mute end so the countdown stops cleanly at 00:00. */
        private fun armExactEndTimer(ctx: Context) {
            clearExactEndTimer()
            val appCtx = ctx.applicationContext
            val st = StateStore(appCtx)
            if (!st.active || st.activeUntil <= 0L) return
            val delayMs = (st.activeUntil - System.currentTimeMillis() + 30L).coerceAtLeast(30L)
            if (delayMs > 24 * 60 * 60_000L) return
            val r = Runnable {
                pendingEndRunnable = null
                Scheduler.reconcile(appCtx, source = "exact_end")
            }
            pendingEndRunnable = r
            mainHandler.postDelayed(r, delayMs)
        }

        /** Runs only when master toggle + shield setting are ON and at least one slot or geo-zone is active. */
        fun shouldRun(data: AppData): Boolean {
            if (!data.enabled || !data.settings.keepAliveService) return false
            val hasSlots = data.active.slots.any { it.enabled && !it.archived }
            val hasGeo = data.geoEnabled && data.geoZones.any { it.enabled }
            return hasSlots || hasGeo
        }

        /** Starts, refreshes, or stops the foreground shield to match current user data. */
        fun sync(context: Context) {
            val ctx = context.applicationContext
            Store.init(ctx)
            val needed = shouldRun(Store.value)
            if (needed) {
                if (isRunning) {
                    RingerController.refreshNotification(ctx)
                    armExactEndTimer(ctx)
                } else {
                    try {
                        ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
                    } catch (_: Exception) {
                    }
                }
            } else {
                clearExactEndTimer()
                if (isRunning) {
                    try {
                        ctx.stopService(Intent(ctx, KeepAliveService::class.java))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
