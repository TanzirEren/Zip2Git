package com.tenix.buildora;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import com.tenix.buildora.core.Prefs;

/** Applies the user's light/dark theme to every screen. */
public abstract class BaseActivity extends Activity {
    protected boolean dark;
    private int appliedTheme;

    @Override protected void onCreate(Bundle b) {
        appliedTheme = Prefs.num(this, Prefs.THEME, 0);
        dark = resolveDark();
        setTheme(dark ? R.style.AppDark : R.style.AppLight);
        super.onCreate(b);
    }

    private boolean resolveDark() {
        int t = Prefs.num(this, Prefs.THEME, 0);
        if (t == 0) {
            int ui = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return ui == Configuration.UI_MODE_NIGHT_YES;
        }
        return t == 2;
    }

    protected void styleBars() {
        View d = getWindow().getDecorView();
        int f = d.getSystemUiVisibility();
        if (dark) f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (Prefs.num(this, Prefs.THEME, 0) != appliedTheme) { recreate(); return; }
        styleBars();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (Prefs.num(this, Prefs.THEME, 0) == 0 && resolveDark() != dark) recreate();
    }

    protected void ui(Runnable r) { runOnUiThread(r); }
}
