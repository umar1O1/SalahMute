package com.salahmute.app.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Zero-dependency persistent JobScheduler backup alongside AlarmManager:
 * 1) A 15-minute persisted watchdog that self-heals alarms & KeepAliveService if an OEM kills them.
 * 2) A one-shot boundary job aimed at the next slot transition as a second independent OS trigger.
 */
class ReconcileJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val tag = if (params?.jobId == JOB_BOUNDARY) "job_boundary" else "job_watchdog"
        withBriefWakeLock(applicationContext, "Job") {
            Scheduler.reconcile(applicationContext, source = tag)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_WATCHDOG = 101
        private const val JOB_BOUNDARY = 102

        fun schedule(context: Context, active: Boolean, nextBoundaryAtMs: Long?) {
            val ctx = context.applicationContext
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            val comp = ComponentName(ctx, ReconcileJobService::class.java)

            if (!active) {
                js.cancel(JOB_WATCHDOG)
                js.cancel(JOB_BOUNDARY)
                return
            }

            // Keep existing 6-hour persisted watchdog intact if already pending (only 4 wakeups/day for minimal battery!)
            if (js.getPendingJob(JOB_WATCHDOG) == null) {
                val period = maxOf(6 * 60 * 60_000L, JobInfo.getMinPeriodMillis())
                val watchdog = JobInfo.Builder(JOB_WATCHDOG, comp)
                    .setPeriodic(period)
                    .setPersisted(true)
                    .build()
                try {
                    js.schedule(watchdog)
                } catch (_: Exception) {
                }
            }

            // One-shot backup right at the next slot boundary
            if (nextBoundaryAtMs == null) {
                js.cancel(JOB_BOUNDARY)
            } else {
                val delay = (nextBoundaryAtMs - System.currentTimeMillis()).coerceAtLeast(1_000L)
                val boundary = JobInfo.Builder(JOB_BOUNDARY, comp)
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(delay + 5_000L)
                    .setPersisted(true)
                    .build()
                try {
                    js.schedule(boundary)
                } catch (_: Exception) {
                }
            }
        }
    }
}
