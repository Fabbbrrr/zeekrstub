package com.fab.zeekrstub.update;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.fab.zeekrstub.Prefs;

/**
 * Keeps the scheduled check in step with the auto-check toggle.
 *
 * <p>One periodic job, one hour interval, network required, not persisted across
 * reboot. No exact alarms and no wake locks: JobScheduler owns the timing and
 * the platform decides when to wake, which is what a car that sleeps twenty
 * hours a day needs.</p>
 */
public final class UpdateScheduler {

    private static final String TAG = "UpdateScheduler";
    public static final int JOB_ID = 4711;
    private static final long INTERVAL_MS = 60L * 60L * 1000L;

    private UpdateScheduler() {
    }

    public static void sync(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                Log.w(TAG, "no JobScheduler on this platform");
                return;
            }
            if (!Prefs.of(context).autoCheck()) {
                scheduler.cancel(JOB_ID);
                return;
            }
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(false)
                    .build();
            int result = scheduler.schedule(job);
            // RESULT_SUCCESS = 1. Anything else means the hourly check will never
            // run, which the owner has to see rather than discover a month later.
            Log.i(TAG, "schedule -> " + result + " (job " + JOB_ID + ")");
            if (result != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "JobScheduler refused the auto-check job");
            }
        } catch (Throwable e) {
            // A stripped platform without JobScheduler is not a reason to crash the
            // one app that must stay installed, but the reason belongs in the log.
            Log.w(TAG, "auto-check not scheduled", e);
        }
    }

    /** True when a job is registered; used by About to show the real state. */
    public static boolean scheduled(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return false;
            }
            return scheduler.getPendingJob(JOB_ID) != null;
        } catch (Throwable e) {
            return false;
        }
    }
}
