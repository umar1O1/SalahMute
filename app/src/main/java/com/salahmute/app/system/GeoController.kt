package com.salahmute.app.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.salahmute.app.data.Store
import com.salahmute.app.engine.Engine

/**
 * Registers Android proximity alerts (geofences) for saved locations and tracks
 * enter/exit transitions without requiring Google Play Services.
 */
object GeoController {
    const val ACTION_PROXIMITY = "com.salahmute.app.PROXIMITY"
    const val EXTRA_ZONE_ID = "zone_id"
    private const val FRESH_FIX_MS = 2 * 60 * 1000L

    private fun pendingIntent(ctx: Context, zoneId: String): PendingIntent {
        val intent = Intent(ctx, ActionReceiver::class.java)
            .setAction(ACTION_PROXIMITY)
            .setData(Uri.parse("salahmute://geo/$zoneId"))
            .putExtra(EXTRA_ZONE_ID, zoneId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(ctx, zoneId.hashCode(), intent, flags)
    }

    /** Re-registers proximity alerts to match current enabled GeoZones. */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun sync(context: Context) {
        val ctx = context.applicationContext
        Store.init(ctx)
        val lm = ctx.getSystemService(LocationManager::class.java)
        val st = StateStore(ctx)

        for (oldId in st.registeredGeoIds) {
            try {
                lm.removeProximityAlert(pendingIntent(ctx, oldId))
            } catch (_: SecurityException) {
            }
        }

        val data = Store.value
        val activeZones = if (data.enabled && data.geoEnabled) data.geoZones.filter { it.enabled } else emptyList()
        val activeIds = activeZones.map { it.id }.toSet()

        // Drop state for zones that were deleted or disabled.
        st.insideGeoIds = st.insideGeoIds.intersect(activeIds)
        st.skippedGeoIds = st.skippedGeoIds.intersect(activeIds)
        st.publish()

        if (activeZones.isEmpty() || !Perms.read(ctx).fineLocation) {
            st.registeredGeoIds = emptySet()
            return
        }

        val registered = HashSet<String>()
        for (z in activeZones) {
            try {
                lm.addProximityAlert(
                    z.lat,
                    z.lon,
                    z.radiusMeters.toFloat(),
                    -1L,
                    pendingIntent(ctx, z.id),
                )
                registered += z.id
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        st.registeredGeoIds = registered

        // If we already have a very recent fix, update inside/outside immediately.
        val fresh = bestLastKnownLocation(ctx)?.takeIf {
            System.currentTimeMillis() - it.time <= FRESH_FIX_MS
        }
        if (fresh != null) {
            applyLocationFixLocked(ctx, fresh.latitude, fresh.longitude, reconcile = false)
        }
    }

    /** Called by ActionReceiver when Android delivers a proximity enter/exit broadcast. */
    @Synchronized
    fun onProximityEvent(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        Store.init(ctx)
        val zoneId = intent.getStringExtra(EXTRA_ZONE_ID)
            ?: intent.data?.lastPathSegment
            ?: return
        if (!intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)

        val st = StateStore(ctx)
        val inside = st.insideGeoIds.toMutableSet()
        val skipped = st.skippedGeoIds.toMutableSet()
        if (entering) {
            if (Store.value.geoZones.any { it.id == zoneId && it.enabled }) {
                inside += zoneId
            }
        } else {
            inside -= zoneId
            skipped -= zoneId
        }
        st.insideGeoIds = inside
        st.skippedGeoIds = skipped
        Scheduler.reconcile(ctx)
    }

    /** Updates inside/outside state from a fresh location fix and reconciles ringer mode. */
    @Synchronized
    fun applyLocationFix(context: Context, lat: Double, lon: Double) {
        applyLocationFixLocked(context.applicationContext, lat, lon, reconcile = true)
    }

    private fun applyLocationFixLocked(ctx: Context, lat: Double, lon: Double, reconcile: Boolean) {
        Store.init(ctx)
        val st = StateStore(ctx)
        val matching = Engine.matchingGeoZones(Store.value, lat, lon).map { it.id }.toSet()
        val left = st.insideGeoIds - matching
        st.insideGeoIds = matching
        if (left.isNotEmpty()) {
            st.skippedGeoIds = st.skippedGeoIds - left
        }
        st.publish()
        if (reconcile) {
            Scheduler.reconcile(ctx)
        }
    }

    /** Requests a fresh location fix, updates geofence state, and invokes [onDone] on the main thread. */
    fun refreshNow(context: Context, onDone: (Location?) -> Unit = {}) {
        val ctx = context.applicationContext
        fetchCurrentLocation(ctx) { loc ->
            if (loc != null) {
                applyLocationFix(ctx, loc.latitude, loc.longitude)
            }
            onDone(loc)
        }
    }

    @SuppressLint("MissingPermission")
    fun bestLastKnownLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (_: SecurityException) {
            null
        }
    }

    /** Obtains a single fresh location fix (with fallback to best last-known location). */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
        val ctx = context.applicationContext
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = pickProvider(lm)
        if (provider == null) {
            onResult(bestLastKnownLocation(ctx))
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            val main = Handler(Looper.getMainLooper())
            var finished = false
            fun deliver(loc: Location?) {
                if (finished) return
                finished = true
                onResult(loc ?: bestLastKnownLocation(ctx))
            }
            main.postDelayed({
                if (!finished) {
                    signal.cancel()
                    deliver(null)
                }
            }, 8000L)
            try {
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
                    deliver(loc)
                }
            } catch (_: SecurityException) {
                deliver(null)
            } catch (_: IllegalArgumentException) {
                deliver(null)
            }
        } else {
            val main = Handler(Looper.getMainLooper())
            var finished = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished) return
                    finished = true
                    try {
                        lm.removeUpdates(this)
                    } catch (_: SecurityException) {
                    }
                    onResult(location)
                }
            }
            main.postDelayed({
                if (!finished) {
                    finished = true
                    try {
                        lm.removeUpdates(listener)
                    } catch (_: SecurityException) {
                    }
                    onResult(bestLastKnownLocation(ctx))
                }
            }, 6000L)
            try {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                finished = true
                onResult(bestLastKnownLocation(ctx))
            }
        }
    }

    private fun pickProvider(lm: LocationManager): String? {
        if (Build.VERSION.SDK_INT >= 31 &&
            lm.allProviders.contains(LocationManager.FUSED_PROVIDER) &&
            lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        ) {
            return LocationManager.FUSED_PROVIDER
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER
        return null
    }
}
