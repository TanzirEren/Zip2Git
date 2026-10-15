package com.tenix.buildora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;

import com.tenix.buildora.build.AndroidBuildEnv;
import com.tenix.buildora.build.BuildEngine;
import com.tenix.buildora.core.FileUtil;
import com.tenix.buildora.core.Prefs;
import com.tenix.buildora.core.Project;

/** Runs builds in the foreground so Android keeps them alive when the app is closed or the screen locks. */
public class BuildService extends Service {
    public static final String ACTION_CANCEL = "com.tenix.buildora.CANCEL_BUILD";
    private static final String CHANNEL = "buildora_build";
    private static final int ONGOING_ID = 41;
    private static final int RESULT_ID = 42;

    private PowerManager.WakeLock wake;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            BuildManager.get().cancel();
            return START_NOT_STICKY;
        }
        final BuildManager mgr = BuildManager.get();
        final Project p = mgr.takePendingProject();
        final BuildEngine.Options opt = mgr.takePendingOptions();
        createChannel();
        startForeground(ONGOING_ID, ongoing(p == null ? "Buildora" : p.name, opt == null ? "" : opt.variant.name, "Starting", 0));
        if (p == null || opt == null) { stopSelf(); return START_NOT_STICKY; }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "buildora:build");
            wake.acquire(30 * 60 * 1000L);
        }
        final String title = p.name;
        final String vname = opt.variant.name + (opt.aab ? " (AAB)" : "");
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        new Thread(new Runnable() {
            @Override public void run() {
                BuildEngine engine = new BuildEngine(new AndroidBuildEnv(BuildService.this), p, opt, new BuildEngine.Listener() {
                    private long last;
                    @Override public void onStage(String stage, int percent) {
                        mgr.postStage(stage, percent);
                        if (nm != null) nm.notify(ONGOING_ID, ongoing(title, vname, stage, percent));
                    }
                    @Override public void onLog(String line) {
                        mgr.postLog(line);
                    }
                });
                mgr.setEngine(engine);
                BuildEngine.Result r;
                try {
                    r = engine.build();
                } catch (Throwable t) {
                    r = new BuildEngine.Result();
                    r.errorText = "Build crashed: " + t;
                }
                try { if (wake != null && wake.isHeld()) wake.release(); } catch (RuntimeException ignored) { }
                stopForeground(true);
                mgr.postFinished(r);
                if (Prefs.bool(BuildService.this, Prefs.NOTIFY, true) && nm != null && !r.cancelled) {
                    nm.notify(RESULT_ID, finished(p, vname, r));
                }
                stopSelf();
            }
        }, "buildora-build").start();
        return START_NOT_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Builds", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Progress and results of your app builds");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private PendingIntent openIde(Project p) {
        Intent i = new Intent(this, p == null ? MainActivity.class : IdeActivity.class);
        if (p != null) i.putExtra("root", p.root.getAbsolutePath());
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(this, 7, i, fl);
    }

    private Notification ongoing(String project, String variant, String stage, int percent) {
        Intent cancel = new Intent(this, BuildService.class).setAction(ACTION_CANCEL);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent cp = PendingIntent.getService(this, 8, cancel, fl);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_build)
                .setContentTitle("Building " + project + (variant.isEmpty() ? "" : " • " + variant))
                .setContentText(stage)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, false)
                .setContentIntent(openIde(BuildManager.get().takePendingProjectPeek()))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close), "Cancel", cp).build());
        return b.build();
    }

    private Notification finished(Project p, String variant, BuildEngine.Result r) {
        String text;
        if (r.success) {
            File out = r.output();
            text = (out == null ? "" : out.getName() + " • " + FileUtil.human(out.length()) + " • ") + "tap to open";
        } else text = "Tap to see the errors";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_build)
                .setContentTitle((r.success ? "Build successful: " : "Build failed: ") + p.name + " • " + variant)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openIde(p))
                .build();
    }

    @Override public void onDestroy() {
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (RuntimeException ignored) { }
        super.onDestroy();
    }

}
