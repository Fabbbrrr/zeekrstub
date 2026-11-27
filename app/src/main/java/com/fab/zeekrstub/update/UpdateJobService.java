package com.fab.zeekrstub.update;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

import com.fab.zeekrstub.Prefs;

/**
 * Hourly-at-most background check, on the framework JobScheduler.
 *
 * <h3>Why not WorkManager</h3>
 *
 * <p>WorkManager's manifest brings WAKE_LOCK, RECEIVE_BOOT_COMPLETED and a
 * Kotlin/Room tail. This app may not request a wake lock at all: the head unit
 * deep-sleeps about twenty hours a day and a woken CPU drains the 12 V battery —
 * the reference repo deleted its keep-alive wake lock for exactly that reason.
 * JobScheduler is the same mechanism WorkManager uses underneath on this API
 * level, without the permission or the megabytes.</p>
 *
 * <p>The job is not persisted across reboots: that would need
 * RECEIVE_BOOT_COMPLETED. Instead every app launch re-arms it, which costs
 * nothing and keeps the permission list honest.</p>
 */
public final class UpdateJobService extends JobService {

    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!Prefs.of(this).autoCheck()) {
            return false;
        }
        worker = new Thread(() -> {
            try {
                UpdateFlow.checkQuietly(getApplicationContext());
            } catch (Throwable ignored) {
                // the failure text is recorded by checkQuietly; nothing to retry here
            }
            main.post(() -> jobFinished(params, false));
        }, "auto-check");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        // Re-arm: a check cut short by the platform should happen again.
        return Prefs.of(this).autoCheck();
    }
}
