package com.tenix.buildora;

import android.app.Application;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // JGit looks for a home directory (for its config); apps have none.
        System.setProperty("user.home", getFilesDir().getAbsolutePath());
        System.setProperty("user.name", "buildora");
    }
}
