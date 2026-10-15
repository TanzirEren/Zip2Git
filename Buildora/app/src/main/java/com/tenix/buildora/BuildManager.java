package com.tenix.buildora;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.tenix.buildora.build.BuildEngine;
import com.tenix.buildora.core.Project;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide build state. The build itself runs inside {@link BuildService} (a foreground service),
 * so it survives leaving the app or locking the screen. Activities attach as listeners to watch it.
 */
public final class BuildManager {

    public interface Listener {
        void onStage(String stage, int percent);
        void onLog(String line);
        void onFinished(BuildEngine.Result result);
    }

    private static final BuildManager INSTANCE = new BuildManager();
    public static BuildManager get() { return INSTANCE; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder log = new StringBuilder();

    private volatile boolean running;
    private String projectRoot = "";
    private String projectName = "";
    private String variant = "";
    private String stage = "";
    private int percent;
    private Project pending;
    private BuildEngine.Options pendingOptions;
    private volatile BuildEngine engine;
    private BuildEngine.Result lastResult;
    private boolean resultSeen = true;
    private String lastRoot = "";

    private BuildManager() { }

    public boolean isRunning() { return running; }
    public boolean isRunningFor(String root) { return running && projectRoot.equals(root); }
    public String projectName() { return projectName; }
    public String variantName() { return variant; }
    public synchronized String stage() { return stage; }
    public synchronized int percent() { return percent; }
    public synchronized String logSnapshot() { return log.toString(); }

    /** The result of the last finished build of this project that the user has not looked at yet. */
    public synchronized BuildEngine.Result takeUnseenResult(String root) {
        if (lastResult != null && !resultSeen && lastRoot.equals(root)) {
            resultSeen = true;
            return lastResult;
        }
        return null;
    }

    public void attach(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void detach(Listener l) { listeners.remove(l); }

    /** Starts the foreground service which runs the build. */
    public synchronized boolean start(Context ctx, Project p, BuildEngine.Options opt) {
        if (running) return false;
        running = true;
        pending = p;
        pendingOptions = opt;
        projectRoot = p.root.getAbsolutePath();
        projectName = p.name;
        variant = opt.variant.name + (opt.aab ? " (AAB)" : "");
        stage = "Starting";
        percent = 0;
        log.setLength(0);
        resultSeen = true;
        Intent i = new Intent(ctx, BuildService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (RuntimeException e) {
            running = false;
            return false;
        }
        return true;
    }

    public void cancel() {
        BuildEngine e = engine;
        if (e != null) e.cancel();
    }

    // ---- called by BuildService

    synchronized Project takePendingProject() { return pending; }
    synchronized Project takePendingProjectPeek() { return pending; }
    synchronized BuildEngine.Options takePendingOptions() { return pendingOptions; }
    void setEngine(BuildEngine e) { engine = e; }

    void postStage(final String s, final int pc) {
        synchronized (this) { stage = s; percent = pc; }
        main.post(new Runnable() {
            @Override public void run() { for (Listener l : listeners) l.onStage(s, pc); }
        });
    }

    void postLog(final String line) {
        synchronized (this) {
            log.append(line).append('\n');
            if (log.length() > 40000) log.delete(0, log.length() - 28000);
        }
        main.post(new Runnable() {
            @Override public void run() { for (Listener l : listeners) l.onLog(line); }
        });
    }

    void postFinished(final BuildEngine.Result r) {
        synchronized (this) {
            lastResult = r;
            lastRoot = projectRoot;
            running = false;
            engine = null;
            pending = null;
            resultSeen = false;
        }
        main.post(new Runnable() {
            @Override public void run() {
                boolean delivered = false;
                for (Listener l : listeners) { l.onFinished(r); delivered = true; }
                if (delivered) markSeen();
            }
        });
    }

    private synchronized void markSeen() { resultSeen = true; }
}
