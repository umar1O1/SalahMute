package com.salahmute.app.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermState(
    val policy: Boolean,
    val exactAlarm: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
) {
    val essentialsOk: Boolean get() = policy && exactAlarm && battery
    val coreGrantedCount: Int get() = (if (policy) 1 else 0) + (if (exactAlarm) 1 else 0) + (if (battery) 1 else 0)
    val geoOk: Boolean get() = fineLocation && backgroundLocation
}

object Perms {
    fun read(ctx: Context): PermState {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val bg = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return PermState(
            policy = nm.isNotificationPolicyAccessGranted,
            exactAlarm = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(),
            notifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            battery = pm.isIgnoringBatteryOptimizations(ctx.packageName),
            fineLocation = fine,
            backgroundLocation = fine && bg,
        )
    }

    private fun Intent.withAppHighlight(pkg: String): Intent {
        val args = Bundle().apply { putString(":settings:fragment_args_key", pkg) }
        putExtra(":settings:fragment_args_key", pkg)
        putExtra(":settings:show_fragment_args", args)
        putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        return this
    }

    /** Opens Do Not Disturb access settings and asks Android Settings to auto-scroll & flash SalahMute. */
    fun dndSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).withAppHighlight(ctx.packageName)

    /** Opens Alarms & reminders directly for SalahMute (with list scroll/flash highlight fallback). */
    fun exactAlarmSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
            .withAppHighlight(ctx.packageName)

    /** Opens the direct 1-tap Ignore Battery Optimizations dialog for SalahMute. */
    fun batterySettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            .withAppHighlight(ctx.packageName)

    /** Opens SalahMute's App Info screen (fallback for Battery → Unrestricted / Autostart). */
    fun appDetailsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /**
     * Returns an exported OEM Autostart / Background Launch settings intent if the device has one
     * (Xiaomi/MIUI, Oppo/Realme, Vivo, OnePlus, Huawei/Honor, Tecno/Infinix, Samsung, Asus), else null.
     */
    fun oemAutostartIntent(ctx: Context): Intent? {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
        )
        val pm = ctx.packageManager
        for (cn in candidates) {
            val intent = Intent().setComponent(cn)
            val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (ri?.activityInfo?.exported == true) {
                return intent
            }
        }
        return null
    }
}
