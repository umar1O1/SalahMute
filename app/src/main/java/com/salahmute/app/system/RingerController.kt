package com.salahmute.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.salahmute.app.MainActivity
import com.salahmute.app.R
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine
import com.salahmute.app.model.RingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Applies a mute mode, remembers the original one, and restores it afterwards. */
object RingerController {
    private const val CHANNEL_ACTIVE = "active_mute"
    private const val CHANNEL_SHIELD = "bg_shield"
    const val NOTIF_ID = 42

    fun enter(
        ctx: Context,
        st: StateStore,
        mode: RingMode,
        startMs: Long?,
        until: Long?,
        slotLabel: String?,
        placeLabel: String?,
    ) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!st.active) {
            st.origRinger = am.ringerMode
            st.origFilter = nm.currentInterruptionFilter
            st.userOverrode = false
            st.active = true
            apply(ctx, am, nm, st, mode)
            st.activeMode = mode
        } else if (st.activeMode != mode) {
            if (st.userOverrode || !isUntouched(am, nm, st)) {
                st.userOverrode = true
            } else {
                apply(ctx, am, nm, st, mode)
                st.activeMode = mode
            }
        }
        st.activeStart = startMs ?: 0L
        st.activeUntil = until ?: 0L
        st.activeLabel = slotLabel
        st.activePlace = placeLabel
        st.publish()
        postActiveNotification(ctx, nm, mode, until, slotLabel, placeLabel)
    }

    fun exit(ctx: Context, st: StateStore) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (st.active) {
            // Smart restore: if the user changed the mode manually during the slot/zone, leave their choice alone.
            if (!st.userOverrode && isUntouched(am, nm, st)) {
                try {
                    if (nm.isNotificationPolicyAccessGranted && nm.currentInterruptionFilter != st.origFilter &&
                        st.origFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                    ) {
                        nm.setInterruptionFilter(st.origFilter)
                    }
                    am.ringerMode = st.origRinger
                } catch (_: SecurityException) {
                }
            }
            st.active = false
            st.userOverrode = false
            st.activeStart = 0L
            st.activeUntil = 0L
            st.activeLabel = null
            st.activePlace = null
        }
        st.publish()
        if (KeepAliveService.isRunning) {
            refreshNotification(ctx)
        } else {
            nm.cancel(NOTIF_ID)
        }
    }

    /** True if the user's pre-slot audio/DND state is already stricter than [targetMode] (never weaken it). */
    internal fun isAlreadyStricter(origRinger: Int, origFilter: Int, targetMode: RingMode): Boolean {
        val origDndOn = origFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            origFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            origFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
        return when (targetMode) {
            RingMode.VIBRATE -> origDndOn || origRinger == AudioManager.RINGER_MODE_SILENT
            RingMode.SILENT -> origDndOn
            RingMode.DND -> origFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        }
    }

    private fun isInCall(am: AudioManager): Boolean {
        val m = am.mode
        return m == AudioManager.MODE_IN_CALL ||
            m == AudioManager.MODE_IN_COMMUNICATION ||
            m == AudioManager.MODE_RINGTONE
    }

    private fun isUntouched(am: AudioManager, nm: NotificationManager, st: StateStore): Boolean {
        val checkDnd = nm.isNotificationPolicyAccessGranted && (
            st.activeMode == RingMode.DND ||
                (st.appliedFilter != -1 &&
                    st.appliedFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    st.appliedFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
            )
        return if (checkDnd) {
            st.appliedFilter == -1 || nm.currentInterruptionFilter == st.appliedFilter
        } else {
            st.appliedRinger == -1 || am.ringerMode == st.appliedRinger
        }
    }

    private fun apply(ctx: Context, am: AudioManager, nm: NotificationManager, st: StateStore, mode: RingMode) {
        val policy = nm.isNotificationPolicyAccessGranted
        var targetFilter = nm.currentInterruptionFilter
        var targetRinger = am.ringerMode
        val wasAlreadyVibrate = targetRinger == AudioManager.RINGER_MODE_VIBRATE
        try {
            // Never weaken a stricter silence mode the user already had turned on before the slot began.
            if (isAlreadyStricter(st.origRinger, st.origFilter, mode)) {
                if (policy && nm.currentInterruptionFilter != st.origFilter &&
                    st.origFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                ) {
                    nm.setInterruptionFilter(st.origFilter)
                    targetFilter = st.origFilter
                }
                if (am.ringerMode != st.origRinger) {
                    am.ringerMode = st.origRinger
                    targetRinger = st.origRinger
                }
                st.appliedRinger = targetRinger
                st.appliedFilter = targetFilter
                return
            }
            if (mode != RingMode.DND && policy && nm.currentInterruptionFilter != st.origFilter &&
                st.origFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                nm.setInterruptionFilter(st.origFilter)
                targetFilter = st.origFilter
            }
            when (mode) {
                RingMode.VIBRATE -> {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    targetRinger = AudioManager.RINGER_MODE_VIBRATE
                    if (!wasAlreadyVibrate && !isInCall(am)) buzz(ctx)
                }
                RingMode.SILENT -> {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    targetRinger = AudioManager.RINGER_MODE_SILENT
                }
                RingMode.DND -> {
                    if (policy) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                        targetFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS
                    } else {
                        am.ringerMode = AudioManager.RINGER_MODE_SILENT
                        targetRinger = AudioManager.RINGER_MODE_SILENT
                    }
                }
            }
        } catch (_: SecurityException) {
            try {
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                targetRinger = AudioManager.RINGER_MODE_VIBRATE
                if (!wasAlreadyVibrate && !isInCall(am)) buzz(ctx)
            } catch (_: SecurityException) {
            }
        }
        // Record expected mode synchronously (Binder updates currentInterruptionFilter asynchronously on Android)
        st.appliedRinger = targetRinger
        st.appliedFilter = targetFilter
    }

    /** Short haptic confirmation when switching into Vibrate mode (matches hardware toggle feel). */
    private fun buzz(ctx: Context) {
        try {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Vibrator::class.java)
            } ?: return
            if (v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureChannels(ctx: Context, nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVE, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHIELD, ctx.getString(R.string.notif_shield_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
    }

    /** Builds the right notification for right now: Active Mute countdown if muted, else tiny silent standby. */
    fun buildCurrentNotification(ctx: Context): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ensureChannels(ctx, nm)
        val st = StateStore(ctx)
        if (st.active) {
            return buildActiveNotification(
                ctx = ctx,
                mode = st.activeMode,
                until = st.activeUntil.takeIf { it > 0L },
                slotLabel = st.activeLabel,
                placeLabel = st.activePlace,
            )
        }
        Store.init(ctx)
        val data = Store.value
        val now = System.currentTimeMillis()
        val next = Engine.nextUpcoming(Engine.intervals(data, now), now)
        val hasGeo = data.geoEnabled && data.geoZones.any { it.enabled }
        return buildStandbyNotification(ctx, next, hasGeo)
    }

    /** Refreshes the unified notification in place (used by KeepAliveService and exit transitions). */
    fun refreshNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        try {
            nm.notify(NOTIF_ID, buildCurrentNotification(ctx))
        } catch (_: SecurityException) {
        }
    }

    private fun postActiveNotification(
        ctx: Context,
        nm: NotificationManager,
        mode: RingMode,
        until: Long?,
        slotLabel: String?,
        placeLabel: String?,
    ) {
        ensureChannels(ctx, nm)
        try {
            nm.notify(NOTIF_ID, buildActiveNotification(ctx, mode, until, slotLabel, placeLabel))
        } catch (_: SecurityException) {
        }
    }

    private fun buildActiveNotification(
        ctx: Context,
        mode: RingMode,
        until: Long?,
        slotLabel: String?,
        placeLabel: String?,
    ): Notification {
        val modeName = ctx.getString(
            when (mode) {
                RingMode.VIBRATE -> R.string.mode_vibrate
                RingMode.SILENT -> R.string.mode_silent
                RingMode.DND -> R.string.mode_dnd
            },
        )
        val time = until?.takeIf { it > 0L }?.let {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }
        val place = placeLabel?.let { it.ifBlank { ctx.getString(R.string.location) } }
        val activeName = slotLabel?.takeIf { it.isNotBlank() } ?: place
        val title = if (activeName != null) "$activeName · $modeName" else ctx.getString(R.string.notif_title)
        val text = when {
            time != null && place != null -> ctx.getString(R.string.notif_restores_place_time, place, time)
            time != null -> ctx.getString(R.string.notif_restores_at, time)
            place != null -> ctx.getString(R.string.notif_restores_leave, place)
            else -> modeName
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val endNow = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, ActionReceiver::class.java).setAction(Scheduler.ACTION_END_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, ctx.getString(R.string.restore_now), endNow)

        // Live ticking countdown in the notification bar & lockscreen (0% CPU — rendered by Android).
        // Add +900ms display cushion so Chronometer stops at 00:00 instead of ticking into -00:01
        // during OS alarm delivery; non-FGS notifications also auto-timeout right at end.
        val now = System.currentTimeMillis()
        if (until != null && until > now) {
            builder
                .setWhen(until + 900L)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
            if (!KeepAliveService.isRunning) {
                builder.setTimeoutAfter((until - now).coerceAtLeast(1_000L))
            }
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    private fun buildStandbyNotification(
        ctx: Context,
        next: Engine.NextSlot?,
        hasGeo: Boolean,
    ): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            next != null -> {
                val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .format(Instant.ofEpochMilli(next.startMs).atZone(ZoneId.systemDefault()))
                val name = next.label.ifBlank { ctx.getString(R.string.auto_silence) }
                ctx.getString(R.string.next_slot_at, name, time)
            }
            hasGeo -> ctx.getString(R.string.notif_shield_geo)
            else -> ctx.getString(R.string.notif_shield_idle)
        }
        return NotificationCompat.Builder(ctx, CHANNEL_SHIELD)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(ctx.getString(R.string.notif_shield_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }
}
